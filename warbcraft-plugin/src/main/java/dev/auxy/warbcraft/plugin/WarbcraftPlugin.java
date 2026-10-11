package dev.auxy.warbcraft.plugin;

import dev.auxy.warbcraft.plugin.command.AdminCommand;
import dev.auxy.warbcraft.plugin.command.ClanCommand;
import dev.auxy.warbcraft.plugin.command.DenarCommand;
import dev.auxy.warbcraft.plugin.command.KingdomCommand;
import dev.auxy.warbcraft.plugin.command.MarketCommand;
import dev.auxy.warbcraft.plugin.command.PartyCommand;
import dev.auxy.warbcraft.plugin.command.QuestCommand;
import dev.auxy.warbcraft.plugin.command.RecruitCommand;
import dev.auxy.warbcraft.plugin.command.SeasonCommand;
import dev.auxy.warbcraft.plugin.data.DataLoader;
import dev.auxy.warbcraft.plugin.data.GameData;
import dev.auxy.warbcraft.plugin.data.PlayerStore;
import dev.auxy.warbcraft.plugin.data.SettlementSite;
import dev.auxy.warbcraft.plugin.data.StateStore;
import dev.auxy.warbcraft.plugin.listener.Bandits;
import dev.auxy.warbcraft.plugin.listener.GameListener;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

public final class WarbcraftPlugin extends JavaPlugin {

    private static final String TROOPS_FILE = "birlikler.yml";
    private static final String WORLD_FILE = "dunya.yml";
    private static final String STATE_FILE = "durum.yml";
    private static final long AUTOSAVE_TICKS = 5 * 60 * 20L;

    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing(TROOPS_FILE);
        saveResourceIfMissing(WORLD_FILE);

        GameData data;
        try {
            data = DataLoader.load(getConfig(), dataFile(TROOPS_FILE), dataFile(WORLD_FILE));
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

        Bandits bandits = new Bandits(this);
        register("parti", new PartyCommand(game));
        register("topla", new RecruitCommand(game));
        register("pazar", new MarketCommand(game));
        register("gorev", new QuestCommand(game));
        register("klan", new ClanCommand(game));
        register("krallik", new KingdomCommand(game));
        register("sezon", new SeasonCommand(game));
        register("dinar", new DenarCommand(game));
        register("wbadmin", new AdminCommand(game, this, bandits));
        getServer().getPluginManager().registerEvents(new GameListener(game, bandits), this);
        getServer().getOnlinePlayers().forEach(game::profile);

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
        try {
            yaml.save(file);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, WORLD_FILE + " kaydedilemedi", e);
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
