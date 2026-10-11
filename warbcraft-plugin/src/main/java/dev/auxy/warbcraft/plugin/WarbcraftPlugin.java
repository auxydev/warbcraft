package dev.auxy.warbcraft.plugin;

import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.plugin.command.AdminCommand;
import dev.auxy.warbcraft.plugin.command.ClanCommand;
import dev.auxy.warbcraft.plugin.command.DenarCommand;
import dev.auxy.warbcraft.plugin.command.KingdomCommand;
import dev.auxy.warbcraft.plugin.command.MarketCommand;
import dev.auxy.warbcraft.plugin.command.MenuCommand;
import dev.auxy.warbcraft.plugin.command.PartyCommand;
import dev.auxy.warbcraft.plugin.command.QuestCommand;
import dev.auxy.warbcraft.plugin.command.RecruitCommand;
import dev.auxy.warbcraft.plugin.command.SeasonCommand;
import dev.auxy.warbcraft.plugin.data.DataLoader;
import dev.auxy.warbcraft.plugin.data.GameData;
import dev.auxy.warbcraft.plugin.data.PlayerStore;
import dev.auxy.warbcraft.plugin.data.SettlementSite;
import dev.auxy.warbcraft.plugin.data.StateStore;
import dev.auxy.warbcraft.plugin.field.Retinue;
import dev.auxy.warbcraft.plugin.field.RetinueListener;
import dev.auxy.warbcraft.plugin.gui.Menus;
import dev.auxy.warbcraft.plugin.npc.NpcBodies;
import dev.auxy.warbcraft.plugin.npc.NpcTags;
import dev.auxy.warbcraft.plugin.listener.Bandits;
import dev.auxy.warbcraft.plugin.listener.GameListener;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public final class WarbcraftPlugin extends JavaPlugin {

    private static final String TROOPS_FILE = "birlikler.yml";
    private static final String WORLD_FILE = "dunya.yml";
    private static final String BANDITS_FILE = "haydutlar.yml";
    private static final String STATE_FILE = "durum.yml";
    private static final long AUTOSAVE_TICKS = 5 * 60 * 20L;
    /** {@code -Dwarbcraft.deneme=true}: yerel deneme sunucusu (gradlew runServer). */
    private static final boolean TEST_MODE = Boolean.getBoolean("warbcraft.deneme");
    private static final int TEST_SITE_SPACING = 120;
    /** dunya.yml'de konumun /wbadmin konum ile elle verildigini gosteren anahtar. */
    private static final String MANUAL_SITE = "elle";

    private Game game;
    private Retinue retinue;
    private Bandits bandits;
    private NpcBodies bodies;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing(TROOPS_FILE);
        saveResourceIfMissing(WORLD_FILE);
        saveResourceIfMissing(BANDITS_FILE);

        if (TEST_MODE) {
            applyTestConfig();
        }
        GameData data;
        try {
            data = DataLoader.load(getConfig(), dataFile(TROOPS_FILE), dataFile(WORLD_FILE), dataFile(BANDITS_FILE));
            archivePreviousSeason(data.season().number());
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Warbcraft başlatılamadı, eklenti kapatılıyor: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PlayerStore players = new PlayerStore(dataFile("oyuncular"), data.tree(),
                data.settings().startingDenars(), getLogger());
        game = new Game(data, players);
        StateStore.load(game, dataFile(STATE_FILE));
        if (TEST_MODE) {
            placeSettlementsNearSpawn(data);
        }

        bodies = new NpcBodies();
        NpcTags tags = new NpcTags(this);
        bandits = new Bandits(this, game, bodies, tags);
        retinue = new Retinue(this, game, bodies, tags);
        ClanCommand clans = new ClanCommand(game);
        Menus menus = new Menus(this, game, retinue, clans);
        register("parti", new PartyCommand(game, retinue));
        register("topla", new RecruitCommand(game));
        register("pazar", new MarketCommand(game));
        register("gorev", new QuestCommand(game));
        register("klan", clans);
        register("krallik", new KingdomCommand(game));
        register("sezon", new SeasonCommand(game));
        register("dinar", new DenarCommand(game));
        register("menu", new MenuCommand(game, menus::openMain));
        register("wbadmin", new AdminCommand(game, this, bandits, retinue));
        getServer().getPluginManager().registerEvents(new GameListener(game, bandits, retinue), this);
        getServer().getPluginManager().registerEvents(new RetinueListener(retinue, menus::openParty), this);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getOnlinePlayers().forEach(game::profile);
        getServer().getOnlinePlayers().forEach(menus::giveJournal);
        retinue.start();
        bandits.start();

        long dayTicks = data.settings().dayLengthMinutes() * 60L * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            game.dailyTick();
            saveAll();
        }, dayTicks, dayTicks);
        getServer().getScheduler().runTaskTimer(this, this::saveAll, AUTOSAVE_TICKS, AUTOSAVE_TICKS);

        getLogger().info("Warbcraft hazır: " + data.tree().size() + " birlik, " + data.realm().kingdoms().size()
                + " krallık, " + data.realm().settlements().size() + " yerleşim · sezon " + data.season().number()
                + " (" + data.season().phaseAt(game.now()).displayName() + ")");
    }

    @Override
    public void onDisable() {
        if (retinue != null) {
            retinue.shutdown();
        }
        if (bandits != null) {
            bandits.shutdown();
        }
        if (bodies != null) {
            bodies.destroyAll();
        }
        if (game != null) {
            saveAll();
        }
    }

    public void saveAll() {
        game.players().saveAll();
        try {
            StateStore.save(game, dataFile(STATE_FILE));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Dünya durumu kaydedilemedi", e);
        }
    }

    /** Yerlesim merkezini ayarlar ve dunya.yml'e yazar. */
    public void setSite(String settlementId, Location location, int radius) {
        String world = Objects.requireNonNull(location.getWorld(), "world").getName();
        game.data().sites().put(settlementId, new SettlementSite(settlementId, world,
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), radius));
        File file = dataFile(WORLD_FILE);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "yerlesimler." + settlementId + ".konum";
        yaml.set(path + ".dunya", world);
        yaml.set(path + ".x", location.getBlockX());
        yaml.set(path + ".y", location.getBlockY());
        yaml.set(path + ".z", location.getBlockZ());
        yaml.set(path + ".yaricap", radius);
        yaml.set(path + "." + MANUAL_SITE, true);
        try {
            yaml.save(file);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, WORLD_FILE + " kaydedilemedi", e);
        }
    }

    /**
     * Deneme modu: sezon dun baslamis ve hazirliksiz, savas penceresi her gun acik, bol para.
     * Yalnizca bellekteki ayarlari degistirir; config.yml dosyasina yazilmaz.
     */
    private void applyTestConfig() {
        ZoneId zone = ZoneId.of(getConfig().getString("sezon.saat-dilimi", "Europe/Istanbul"));
        getConfig().set("sezon.baslangic", LocalDateTime.now(zone).minusDays(1).truncatedTo(ChronoUnit.MINUTES).toString());
        getConfig().set("sezon.hazirlik-gun", 0);
        getConfig().set("sezon.kusatma-ihbar-saat", 0);
        getConfig().set("sezon.savas-pencereleri", List.of(Map.of(
                "gunler", List.of("pazartesi", "sali", "carsamba", "persembe", "cuma", "cumartesi", "pazar"),
                "baslangic", "00:00",
                "bitis", "23:59")));
        getConfig().set("oyun.baslangic-dinar", 10_000);
        getConfig().set("oyun.gun-uzunlugu-dakika", 10);
        getLogger().warning("DENEME MODU: sezon başlamış, savaş penceresi hep açık, başlangıç parası 10000.");
    }

    /**
     * Deneme modu: yerlesimleri spawn cevresine 5 sutunluk bir izgaraya dizer (dosyaya yazmaz).
     * {@code /wbadmin konum} ile elle konulanlar yerinde kalir.
     */
    private void placeSettlementsNearSpawn(GameData data) {
        Location spawn = getServer().getWorlds().getFirst().getSpawnLocation();
        String world = Objects.requireNonNull(spawn.getWorld(), "world").getName();
        YamlConfiguration worldYaml = YamlConfiguration.loadConfiguration(dataFile(WORLD_FILE));
        int index = 0;
        for (Settlement settlement : data.realm().settlements()) {
            if (worldYaml.getBoolean("yerlesimler." + settlement.id() + ".konum." + MANUAL_SITE)) {
                getLogger().info("Elle konulmuş, yerinde kalıyor: " + settlement.name());
                index++;
                continue;
            }
            int x = spawn.getBlockX() + (index % 5) * TEST_SITE_SPACING;
            int z = spawn.getBlockZ() + (index / 5) * TEST_SITE_SPACING;
            data.sites().put(settlement.id(), new SettlementSite(settlement.id(), world, x, spawn.getBlockY(), z, 50));
            getLogger().info("Deneme konumu: " + settlement.name() + " -> " + x + ", " + z);
            index++;
        }
    }

    /**
     * Yeni sezon basladiysa (config'deki sezon numarasi degistiyse) onceki sezonun durumu ve
     * oyuncu verisi {@code arsiv/sezon-N/} altina tasinir; herkes sifirdan baslar.
     */
    private void archivePreviousSeason(int currentSeason) {
        File state = dataFile(STATE_FILE);
        int saved = StateStore.savedSeason(state);
        if (saved == 0 || saved == currentSeason) {
            return;
        }
        File archive = dataFile("arsiv/sezon-" + saved);
        if (!archive.mkdirs() && !archive.isDirectory()) {
            throw new IllegalStateException("Arşiv klasörü oluşturulamadı: " + archive);
        }
        File players = dataFile("oyuncular");
        boolean moved = state.renameTo(new File(archive, STATE_FILE))
                & (!players.exists() || players.renameTo(new File(archive, "oyuncular")));
        if (!moved) {
            throw new IllegalStateException("Önceki sezon arşivlenemedi: " + archive);
        }
        getLogger().info("Sezon " + saved + " arşivlendi; sezon " + currentSeason + " sıfırdan başlıyor.");
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), () -> "plugin.yml'de komut yok: " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void saveResourceIfMissing(String name) {
        if (!dataFile(name).exists()) {
            saveResource(name, false);
        }
    }

    private File dataFile(String name) {
        return new File(getDataFolder(), name);
    }
}
