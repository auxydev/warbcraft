package dev.auxy.warbcraft.plugin.listener;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.quest.QuestType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.field.Retinue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.Optional;

public final class GameListener implements Listener {

    private final Game game;
    private final Bandits bandits;
    private final Retinue retinue;

    public GameListener(Game game, Bandits bandits, Retinue retinue) {
        this.game = game;
        this.bandits = bandits;
        this.retinue = retinue;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        game.profile(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        game.players().unload(event.getPlayer().getUniqueId());
    }

    /** Haydudu oyuncu ya da onun askeri oldurduyse odul oyuncuya yazilir. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBanditDeath(EntityDeathEvent event) {
        if (!bandits.isBandit(event.getEntity())) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            killer = Retinue.lastDamager(event.getEntity()).flatMap(retinue::commander).orElse(null);
        }
        if (killer == null) {
            return;
        }
        int bounty = game.data().settings().banditBounty();
        game.purse(killer).deposit(bounty);
        game.addRenown(killer.getUniqueId(), 1);
        Msg.info(killer, "Haydut öldürüldü: <yellow>+" + bounty + " dinar</yellow>, +1 ün");
        game.progressQuest(killer, QuestType.HUNT_BANDITS, "", 1);
    }

    /** Savastaki bir kralligin savascisini yenmek sefer hizmeti gorevini ilerletir. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        Optional<Clan> victimClan = game.clanOf(victim.getUniqueId());
        Optional<Clan> killerClan = game.clanOf(killer.getUniqueId());
        if (victimClan.isEmpty() || victimClan.get().kingdomId() == null || victimClan.equals(killerClan)) {
            return;
        }
        String victimKingdom = victimClan.get().kingdomId();
        if (killerClan.map(Clan::kingdomId).filter(k -> Objects.equals(k, victimKingdom)).isPresent()) {
            return;
        }
        game.progressQuest(killer, QuestType.WAR_SERVICE, victimKingdom, 1);
    }
}
