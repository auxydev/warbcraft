package dev.auxy.warbcraft.plugin.field;

import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Sahadaki askerlerin dunya olaylari: dost atesi, olum, hedef secimi, tiklama. */
public final class RetinueListener implements Listener {

    private final Retinue retinue;
    private final Consumer<Player> openPartyMenu;

    public RetinueListener(Retinue retinue, Consumer<Player> openPartyMenu) {
        this.retinue = retinue;
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
        if (victim instanceof Player owner) {
            retinue.recordThreat(owner, living);
        } else if (attacker instanceof Player owner && victim instanceof LivingEntity target
                && (retinue.isFoe(target, owner) || retinue.isTagged(target))) {
            retinue.recordThreat(owner, target);
        }
        if (retinue.isSoldier(victim) && victim instanceof Mob soldier && soldier.getTarget() == null) {
            soldier.setTarget(living);
        }
    }

    /** Asker ve atlari yere carpma ya da duvara sikisma ile olmez (isinlanma ve binme kazalari). */
    @EventHandler(ignoreCancelled = true)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (retinue.isTagged(event.getEntity()) && (event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        LivingEntity target = event.getTarget();
        if (target == null) {
            return;
        }
        Optional<UUID> side = retinue.sideOf(event.getEntity());
        if (side.isPresent() && side.equals(retinue.sideOf(target))) {
            event.setCancelled(true);
            return;
        }
        // Koy golemleri askerleri canavar sanmasin.
        if (event.getEntity() instanceof IronGolem && retinue.isTagged(target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (retinue.isTagged(dead)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            if (retinue.isSoldier(dead)) {
                retinue.onSoldierDeath(dead);
            }
        }
        Retinue.lastDamager(dead).filter(retinue::isSoldier).ifPresent(soldier -> retinue.creditKill(soldier, dead));
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (retinue.isTagged(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (retinue.isTagged(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Kendi askerine sag tik ordu menusunu acar; baskasinin askerine binilemez, tasma takilamaz. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!retinue.isTagged(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (retinue.sideOf(event.getRightClicked()).filter(player.getUniqueId()::equals).isPresent()) {
            openPartyMenu.accept(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        retinue.forget(event.getPlayer());
    }
}
