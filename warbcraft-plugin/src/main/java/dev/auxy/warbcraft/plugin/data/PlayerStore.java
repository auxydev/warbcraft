package dev.auxy.warbcraft.plugin.data;

import dev.auxy.warbcraft.core.economy.Purse;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.party.PartyKind;
import dev.auxy.warbcraft.core.party.TroopStack;
import dev.auxy.warbcraft.core.troop.TroopTree;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Oyuncu verisini {@code oyuncular/<uuid>.yml} dosyalarinda tutar. */
public final class PlayerStore {

    private final File directory;
    private final TroopTree tree;
    private final int startingDenars;
    private final Logger logger;
    private final Map<UUID, PlayerProfile> loaded = new HashMap<>();

    public PlayerStore(File directory, TroopTree tree, int startingDenars, Logger logger) {
        this.directory = directory;
        this.tree = tree;
        this.startingDenars = startingDenars;
        this.logger = logger;
    }

    /** Profili getirir; yuklu degilse diskten okur, hic yoksa yeni olusturur. */
    public PlayerProfile profile(UUID id, String name) {
        PlayerProfile profile = loaded.computeIfAbsent(id, this::read);
        if (name != null) {
            profile.setName(name);
        }
        return profile;
    }

    public PlayerProfile profile(UUID id) {
        return profile(id, null);
    }

    public Collection<PlayerProfile> loaded() {
        return loaded.values();
    }

    public void unload(UUID id) {
        PlayerProfile profile = loaded.remove(id);
        if (profile != null) {
            write(profile);
        }
    }

    public void saveAll() {
        loaded.values().forEach(this::write);
    }

    private PlayerProfile read(UUID id) {
        File file = file(id);
        if (!file.exists()) {
            return new PlayerProfile(id, id.toString(), new Purse(startingDenars),
                    new Party(id.toString(), PartyKind.PLAYER, 0));
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Party party = new Party(id.toString(), PartyKind.PLAYER, 0);
        party.setMorale(yaml.getInt("parti.moral", Party.DEFAULT_MORALE));
        ConfigurationSection roster = yaml.getConfigurationSection("parti.birlikler");
        if (roster != null) {
            for (String troopId : roster.getKeys(false)) {
                if (tree.find(troopId).isEmpty()) {
                    logger.warning(id + ": artik tanimli olmayan birlik atlandi: " + troopId);
                    continue;
                }
                party.forceAddTroops(troopId, roster.getInt(troopId + ".saglam"), roster.getInt(troopId + ".yarali"));
                int xp = roster.getInt(troopId + ".xp");
                if (xp > 0 && party.count(troopId) > 0) {
                    party.addXp(troopId, xp);
                }
            }
        }
        return new PlayerProfile(id, yaml.getString("ad", id.toString()),
                new Purse(Math.max(0, yaml.getLong("dinar"))), party);
    }

    private void write(PlayerProfile profile) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ad", profile.name());
        yaml.set("dinar", profile.purse().balance());
        yaml.set("parti.moral", profile.party().morale());
        for (Map.Entry<String, TroopStack> entry : profile.party().roster().entrySet()) {
            String path = "parti.birlikler." + entry.getKey();
            yaml.set(path + ".saglam", entry.getValue().healthy());
            yaml.set(path + ".yarali", entry.getValue().wounded());
            yaml.set(path + ".xp", entry.getValue().xp());
        }
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Klasor olusturulamadi: " + directory);
            }
            yaml.save(file(profile.id()));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Oyuncu verisi kaydedilemedi: " + profile.id(), e);
        }
    }

    private File file(UUID id) {
        return new File(directory, id + ".yml");
    }
}
