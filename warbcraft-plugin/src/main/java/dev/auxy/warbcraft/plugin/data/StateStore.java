package dev.auxy.warbcraft.plugin.data;

import dev.auxy.warbcraft.core.economy.Market;
import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Realm;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.quest.Quest;
import dev.auxy.warbcraft.core.quest.QuestOffer;
import dev.auxy.warbcraft.core.quest.QuestStatus;
import dev.auxy.warbcraft.core.quest.QuestType;
import dev.auxy.warbcraft.plugin.Game;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dunyanin degisen durumunu ({@code durum.yml}) kaydeder: yerlesim sahipleri, klanlar, oyuncu
 * kralliklari, savaslar, iliskiler, gorevler, pazar stoklari.
 */
public final class StateStore {

    private StateStore() {
    }

    /** Kayitli durumun ait oldugu sezon; dosya yoksa 0. */
    public static int savedSeason(File file) {
        return file.exists() ? YamlConfiguration.loadConfiguration(file).getInt("sezon", 0) : 0;
    }

    public static void save(Game game, File file) throws IOException {
        Realm realm = game.realm();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("sezon", game.season().number());

        for (Kingdom kingdom : realm.kingdoms()) {
            if (!kingdom.npc()) {
                String path = "oyuncu-kralliklari." + kingdom.id();
                yaml.set(path + ".ad", kingdom.name());
                yaml.set(path + ".kultur", kingdom.culture());
                yaml.set(path + ".hukumdar", kingdom.rulerId());
            }
        }
        for (Clan clan : realm.clans()) {
            String path = "klanlar." + clan.id();
            yaml.set(path + ".ad", clan.name());
            yaml.set(path + ".npc", clan.npc());
            yaml.set(path + ".lider", clan.leaderId());
            yaml.set(path + ".uyeler", new ArrayList<>(clan.members()));
            yaml.set(path + ".krallik", clan.kingdomId());
            yaml.set(path + ".un", clan.renown());
        }
        for (Settlement settlement : realm.settlements()) {
            yaml.set("yerlesimler." + settlement.id() + ".sahip", settlement.ownerClanId());
            yaml.set("yerlesimler." + settlement.id() + ".refah", settlement.prosperity());
        }
        Set<String> seen = new HashSet<>();
        List<String> wars = new ArrayList<>();
        for (Kingdom kingdom : realm.kingdoms()) {
            for (String enemy : realm.enemiesOf(kingdom.id())) {
                String key = kingdom.id().compareTo(enemy) < 0 ? kingdom.id() + "|" + enemy : enemy + "|" + kingdom.id();
                if (seen.add(key)) {
                    wars.add(key);
                }
            }
        }
        yaml.set("savaslar", wars);

        Set<String> playerIds = new HashSet<>();
        for (PlayerProfile profile : game.players().loaded()) {
            playerIds.add(profile.id().toString());
        }
        for (Quest quest : game.quests().allActive()) {
            playerIds.add(quest.assigneeId());
        }
        for (String playerId : playerIds) {
            game.relations().of(playerId).forEach((npc, value) -> yaml.set("iliskiler." + playerId + "." + npc, value));
        }
        // Yuklu olmayan oyuncularin iliskileri onceki kayittan korunur.
        if (file.exists()) {
            ConfigurationSection old = YamlConfiguration.loadConfiguration(file).getConfigurationSection("iliskiler");
            if (old != null) {
                for (String playerId : old.getKeys(false)) {
                    ConfigurationSection relations = old.getConfigurationSection(playerId);
                    if (playerIds.contains(playerId) || relations == null) {
                        continue;
                    }
                    for (String npc : relations.getKeys(false)) {
                        yaml.set("iliskiler." + playerId + "." + npc, relations.getInt(npc));
                    }
                }
            }
        }

        List<Map<String, Object>> questList = new ArrayList<>();
        for (Quest quest : game.quests().allActive()) {
            QuestOffer offer = quest.offer();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", offer.id());
            map.put("tur", offer.type().name());
            map.put("kral", offer.kingId());
            map.put("krallik", offer.kingdomId());
            map.put("hedef", offer.target());
            map.put("gereken", offer.required());
            map.put("odul-dinar", offer.rewardDenars());
            map.put("odul-iliski", offer.rewardRelation());
            map.put("odul-un", offer.rewardRenown());
            map.put("sure-saniye", offer.timeLimit().toSeconds());
            map.put("oyuncu", quest.assigneeId());
            map.put("kabul", quest.acceptedAt().toString());
            map.put("ilerleme", quest.progress());
            questList.add(map);
        }
        yaml.set("gorevler", questList);
        yaml.set("sonraki-gorev-no", game.quests().nextOfferId());

        game.markets().forEach((town, market) -> {
            for (String goodId : market.goods().keySet()) {
                yaml.set("pazarlar." + town + "." + goodId, market.stock(goodId));
            }
        });
        game.recruitPools().forEach((id, pool) -> yaml.set("asker-havuzu." + id, pool));
        yaml.save(file);
    }

    public static void load(Game game, File file) {
        if (!file.exists()) {
            return;
        }
        Realm realm = game.realm();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection kingdoms = yaml.getConfigurationSection("oyuncu-kralliklari");
        if (kingdoms != null) {
            for (String id : kingdoms.getKeys(false)) {
                if (realm.findKingdom(id).isEmpty()) {
                    realm.addKingdom(new Kingdom(id, kingdoms.getString(id + ".ad", id),
                            kingdoms.getString(id + ".kultur", ""), kingdoms.getString(id + ".hukumdar", ""), false));
                }
            }
        }
        ConfigurationSection clans = yaml.getConfigurationSection("klanlar");
        if (clans != null) {
            for (String id : clans.getKeys(false)) {
                ConfigurationSection c = clans.getConfigurationSection(id);
                if (c == null) {
                    continue;
                }
                Clan clan = realm.findClan(id).orElse(null);
                if (clan == null) {
                    if (c.getBoolean("npc")) {
                        continue; // dunya.yml'den kaldirilmis NPC klani
                    }
                    clan = new Clan(id, c.getString("ad", id), c.getString("lider", ""), false);
                    for (String member : c.getStringList("uyeler")) {
                        clan.addMember(member);
                    }
                    String kingdomId = c.getString("krallik");
                    clan.setKingdomId(kingdomId != null && realm.findKingdom(kingdomId).isPresent() ? kingdomId : null);
                    realm.addClan(clan);
                } else {
                    String kingdomId = c.getString("krallik");
                    if (kingdomId == null || realm.findKingdom(kingdomId).isPresent()) {
                        clan.setKingdomId(kingdomId);
                    }
                }
                clan.addRenown(Math.max(0, c.getInt("un") - clan.renown()));
            }
        }
        ConfigurationSection settlements = yaml.getConfigurationSection("yerlesimler");
        if (settlements != null) {
            for (String id : settlements.getKeys(false)) {
                realm.findSettlement(id).ifPresent(s -> {
                    String owner = settlements.getString(id + ".sahip");
                    if (owner != null && realm.findClan(owner).isPresent()) {
                        s.setOwnerClanId(owner);
                    }
                    s.setProsperity(settlements.getInt(id + ".refah", s.prosperity()));
                });
            }
        }
        for (String war : yaml.getStringList("savaslar")) {
            String[] pair = war.split("\\|");
            if (pair.length == 2 && realm.findKingdom(pair[0]).isPresent() && realm.findKingdom(pair[1]).isPresent()) {
                game.diplomacy().restoreWar(pair[0], pair[1]);
            }
        }
        ConfigurationSection relations = yaml.getConfigurationSection("iliskiler");
        if (relations != null) {
            for (String playerId : relations.getKeys(false)) {
                ConfigurationSection r = relations.getConfigurationSection(playerId);
                if (r != null) {
                    for (String npc : r.getKeys(false)) {
                        game.relations().set(playerId, npc, r.getInt(npc));
                    }
                }
            }
        }
        for (Map<?, ?> map : yaml.getMapList("gorevler")) {
            QuestOffer offer = new QuestOffer(
                    String.valueOf(map.get("id")),
                    QuestType.valueOf(String.valueOf(map.get("tur"))),
                    String.valueOf(map.get("kral")),
                    String.valueOf(map.get("krallik")),
                    String.valueOf(map.get("hedef")),
                    number(map.get("gereken")),
                    number(map.get("odul-dinar")),
                    number(map.get("odul-iliski")),
                    number(map.get("odul-un")),
                    Duration.ofSeconds(number(map.get("sure-saniye"))));
            game.quests().restore(Quest.restore(offer, String.valueOf(map.get("oyuncu")),
                    Instant.parse(String.valueOf(map.get("kabul"))), number(map.get("ilerleme")), QuestStatus.ACTIVE));
        }
        game.quests().reserveOfferIds(yaml.getLong("sonraki-gorev-no", 1));

        ConfigurationSection markets = yaml.getConfigurationSection("pazarlar");
        if (markets != null) {
            for (String town : markets.getKeys(false)) {
                Market market = game.markets().get(town);
                ConfigurationSection stocks = markets.getConfigurationSection(town);
                if (market == null || stocks == null) {
                    continue;
                }
                for (String goodId : stocks.getKeys(false)) {
                    if (market.goods().containsKey(goodId)) {
                        market.adjustStock(goodId, stocks.getInt(goodId) - market.stock(goodId));
                    }
                }
            }
        }
        ConfigurationSection pools = yaml.getConfigurationSection("asker-havuzu");
        if (pools != null) {
            for (String id : pools.getKeys(false)) {
                if (game.recruitPools().containsKey(id)) {
                    game.recruitPools().put(id, pools.getInt(id));
                }
            }
        }
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }
}
