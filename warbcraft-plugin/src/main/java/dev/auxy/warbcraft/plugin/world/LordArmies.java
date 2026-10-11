package dev.auxy.warbcraft.plugin.world;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.field.FieldDeployment;
import dev.auxy.warbcraft.core.field.FieldFormation;
import dev.auxy.warbcraft.core.npc.LordRoute;
import dev.auxy.warbcraft.core.npc.NpcKing;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.troop.TroopType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.data.SettlementSite;
import dev.auxy.warbcraft.plugin.data.Skin;
import dev.auxy.warbcraft.plugin.field.Retinue;
import dev.auxy.warbcraft.plugin.field.SoldierKit;
import dev.auxy.warbcraft.plugin.map.WebMap;
import dev.auxy.warbcraft.plugin.npc.NpcBodies;
import dev.auxy.warbcraft.plugin.npc.NpcTags;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Haritada dolasan NPC lord ordulari. Her NPC lord (krallar haric; onlar baskentte oturur) kendi
 * kralliginin yerlesimleri arasinda gider gelir ({@link LordRoute}). Yakinda oyuncu yokken hareket
 * soyuttur; oyuncu yaklasinca lord ve ordusunun bir kismi skinli NPC olarak belirir, uzaklasinca
 * yine soyuta doner. Savastaki kralligin oyuncularina ve askerlerine, yakindaki haydutlara saldirir.
 * Olen eskort askeri lordun partisinden duser. Konumlar web haritada gorunur.
 */
public final class LordArmies implements Listener {

    private static final long TICK_PERIOD = 10L;
    private static final double MATERIALIZE_DISTANCE = 96;
    private static final long DEMATERIALIZE_AFTER_MILLIS = 20_000;
    private static final int ESCORT_CAP = 6;
    private static final double AGGRO_RADIUS = 20;
    private static final double LEASH = 40;
    private static final double ARRIVE_DISTANCE = 6;
    private static final long REPATH_MILLIS = 3_000;
    private static final long LORD_RESPAWN_MILLIS = 10 * 60_000;
    private static final int MAP_EVERY = 10;

    private final Plugin plugin;
    private final Game game;
    private final NpcBodies bodies;
    private final NpcTags tags;
    private final WebMap webMap;
    private final Map<String, Host> hosts = new LinkedHashMap<>();
    private final Random random = new Random();
    private int ticks;

    /** @param webMap squaremap yoksa {@code null} */
    public LordArmies(Plugin plugin, Game game, NpcBodies bodies, NpcTags tags, WebMap webMap) {
        this.plugin = plugin;
        this.game = game;
        this.bodies = bodies;
        this.tags = tags;
        this.webMap = webMap;
    }

    public void start() {
        for (Map.Entry<String, Party> entry : game.lordParties().entrySet()) {
            Optional<Clan> clan = game.realm().findClan(entry.getKey());
            if (clan.isEmpty() || isKing(clan.get().leaderId()) || clan.get().kingdomId() == null) {
                continue;
            }
            List<LordRoute.Stop> stops = stops(clan.get().kingdomId());
            if (stops.isEmpty()) {
                continue;
            }
            LordRoute route = new LordRoute(stops.get(random.nextInt(stops.size())));
            hosts.put(clan.get().id(), new Host(clan.get(), entry.getValue(), route));
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
        plugin.getLogger().info(hosts.size() + " NPC lord ordusu haritada dolaşıyor.");
    }

    public void shutdown() {
        hosts.values().forEach(Host::dematerialize);
        if (webMap != null) {
            hosts.keySet().forEach(id -> webMap.removePin(WebMap.Layer.LORDS, id));
        }
    }

    /** Yonetici icin: lordlar nerede, ne yapiyor. */
    public List<String> describe(Location around, double radius) {
        List<String> lines = new ArrayList<>();
        for (Host host : hosts.values()) {
            double dx = host.route.x() - around.getX();
            double dz = host.route.z() - around.getZ();
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            String where = host.route.resting() ? "konaklıyor" : "yolda → " + stopName(host.route.destination());
            lines.add(Msg.esc(host.lordName()) + " · ordu " + host.party.totalCount() + " · " + (int) Math.hypot(dx, dz)
                    + " blok · " + where + (host.lord != null ? " · görünür" : ""));
        }
        return lines;
    }

    // ---------------------------------------------------------------- dongu

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        World world = Bukkit.getWorlds().getFirst();
        List<Player> players = NpcTags.realPlayers();
        for (Host host : hosts.values()) {
            if (now < host.respawnAt) {
                continue;
            }
            boolean near = false;
            for (Player player : players) {
                double dx = player.getLocation().getX() - host.route.x();
                double dz = player.getLocation().getZ() - host.route.z();
                if (player.getWorld().equals(world) && dx * dx + dz * dz < MATERIALIZE_DISTANCE * MATERIALIZE_DISTANCE) {
                    near = true;
                    break;
                }
            }
            if (near) {
                host.lastSeen = now;
                if (host.lord == null) {
                    host.materialize(world);
                }
            } else if (host.lord != null && now - host.lastSeen > DEMATERIALIZE_AFTER_MILLIS) {
                host.dematerialize();
            }
            if (host.lord != null) {
                host.command(now);
            } else {
                host.route.advance(TICK_PERIOD / 20.0, stops(host.clan.kingdomId()), random);
            }
            if (webMap != null && ticks % MAP_EVERY == 0) {
                host.pin(world);
            }
        }
    }

    // ---------------------------------------------------------------- olaylar

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        Host host = hostOf(event.getEntity());
        if (host == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (event instanceof PlayerDeathEvent death) {
            death.deathMessage(null);
        }
        if (host.lord != null && event.getEntity().equals(host.lord.getEntity())) {
            // Lord yenildi: ordusu dagilir, bir sure sonra baskentinden yeniden yola cikar.
            Bukkit.getServer().broadcast(MiniMessage.miniMessage().deserialize(
                    "<gold>⚔ " + Msg.esc(host.lordName()) + " yenildi! Ordusu dağıldı."));
            Bukkit.getScheduler().runTask(plugin, host::defeated);
            return;
        }
        tags.troop(event.getEntity()).ifPresent(troopId -> {
            if (host.party.count(troopId) > 0) {
                boolean fatal = FieldDeployment.fallIsFatal(random.nextDouble());
                host.party.applyCasualties(troopId, fatal ? 1 : 0, fatal ? 0 : 1);
            }
        });
        host.escorts.removeIf(npc -> event.getEntity().equals(npc.getEntity()));
    }

    /** Lorda ya da eskortuna vurulursa butun ordu saldirgana doner. */
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Host host = hostOf(event.getEntity());
        if (host == null || !(Retinue.resolve(event.getDamager()) instanceof LivingEntity attacker)) {
            return;
        }
        if (hostOf(attacker) == host) {
            event.setCancelled(true);
            return;
        }
        host.provoker = attacker;
        host.attack(attacker);
    }

    // ---------------------------------------------------------------- yardimcilar

    private Host hostOf(Entity entity) {
        for (Host host : hosts.values()) {
            if (host.lord != null && entity.equals(host.lord.getEntity())) {
                return host;
            }
            for (NPC escort : host.escorts) {
                if (entity.equals(escort.getEntity())) {
                    return host;
                }
            }
        }
        return null;
    }

    private boolean isKing(String leaderId) {
        for (NpcKing king : game.data().kings().values()) {
            if (king.id().equals(leaderId)) {
                return true;
            }
        }
        return false;
    }

    private List<LordRoute.Stop> stops(String kingdomId) {
        List<LordRoute.Stop> stops = new ArrayList<>();
        for (Settlement settlement : game.realm().settlementsOfKingdom(kingdomId)) {
            SettlementSite site = game.data().sites().get(settlement.id());
            if (site != null) {
                stops.add(new LordRoute.Stop(settlement.id(), site.x() + 0.5, site.z() + 0.5));
            }
        }
        return stops;
    }

    private String stopName(LordRoute.Stop stop) {
        return game.realm().findSettlement(stop.id()).map(Settlement::name).orElse(stop.id());
    }

    /** "lord_merov" -> "Lord Merov", "bey_alpagu" -> "Bey Alpagu". */
    static String titleCase(String id) {
        StringBuilder name = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            name.append(name.isEmpty() ? "" : " ").append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return name.toString();
    }

    private static LivingEntity living(NPC npc) {
        return npc.isSpawned() && npc.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static Location surface(World world, double x, double z) {
        return world.getHighestBlockAt((int) Math.floor(x), (int) Math.floor(z), HeightMap.MOTION_BLOCKING_NO_LEAVES)
                .getLocation().add(0.5, 1, 0.5);
    }

    /** Bir lord ve ordusu. */
    private final class Host {
        private final Clan clan;
        private final Party party;
        private final LordRoute route;
        private final List<NPC> escorts = new ArrayList<>();
        private NPC lord;
        private long lastSeen;
        private long nextRepath;
        private long respawnAt;
        private LivingEntity target;
        private LivingEntity provoker;

        private Host(Clan clan, Party party, LordRoute route) {
            this.clan = clan;
            this.party = party;
            this.route = route;
        }

        private String lordName() {
            return titleCase(clan.leaderId());
        }

        private Optional<Kingdom> kingdom() {
            return Optional.ofNullable(clan.kingdomId()).flatMap(game.realm()::findKingdom);
        }

        private void materialize(World world) {
            Location at = surface(world, route.x(), route.z());
            String culture = kingdom().map(Kingdom::culture).orElse("valdor");
            Skin skin = game.data().cultureSkins().getOrDefault(culture, Skin.NONE);
            TroopType best = party.roster().keySet().stream().map(game.tree()::get)
                    .max((a, b) -> Integer.compare(a.tier(), b.tier())).orElse(null);
            SoldierKit.Outfit outfit = best == null ? new SoldierKit.Outfit(null, null, null, null, null, null)
                    : SoldierKit.outfit(best);
            Optional<NPC> spawned = bodies.spawnHuman("<yellow><bold>" + Msg.esc(lordName()) + "</bold> <gray>("
                    + Msg.esc(clan.name()) + ")", true, at, skin, outfit, 40);
            if (spawned.isEmpty()) {
                return;
            }
            lord = spawned.get();
            NpcBodies.roamerDefaults(lord);
            NpcBodies.melee(lord, () -> living(lord), 7, 14, 2.8);
            Map<String, Integer> escort = FieldDeployment.select(party, game.tree(), ESCORT_CAP);
            int index = 0;
            for (Map.Entry<String, Integer> entry : escort.entrySet()) {
                TroopType troop = game.tree().get(entry.getKey());
                for (int i = 0; i < entry.getValue(); i++) {
                    FieldFormation.Slot slot = FieldFormation.COLUMN.slot(index++);
                    Location place = Retinue.ground(at.clone().add(slot.side(), 0, slot.back())).orElse(at);
                    spawnEscort(troop, place, culture);
                }
            }
            nextRepath = 0;
        }

        private void spawnEscort(TroopType troop, Location at, String culture) {
            FieldDeployment.Stats stats = FieldDeployment.stats(troop);
            Skin skin = game.data().cultureSkins().getOrDefault(culture, Skin.NONE);
            bodies.spawnHuman(Retinue.tierColor(troop.tier()) + Msg.esc(troop.name()), false, at, skin,
                    SoldierKit.outfit(troop), stats.maxHealth()).ifPresent(npc -> {
                        tags.tagGuard(npc.getEntity(), "lord:" + clan.id(), troop.id());
                        NpcBodies.roamerDefaults(npc);
                        if (troop.category().isRanged()) {
                            NpcBodies.ranged(npc, () -> living(npc), stats.arrowDamage(), stats.shootCooldownTicks());
                        } else {
                            NpcBodies.melee(npc, () -> living(npc), stats.attackDamage(), Math.max(10, 22 - troop.tier() * 2), 2.8);
                        }
                        escorts.add(npc);
                    });
        }

        private void dematerialize() {
            if (lord != null && lord.isSpawned()) {
                Location at = lord.getEntity().getLocation();
                route.moveTo(at.getX(), at.getZ());
            }
            NpcBodies.destroy(lord);
            lord = null;
            escorts.forEach(NpcBodies::destroy);
            escorts.clear();
            target = null;
            provoker = null;
        }

        private void defeated() {
            dematerialize();
            respawnAt = System.currentTimeMillis() + LORD_RESPAWN_MILLIS;
            List<LordRoute.Stop> stops = stops(clan.kingdomId());
            if (!stops.isEmpty()) {
                LordRoute.Stop home = stops.getFirst();
                route.moveTo(home.x(), home.z());
            }
        }

        private void command(long now) {
            if (!lord.isSpawned()) {
                dematerialize();
                return;
            }
            escorts.removeIf(npc -> {
                if (!npc.isSpawned()) {
                    NpcBodies.destroy(npc);
                    return true;
                }
                return false;
            });
            Location at = lord.getEntity().getLocation();
            route.moveTo(at.getX(), at.getZ());
            if (target != null && !keep(target, at)) {
                target = null;
                provoker = null;
                lord.getNavigator().cancelNavigation();
                escorts.forEach(npc -> npc.getNavigator().cancelNavigation());
                nextRepath = 0;
            }
            if (target == null) {
                target = findHostile(at);
            }
            if (target != null) {
                attack(target);
                return;
            }
            // Yolculuk: lord duraga yurur, eskort arkasinda kol duzeninde.
            if (route.resting()) {
                route.advance(TICK_PERIOD / 20.0, stops(clan.kingdomId()), random);
            } else if (route.remaining() < ARRIVE_DISTANCE) {
                route.arrive(random);
            } else if (now >= nextRepath) {
                nextRepath = now + REPATH_MILLIS;
                LordRoute.Stop stop = route.destination();
                // Uzak duraga tek seferde yol bulunamaz; 32 blokluk adimlarla gidilir.
                double dx = stop.x() - at.getX();
                double dz = stop.z() - at.getZ();
                double distance = Math.hypot(dx, dz);
                double step = Math.min(32, distance);
                Location next = surface(at.getWorld(), at.getX() + dx / distance * step, at.getZ() + dz / distance * step);
                lord.getNavigator().setTarget(next);
            }
            int index = 0;
            float yaw = at.getYaw();
            for (NPC escort : escorts) {
                FieldFormation.Slot slot = FieldFormation.COLUMN.slot(index++);
                double rad = Math.toRadians(yaw);
                Location point = at.clone().add(Math.sin(rad) * slot.back() - Math.cos(rad) * slot.side(), 0,
                        -Math.cos(rad) * slot.back() - Math.sin(rad) * slot.side());
                Location ground = Retinue.ground(point).orElse(point);
                Entity body = escort.getEntity();
                if (body == null) {
                    continue;
                }
                if (body.getLocation().distanceSquared(ground) > 4) {
                    Navigator navigator = escort.getNavigator();
                    if (!navigator.isNavigating() || navigator.getEntityTarget() != null
                            || navigator.getTargetAsLocation() == null
                            || navigator.getTargetAsLocation().distanceSquared(ground) > 9) {
                        navigator.setTarget(ground);
                    }
                }
            }
        }

        private void attack(LivingEntity enemy) {
            target = enemy;
            List<NPC> all = new ArrayList<>(escorts);
            all.add(lord);
            for (NPC npc : all) {
                if (npc == null || !npc.isSpawned()) {
                    continue;
                }
                Navigator navigator = npc.getNavigator();
                if (navigator.getEntityTarget() == null || navigator.getEntityTarget().getTarget() != enemy) {
                    navigator.setTarget(enemy, true);
                }
            }
        }

        private boolean keep(LivingEntity enemy, Location at) {
            return enemy.isValid() && !enemy.isDead() && enemy.getWorld().equals(at.getWorld())
                    && enemy.getLocation().distanceSquared(at) < LEASH * LEASH
                    && (enemy.equals(provoker) || hostile(enemy));
        }

        private LivingEntity findHostile(Location at) {
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Entity entity : at.getWorld().getNearbyEntities(at, AGGRO_RADIUS, 10, AGGRO_RADIUS)) {
                if (entity instanceof LivingEntity living && !living.isDead() && hostile(living)) {
                    double distance = living.getLocation().distanceSquared(at);
                    if (distance < bestDistance) {
                        best = living;
                        bestDistance = distance;
                    }
                }
            }
            return best;
        }

        /** Haydutlar ve savastaki kralligin oyunculari ile askerleri. */
        private boolean hostile(LivingEntity entity) {
            if (tags.isBandit(entity)) {
                return true;
            }
            Optional<Kingdom> mine = kingdom();
            if (mine.isEmpty()) {
                return false;
            }
            UUID playerId = null;
            if (entity instanceof Player player && !NpcTags.isAnyNpc(player)) {
                if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
                    return false;
                }
                playerId = player.getUniqueId();
            } else if (tags.isRetinue(entity)) {
                playerId = tags.owner(entity).orElse(null);
            }
            if (playerId == null) {
                return false;
            }
            Optional<String> theirs = game.clanOf(playerId).map(Clan::kingdomId);
            return theirs.isPresent() && game.realm().atWar(mine.get().id(), theirs.get());
        }

        private void pin(World world) {
            String where = route.resting() ? "Konaklıyor" : "Yolda: " + WebMap.esc(stopName(route.destination()));
            String html = "<b>" + WebMap.esc(lordName()) + "</b><br>" + WebMap.esc(clan.name())
                    + kingdom().map(k -> "<br>" + WebMap.esc(k.name())).orElse("")
                    + "<br>Ordu: " + party.totalCount() + "<br>" + where;
            Color color = kingdom().map(WebMap::awt).orElse(Color.GRAY);
            Location at = new Location(world, route.x(), 64, route.z());
            if (System.currentTimeMillis() < respawnAt) {
                webMap.removePin(WebMap.Layer.LORDS, clan.id());
            } else {
                webMap.setPin(WebMap.Layer.LORDS, clan.id(), at, 4, color, html);
            }
        }
    }
}
