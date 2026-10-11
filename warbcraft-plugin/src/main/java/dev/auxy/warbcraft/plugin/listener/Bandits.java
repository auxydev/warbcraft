package dev.auxy.warbcraft.plugin.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Vindicator;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Haydutlar: Warbcraft etiketi tasiyan yagmaci (arbaletli) ve eskiya (baltali) varliklari.
 * Ileride haydut kamplari ve dolasan haydut partileri bu siniftan dogacak.
 */
public final class Bandits {

    private final NamespacedKey key;

    public Bandits(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "haydut");
    }

    public boolean isBandit(Entity entity) {
        return entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** @return dogan haydut sayisi */
    public int spawnGroup(Location center, int amount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int spawned = 0;
        for (int i = 0; i < amount; i++) {
            Location at = center.clone().add(random.nextDouble(-6, 6), 0, random.nextDouble(-6, 6));
            at.setY(center.getWorld().getHighestBlockYAt(at) + 1);
            LivingEntity bandit = random.nextBoolean()
                    ? center.getWorld().spawn(at, Pillager.class)
                    : center.getWorld().spawn(at, Vindicator.class);
            bandit.customName(Component.text(bandit instanceof Pillager ? "Yağmacı" : "Eşkıya", NamedTextColor.DARK_RED));
            bandit.setCustomNameVisible(true);
            bandit.setRemoveWhenFarAway(false);
            bandit.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            spawned++;
        }
        return spawned;
    }
}
