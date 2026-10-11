package dev.auxy.warbcraft.plugin.npc;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Warbcraft NPC'lerinin varlik etiketleri. Asker ve atlari sahibinin kimligini, askerler birligini,
 * haydutlar haydut isaretini tasir. Etiketler yalnizca bellekte yasayan NPC varliklarindadir.
 */
public final class NpcTags {

    private final NamespacedKey owner;
    private final NamespacedKey troop;
    private final NamespacedKey bandit;
    private final NamespacedKey king;
    private final NamespacedKey guard;

    public NpcTags(Plugin plugin) {
        this.owner = new NamespacedKey(plugin, "asker_sahibi");
        this.troop = new NamespacedKey(plugin, "asker_birlik");
        this.bandit = new NamespacedKey(plugin, "haydut");
        this.king = new NamespacedKey(plugin, "kral");
        this.guard = new NamespacedKey(plugin, "nobetci");
    }

    public void tagSoldier(Entity entity, UUID ownerId, String troopId) {
        tagMount(entity, ownerId);
        entity.getPersistentDataContainer().set(troop, PersistentDataType.STRING, troopId);
    }

    public void tagMount(Entity entity, UUID ownerId) {
        entity.getPersistentDataContainer().set(owner, PersistentDataType.STRING, ownerId.toString());
    }

    public void tagBandit(Entity entity, String troopId) {
        entity.getPersistentDataContainer().set(bandit, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(troop, PersistentDataType.STRING, troopId);
    }

    /** NPC kral (baskentinde durur, sag tikla gorev panosu). */
    public void tagKing(Entity entity, String kingId) {
        entity.getPersistentDataContainer().set(king, PersistentDataType.STRING, kingId);
    }

    /** Bir yerlesimin nobetcisi (garnizondan). */
    public void tagGuard(Entity entity, String settlementId, String troopId) {
        entity.getPersistentDataContainer().set(guard, PersistentDataType.STRING, settlementId);
        entity.getPersistentDataContainer().set(troop, PersistentDataType.STRING, troopId);
    }

    public Optional<String> king(Entity entity) {
        return Optional.ofNullable(entity == null ? null : entity.getPersistentDataContainer().get(king, PersistentDataType.STRING));
    }

    /** Nobetcinin yerlesimi. */
    public Optional<String> guardOf(Entity entity) {
        return Optional.ofNullable(entity == null ? null : entity.getPersistentDataContainer().get(guard, PersistentDataType.STRING));
    }

    /** Asker ya da atlari. */
    public boolean isRetinue(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(owner, PersistentDataType.STRING);
    }

    public boolean isSoldier(Entity entity) {
        return isRetinue(entity) && entity.getPersistentDataContainer().has(troop, PersistentDataType.STRING);
    }

    public boolean isBandit(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(bandit, PersistentDataType.BYTE);
    }

    public Optional<UUID> owner(Entity entity) {
        String value = entity == null ? null : entity.getPersistentDataContainer().get(owner, PersistentDataType.STRING);
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }

    public Optional<String> troop(Entity entity) {
        return Optional.ofNullable(entity == null ? null : entity.getPersistentDataContainer().get(troop, PersistentDataType.STRING));
    }

    /**
     * Cevrimici gercek oyuncular. Citizens'in insan NPC'leri de {@link Bukkit#getOnlinePlayers()} icinde
     * gorunur; onlara parti, ordu ya da haydut cetesi acilmamali.
     */
    public static List<Player> realPlayers() {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isAnyNpc(player)) {
                players.add(player);
            }
        }
        return players;
    }

    /** Citizens'in (ya da baska bir eklentinin) NPC'si mi? Gercek oyuncu sanilmamali. */
    public static boolean isAnyNpc(Entity entity) {
        return entity != null && entity.hasMetadata("NPC");
    }
}
