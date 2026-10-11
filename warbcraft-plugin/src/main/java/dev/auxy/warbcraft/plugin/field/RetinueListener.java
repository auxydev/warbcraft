package dev.auxy.warbcraft.plugin.field;

import dev.auxy.warbcraft.plugin.npc.NpcTags;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Sahadaki askerlerin dunya olaylari: dost atesi, olum, hedef secimi, tiklama. */
public final class RetinueListener implements Listener {

    private final Retinue retinue;
    private final NpcTags tags;
    private final Consumer<Player> openPartyMenu;

    public RetinueListener(Retinue retinue, Consumer<Player> openPartyMenu) {
        this.retinue = retinue;
        this.tags = retinue.tags();
        this.openPartyMenu = openPartyMenu;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity attacker = Retinue.resolve(event.getDamager());
        Entity victim = event.getEntity();
        Optional<UUID> attackerSide = retinue.sideOf(attacker);
        if (attackerSide.isPresent() && attackerSide.equals(retinue.sideOf(victim))) {
            event.setCancelled(true);
            return;
        }
        if (!(attacker instanceof LivingEntity living)) {
            return;
        }
        if (victim instanceof Player owner && !NpcTags.isAnyNpc(owner)) {
            retinue.recordThreat(owner, living);
        } else if (attacker instanceof Player owner && !NpcTags.isAnyNpc(owner) && victim instanceof LivingEntity target
                && (retinue.isFoe(target, owner) || tags.isRetinue(target))) {
            retinue.recordThreat(owner, target);
        }
        if (tags.isRetinue(victim)) {
            retinue.onSoldierAttacked(victim, living);
        }
    }

    /** Asker ve atlari yere carpma ya da duvara sikisma ile olmez (isinlanma ve binme kazalari). */
    @EventHandler(ignoreCancelled = true)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (tags.isRetinue(event.getEntity()) && (event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION)) {
            event.setCancelled(true);
        }
    }

    /** Canavarlar sahibin kendi askerini hedef alabilir; askerin kendi tarafini degil. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        LivingEntity target = event.getTarget();
        if (target == null) {
            return;
        }
        Optional<UUID> side = retinue.sideOf(event.getEntity());
        if (side.isPresent() && side.equals(retinue.sideOf(target))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        boolean ours = tags.isRetinue(dead) || tags.isBandit(dead);
        if (ours) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            // NPC oyuncu varligidir; olum mesaji sohbete dusmesin.
            if (event instanceof PlayerDeathEvent playerDeath) {
                playerDeath.deathMessage(null);
            }
        }
        if (tags.isSoldier(dead)) {
            retinue.onSoldierDeath(dead);
        }
        Retinue.lastDamager(dead).filter(tags::isSoldier).ifPresent(soldier -> retinue.creditKill(soldier, dead));
    }

    /** Kendi askerine sag tik ordu menusunu acar; baskasinin askerine binilemez. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!tags.isRetinue(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (tags.owner(event.getRightClicked()).filter(player.getUniqueId()::equals).isPresent()) {
            openPartyMenu.accept(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        retinue.forget(event.getPlayer());
    }
}
