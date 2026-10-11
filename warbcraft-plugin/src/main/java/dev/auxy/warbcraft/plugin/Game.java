package dev.auxy.warbcraft.plugin;

import dev.auxy.warbcraft.core.economy.Good;
import dev.auxy.warbcraft.core.economy.Market;
import dev.auxy.warbcraft.core.economy.Purse;
import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Diplomacy;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Realm;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.faction.SettlementType;
import dev.auxy.warbcraft.core.npc.NpcKing;
import dev.auxy.warbcraft.core.npc.NpcLordFactory;
import dev.auxy.warbcraft.core.npc.RelationBook;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.party.PartyUpkeep;
import dev.auxy.warbcraft.core.quest.Quest;
import dev.auxy.warbcraft.core.quest.QuestService;
import dev.auxy.warbcraft.core.quest.QuestType;
import dev.auxy.warbcraft.core.season.Season;
import dev.auxy.warbcraft.core.troop.TroopTree;
import dev.auxy.warbcraft.plugin.data.GameData;
import dev.auxy.warbcraft.plugin.data.PlayerProfile;
import dev.auxy.warbcraft.plugin.data.PlayerStore;
import dev.auxy.warbcraft.plugin.data.SettlementSite;
import dev.auxy.warbcraft.plugin.npc.NpcTags;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/** Calisan sunucudaki oyun durumu. Tum metotlar ana is parcacigindan cagrilmalidir. */
public final class Game {

    public static final int MARKET_TARGET_STOCK = 200;
    public static final int RECRUIT_POOL_MAX = 20;
    public static final int DAILY_HEAL = 5;
    public static final int TOWN_CONSUMPTION = 4;

    private final GameData data;
    private final PlayerStore players;
    private final RelationBook relations = new RelationBook();
    private final QuestService quests;
    private final Diplomacy diplomacy;
    private final NpcLordFactory npcFactory;
    private final Map<String, Market> markets = new LinkedHashMap<>();
    private final Map<String, Party> lordParties = new LinkedHashMap<>();
    private final Map<String, Party> garrisons = new LinkedHashMap<>();
    private final Map<String, Integer> recruitPools = new LinkedHashMap<>();
    private final Random random = new Random();

    public Game(GameData data, PlayerStore players) {
        this.data = data;
        this.players = players;
        this.quests = new QuestService(relations, id -> players.profile(UUID.fromString(id)).purse());
        this.diplomacy = new Diplomacy(data.realm(), data.season(), data.balance());
        this.npcFactory = new NpcLordFactory(data.tree(), data.balance());
        for (Settlement settlement : data.realm().settlements()) {
            if (settlement.type() == SettlementType.TOWN) {
                Market market = new Market();
                for (Good good : data.goods().values()) {
                    market.list(good, MARKET_TARGET_STOCK, MARKET_TARGET_STOCK);
                }
                markets.put(settlement.id(), market);
            }
            if (settlement.type().isFortified()) {
                garrisons.put(settlement.id(), npcFactory.createGarrison(settlement.id(), settlement.culture(),
                        data.garrisonSizes().getOrDefault(settlement.id(), 50), random));
            }
            if (settlement.type() != SettlementType.CASTLE) {
                recruitPools.put(settlement.id(), RECRUIT_POOL_MAX / 2);
            }
        }
        for (Clan clan : data.realm().clans()) {
            if (clan.npc() && clan.kingdomId() != null) {
                String culture = data.realm().kingdom(clan.kingdomId()).culture();
                lordParties.put(clan.id(), npcFactory.createLordParty(clan.leaderId(), culture, clan.tier(), random));
            }
        }
        refreshBoards();
    }

    public GameData data() {
        return data;
    }

    public TroopTree tree() {
        return data.tree();
    }

    public Realm realm() {
        return data.realm();
    }

    public Season season() {
        return data.season();
    }

    public PlayerStore players() {
        return players;
    }

    public RelationBook relations() {
        return relations;
    }

    public QuestService quests() {
        return quests;
    }

    public Diplomacy diplomacy() {
        return diplomacy;
    }

    public Map<String, Market> markets() {
        return markets;
    }

    public Map<String, Party> lordParties() {
        return lordParties;
    }

    public Map<String, Party> garrisons() {
        return garrisons;
    }

    public Map<String, Integer> recruitPools() {
        return recruitPools;
    }

    public Instant now() {
        return Instant.now();
    }

    public PlayerProfile profile(Player player) {
        PlayerProfile profile = players.profile(player.getUniqueId(), player.getName());
        profile.party().setSizeLimit(partyLimit(player.getUniqueId()));
        return profile;
    }

    public Purse purse(Player player) {
        return profile(player).purse();
    }

    public Optional<Clan> clanOf(UUID playerId) {
        return realm().clanOfMember(playerId.toString());
    }

    public int partyLimit(UUID playerId) {
        return PartyUpkeep.sizeLimit(clanOf(playerId).map(Clan::tier).orElse(0), 0);
    }

    public Optional<Settlement> settlementAt(Location location) {
        for (SettlementSite site : data.sites().values()) {
            if (site.contains(location)) {
                return realm().findSettlement(site.settlementId());
            }
        }
        return Optional.empty();
    }

    /** Oyuncunun bulundugu yerde makami olan NPC kral. */
    public Optional<NpcKing> kingAt(Location location) {
        Optional<Settlement> here = settlementAt(location);
        if (here.isEmpty()) {
            return Optional.empty();
        }
        for (NpcKing king : data.kings().values()) {
            if (king.seatId().equals(here.get().id())) {
                return Optional.of(king);
            }
        }
        return Optional.empty();
    }

    public void refreshBoards() {
        List<Good> goods = List.copyOf(data.goods().values());
        for (NpcKing king : data.kings().values()) {
            quests.refreshBoard(king, goods, realm().enemiesOf(king.kingdomId()), random);
        }
    }

    /** Oyuncuya un ekler (klani varsa). */
    public void addRenown(UUID playerId, int amount) {
        if (amount > 0) {
            clanOf(playerId).ifPresent(clan -> clan.addRenown(amount));
        }
    }

    /** Gorev ilerlemesini isler, tamamlananlari oyuncuya bildirir. */
    public void progressQuest(Player player, QuestType type, String target, int amount) {
        for (Quest done : quests.progress(player.getUniqueId().toString(), type, target, amount, now())) {
            addRenown(player.getUniqueId(), done.offer().rewardRenown());
            Msg.ok(player, "Görev tamamlandı: <gold>" + done.offer().type().displayName()
                    + "</gold> · <yellow>+" + done.offer().rewardDenars() + " dinar</yellow>, +"
                    + done.offer().rewardRelation() + " ilişki, +" + done.offer().rewardRenown() + " ün");
        }
    }

    /** Bir sefer gunu: maaslar, iyilesme, uretim, asker havuzlari, gorev panolari. */
    public void dailyTick() {
        for (Player player : NpcTags.realPlayers()) {
            PlayerProfile profile = profile(player);
            Party party = profile.party();
            if (party.isEmpty()) {
                continue;
            }
            PartyUpkeep.Result result = PartyUpkeep.dailyTick(party, tree(), profile.purse());
            if (party.totalCount() > party.healthyCount()) {
                party.healWounded(DAILY_HEAL);
            }
            if (result.paidInFull()) {
                Msg.info(player, "Yeni gün: askerlerine <yellow>" + result.wagePaid() + " dinar</yellow> maaş ödendi.");
            } else {
                Msg.error(player, "Maaşları ödeyemedin! Moral düşüyor (" + party.morale() + ").");
            }
            if (result.deserters() > 0) {
                Msg.error(player, result.deserters() + " asker firar etti.");
            }
        }
        for (Settlement village : realm().settlements()) {
            String produce = data.villageProduce().get(village.id());
            Market market = village.boundTo() == null ? null : markets.get(village.boundTo());
            if (produce != null && market != null) {
                market.adjustStock(produce, 5 + village.prosperity() / 200);
            }
        }
        for (Market market : markets.values()) {
            for (String goodId : market.goods().keySet()) {
                market.adjustStock(goodId, -TOWN_CONSUMPTION);
            }
        }
        recruitPools.replaceAll((id, pool) -> {
            int regen = 2 + realm().settlement(id).prosperity() / 2000;
            return Math.min(RECRUIT_POOL_MAX, pool + regen);
        });
        for (Quest failed : quests.expire(now())) {
            Player player = Bukkit.getPlayer(UUID.fromString(failed.assigneeId()));
            if (player != null) {
                Msg.error(player, "Görevin süresi doldu: " + failed.offer().type().displayName()
                        + " (-" + QuestService.FAILURE_RELATION_PENALTY + " ilişki)");
            }
        }
        refreshBoards();
    }

    /** Oyuncu adindan klan/krallik kimligi uretir ("Kurt Ogullari" -> "kurt_ogullari"). */
    public static String slug(String name) {
        String ascii = Normalizer.normalize(name.toLowerCase(Locale.ROOT)
                        .replace('ı', 'i').replace('ğ', 'g').replace('ş', 's')
                        .replace('ç', 'c').replace('ö', 'o').replace('ü', 'u'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return ascii.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    public List<Kingdom> kingdomsSorted() {
        List<Kingdom> list = new ArrayList<>(realm().kingdoms());
        list.sort((a, b) -> Boolean.compare(!a.npc(), !b.npc()));
        return list;
    }
}
