package dev.auxy.warbcraft.plugin.data;

import dev.auxy.warbcraft.core.bandit.BanditKind;
import dev.auxy.warbcraft.core.bandit.BanditSpawner;
import dev.auxy.warbcraft.core.economy.Good;
import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Realm;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.faction.SettlementType;
import dev.auxy.warbcraft.core.npc.NpcBalance;
import dev.auxy.warbcraft.core.npc.NpcKing;
import dev.auxy.warbcraft.core.season.Season;
import dev.auxy.warbcraft.core.season.WarWindow;
import dev.auxy.warbcraft.core.troop.TroopCategory;
import dev.auxy.warbcraft.core.troop.TroopTree;
import dev.auxy.warbcraft.core.troop.TroopType;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * config.yml, birlikler.yml ve dunya.yml dosyalarini okur. Hatali veri sunucuyu acilista
 * durdurur; yari yuklenmis bir dunyayla oynamaktan iyidir.
 */
public final class DataLoader {

    private static final Map<String, DayOfWeek> DAYS = Map.ofEntries(
            Map.entry("pazartesi", DayOfWeek.MONDAY),
            Map.entry("sali", DayOfWeek.TUESDAY),
            Map.entry("carsamba", DayOfWeek.WEDNESDAY),
            Map.entry("persembe", DayOfWeek.THURSDAY),
            Map.entry("cuma", DayOfWeek.FRIDAY),
            Map.entry("cumartesi", DayOfWeek.SATURDAY),
            Map.entry("pazar", DayOfWeek.SUNDAY));

    /** Haydut birliklerinin kulturu; toplanamaz, kralliga kultur olarak secilemez. */
    public static final String BANDIT_CULTURE = "haydut";

    private DataLoader() {
    }

    public static GameData load(FileConfiguration config, File troopsFile, File worldFile, File banditsFile) {
        YamlConfiguration troopsYaml = YamlConfiguration.loadConfiguration(troopsFile);
        YamlConfiguration worldYaml = YamlConfiguration.loadConfiguration(worldFile);
        YamlConfiguration banditsYaml = YamlConfiguration.loadConfiguration(banditsFile);

        Map<String, String> cultureNames = new LinkedHashMap<>();
        Map<String, Skin> cultureSkins = new LinkedHashMap<>();
        ConfigurationSection cultures = section(troopsYaml, "kulturler");
        for (String id : cultures.getKeys(false)) {
            cultureNames.put(id, cultures.getString(id + ".ad", id));
            cultureSkins.put(id, Skin.parse(cultures.getConfigurationSection(id + ".skin"), "kulturler." + id + ".skin"));
        }
        TroopTree tree = loadTroops(section(troopsYaml, "birlikler"), cultureNames);
        NpcBalance balance = loadBalance(section(config, "npc-denge"));
        BanditData bandits = loadBandits(banditsYaml, tree, balance);

        Map<String, Good> goods = new LinkedHashMap<>();
        Map<String, Material> goodItems = new LinkedHashMap<>();
        ConfigurationSection goodsSection = section(worldYaml, "mallar");
        for (String id : goodsSection.getKeys(false)) {
            ConfigurationSection g = section(goodsSection, id);
            goods.put(id, new Good(id, g.getString("ad", id), g.getInt("fiyat")));
            goodItems.put(id, material(g.getString("esya"), "mallar." + id));
        }

        Realm realm = new Realm();
        Map<String, NpcKing> kings = new LinkedHashMap<>();
        ConfigurationSection kingdoms = section(worldYaml, "kralliklar");
        for (String id : kingdoms.getKeys(false)) {
            ConfigurationSection k = section(kingdoms, id);
            String culture = require(k, "kultur");
            if (!cultureNames.containsKey(culture)) {
                throw new IllegalArgumentException("kralliklar." + id + ": bilinmeyen kultur " + culture);
            }
            String kingId = require(k, "kral.id");
            realm.addKingdom(new Kingdom(id, require(k, "ad"), culture, kingId, true));
            kings.put(kingId, new NpcKing(kingId, require(k, "kral.ad"), id, require(k, "kral.makam")));
        }

        ConfigurationSection clans = section(worldYaml, "klanlar");
        for (String id : clans.getKeys(false)) {
            ConfigurationSection c = section(clans, id);
            Clan clan = new Clan(id, require(c, "ad"), require(c, "lider"), true);
            clan.setKingdomId(c.getString("krallik"));
            clan.addRenown(c.getInt("un", 0));
            realm.addClan(clan);
        }

        Map<String, SettlementSite> sites = new LinkedHashMap<>();
        Map<String, String> villageProduce = new LinkedHashMap<>();
        Map<String, Integer> garrisonSizes = new LinkedHashMap<>();
        ConfigurationSection settlements = section(worldYaml, "yerlesimler");
        // Koyler bagli olduklari sehirden sonra eklenmeli; once surlu yerlesimler.
        List<String> ordered = new ArrayList<>();
        for (String id : settlements.getKeys(false)) {
            if (settlementType(require(section(settlements, id), "tur")) != SettlementType.VILLAGE) {
                ordered.add(id);
            }
        }
        for (String id : settlements.getKeys(false)) {
            if (!ordered.contains(id)) {
                ordered.add(id);
            }
        }
        for (String id : ordered) {
            ConfigurationSection s = section(settlements, id);
            SettlementType type = settlementType(require(s, "tur"));
            realm.addSettlement(new Settlement(id, require(s, "ad"), type, require(s, "kultur"), require(s, "sahip"),
                    s.getString("bagli"), s.getInt("refah", 1000)));
            if (type == SettlementType.VILLAGE) {
                String produce = require(s, "uretim");
                if (!goods.containsKey(produce)) {
                    throw new IllegalArgumentException("yerlesimler." + id + ": bilinmeyen mal " + produce);
                }
                villageProduce.put(id, produce);
            } else {
                garrisonSizes.put(id, s.getInt("garnizon", 50));
            }
            ConfigurationSection loc = s.getConfigurationSection("konum");
            if (loc != null) {
                sites.put(id, new SettlementSite(id, loc.getString("dunya", "world"), loc.getDouble("x"),
                        loc.getDouble("y"), loc.getDouble("z"), loc.getInt("yaricap", 50)));
            }
        }
        for (NpcKing king : kings.values()) {
            if (realm.findSettlement(king.seatId()).isEmpty()) {
                throw new IllegalArgumentException(king.id() + ": makam yerlesimi bulunamadi: " + king.seatId());
            }
        }

        return new GameData(tree, realm, loadSeason(section(config, "sezon")), balance,
                loadSettings(section(config, "oyun")), kings, goods, goodItems, sites, villageProduce,
                garrisonSizes, cultureNames, cultureSkins, bandits);
    }

    /**
     * Haydut turleri. Dogrulama: birlikler tanimli ve "haydut" kulturunden, kademeleri NPC dengesinin
     * izin verdigi en yuksek kademeyi asmaz (haydutlar oyunculardan guclu olamaz), tam bir "*" turu var.
     */
    private static BanditData loadBandits(YamlConfiguration yaml, TroopTree tree, NpcBalance balance) {
        ConfigurationSection s = section(yaml, "cete");
        BanditSpawner.Settings settings = new BanditSpawner.Settings(
                s.getInt("deneme-araligi-saniye", 45), s.getDouble("olasilik", 0.35), s.getInt("en-fazla-cete", 2),
                s.getInt("en-yakin", 32), s.getInt("en-uzak", 56));
        Map<String, BanditKind> kinds = new LinkedHashMap<>();
        Map<String, BanditData.Look> looks = new LinkedHashMap<>();
        ConfigurationSection types = section(yaml, "turler");
        for (String id : types.getKeys(false)) {
            ConfigurationSection t = section(types, id);
            String path = "turler." + id;
            Map<String, BanditKind.Range> roster = new LinkedHashMap<>();
            ConfigurationSection troops = section(t, "birlikler");
            for (String troopId : troops.getKeys(false)) {
                TroopType troop = tree.find(troopId).orElseThrow(() ->
                        new IllegalArgumentException(path + ": bilinmeyen birlik " + troopId));
                if (!troop.culture().equals(BANDIT_CULTURE)) {
                    throw new IllegalArgumentException(path + ": " + troopId + " haydut kulturunden degil");
                }
                if (troop.tier() > balance.maxLordTroopTier()) {
                    throw new IllegalArgumentException(path + ": " + troopId + " kademesi NPC siniri "
                            + balance.maxLordTroopTier() + "'u asiyor");
                }
                List<Integer> range = troops.getIntegerList(troopId);
                if (range.size() != 2) {
                    throw new IllegalArgumentException(path + ".birlikler." + troopId + ": [en-az, en-cok] olmali");
                }
                roster.put(troopId, new BanditKind.Range(range.get(0), range.get(1)));
            }
            List<String> biomes = t.getStringList("biyomlar");
            for (String biome : biomes) {
                if (!biome.equals(BanditKind.ANY_BIOME)
                        && Registry.BIOME.get(NamespacedKey.minecraft(biome.toLowerCase(Locale.ROOT))) == null) {
                    throw new IllegalArgumentException(path + ": bilinmeyen biyom " + biome);
                }
            }
            kinds.put(id, new BanditKind(id, require(t, "ad"), Set.copyOf(biomes), roster));
            looks.put(id, new BanditData.Look(color(t.getString("renk", "#5A4632"), path + ".renk"),
                    Skin.parse(t.getConfigurationSection("skin"), path + ".skin")));
        }
        if (kinds.values().stream().noneMatch(BanditKind::isFallback)) {
            throw new IllegalArgumentException("haydutlar.yml: biyomlar: [\"*\"] olan bir tur gerekli");
        }
        return new BanditData(settings, kinds, looks);
    }

    private static Color color(String hex, String path) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(path + ": renk #RRGGBB olmali: " + hex, e);
        }
    }

    private static TroopTree loadTroops(ConfigurationSection section, Map<String, String> cultures) {
        List<TroopType> troops = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection t = section(section, id);
            String culture = require(t, "kultur");
            if (!cultures.containsKey(culture)) {
                throw new IllegalArgumentException("birlikler." + id + ": bilinmeyen kultur " + culture);
            }
            troops.add(new TroopType(id, require(t, "ad"), culture, t.getInt("kademe"),
                    category(require(t, "sinif")), t.getInt("guc"), t.getInt("maas"), t.getInt("terfi-xp", 0),
                    t.getStringList("terfi")));
        }
        return TroopTree.of(troops);
    }

    private static Season loadSeason(ConfigurationSection s) {
        ZoneId zone = ZoneId.of(s.getString("saat-dilimi", "Europe/Istanbul"));
        List<WarWindow> windows = new ArrayList<>();
        for (Map<?, ?> raw : s.getMapList("savas-pencereleri")) {
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            Object dayList = raw.get("gunler");
            if (dayList instanceof List<?> list) {
                for (Object day : list) {
                    days.add(day(String.valueOf(day)));
                }
            }
            windows.add(new WarWindow(days, LocalTime.parse(String.valueOf(raw.get("baslangic"))),
                    LocalTime.parse(String.valueOf(raw.get("bitis")))));
        }
        return new Season(s.getInt("numara", 1),
                LocalDateTime.parse(require(s, "baslangic")).atZone(zone).toInstant(), zone,
                s.getInt("hazirlik-gun"), s.getInt("sefer-gun"), s.getInt("son-gun"),
                s.getInt("kusatma-ihbar-saat"), windows);
    }

    private static NpcBalance loadBalance(ConfigurationSection s) {
        NpcBalance d = NpcBalance.DEFAULT;
        return new NpcBalance(
                s.getDouble("lord-parti-carpani", d.lordPartyFactor()),
                s.getInt("lord-en-yuksek-kademe", d.maxLordTroopTier()),
                s.getDouble("garnizon-carpani", d.garrisonFactor()),
                s.getInt("oyuncuya-savas-bekleme-gun", d.npcWarCooldownDays()),
                s.getInt("oyuncularla-en-fazla-savas", d.maxNpcWarsVsPlayers()));
    }

    private static GameSettings loadSettings(ConfigurationSection s) {
        GameSettings settings = new GameSettings(
                s.getInt("gun-uzunlugu-dakika", 60),
                s.getInt("asker-toplama-ucreti", 30),
                s.getInt("baslangic-dinar", 500),
                s.getInt("haydut-odulu", 15),
                s.getInt("klan-kurma-ucreti", 2000),
                s.getInt("krallik-icin-klan-kademesi", 2),
                s.getInt("vasal-icin-iliski", 10),
                s.getInt("sahada-asker", 12));
        if (settings.dayLengthMinutes() <= 0) {
            throw new IllegalArgumentException("oyun.gun-uzunlugu-dakika pozitif olmali");
        }
        if (settings.fieldCap() < 0) {
            throw new IllegalArgumentException("oyun.sahada-asker negatif olamaz");
        }
        return settings;
    }

    private static TroopCategory category(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "PIYADE" -> TroopCategory.INFANTRY;
            case "OKCU" -> TroopCategory.ARCHER;
            case "SUVARI" -> TroopCategory.CAVALRY;
            case "ATLI_OKCU" -> TroopCategory.HORSE_ARCHER;
            default -> throw new IllegalArgumentException("Bilinmeyen birlik sinifi: " + value);
        };
    }

    private static SettlementType settlementType(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SEHIR" -> SettlementType.TOWN;
            case "KALE" -> SettlementType.CASTLE;
            case "KOY" -> SettlementType.VILLAGE;
            default -> throw new IllegalArgumentException("Bilinmeyen yerlesim turu: " + value);
        };
    }

    private static DayOfWeek day(String value) {
        String key = value.toLowerCase(Locale.ROOT);
        DayOfWeek day = DAYS.get(key);
        return day != null ? day : DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static Material material(String name, String path) {
        Material material = name == null ? null : Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException(path + ": gecersiz esya " + name);
        }
        return material;
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("Eksik bolum: " + parent.getCurrentPath() + "." + path);
        }
        return section;
    }

    private static String require(ConfigurationSection section, String path) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Eksik alan: " + section.getCurrentPath() + "." + path);
        }
        return value;
    }
}
