package dev.auxy.warbcraft.plugin.field;

import dev.auxy.warbcraft.core.field.FieldDeployment;
import dev.auxy.warbcraft.core.field.FieldFormation;
import dev.auxy.warbcraft.core.field.FieldOrder;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.troop.TroopType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.data.Skin;
import dev.auxy.warbcraft.plugin.npc.NpcBodies;
import dev.auxy.warbcraft.plugin.npc.NpcTags;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Oyuncunun partisinden sahaya cikan askerler. Her oyuncunun arkasinda en fazla
 * {@code oyun.sahada-asker} asker yurur (secim cekirdekte: {@link FieldDeployment#select}).
 * Askerler skinli Citizens NPC'leridir ve kalici degildir: oyuncu cikinca kaybolur, parti degisince
 * (toplama, terfi, terhis, yaralanma) sahadaki dizilis kendiliginden guncellenir.
 *
 * <p>Yurume ve hedef kovalama Citizens'in yol bulucusuyla olur; hedef secimi ve formasyon buradadir.
 * Atli birliklerde binici bir at NPC'sine biner, yolu at yurur, vurusu binici yapar.
 */
public final class Retinue {

    private static final long TICK_PERIOD = 5L;
    private static final int SYNC_EVERY = 4;
    private static final int SPAWN_PER_SYNC = 4;
    /** Bundan uzaktaki asker kaybolmus sayilir ve yanina tasinir (orn. oyuncu isinlandi). */
    private static final double REGROUP_DISTANCE = 64;
    /** Hedef nokta bu kadar kaymadikca yeni yol hesaplanmaz; surekli yeniden yol dur-kalk yaptiriyordu. */
    private static final double REPATH_DISTANCE = 3;
    private static final double SLOT_TOLERANCE = 1.5;
    private static final double TARGET_LEASH = 12;
    private static final long THREAT_MILLIS = 10_000;
    private static final double STUCK_DISTANCE = 10;
    private static final int[] GROUND_SEARCH = {0, -1, 1, -2, 2, -3, 3, -4, -5, -6, -7, -8, -10, -12};
    private static final long STUCK_MILLIS = 6_000;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final Game game;
    private final NpcBodies bodies;
    private final NpcTags tags;
    private final Map<UUID, Band> bands = new HashMap<>();
    private final Random random = new Random();
    private int ticks;

    public Retinue(Plugin plugin, Game game, NpcBodies bodies, NpcTags tags) {
        this.plugin = plugin;
        this.game = game;
        this.bodies = bodies;
        this.tags = tags;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void shutdown() {
        bands.values().forEach(this::dismissAll);
        bands.clear();
    }

    // ---------------------------------------------------------------- emirler (komut ve menu)

    public FieldOrder order(Player player) {
        return band(player).order;
    }

    public FieldFormation formation(Player player) {
        return band(player).formation;
    }

    public boolean deployed(Player player) {
        return band(player).deployed;
    }

    public int fieldCount(Player player) {
        return band(player).soldiers.size();
    }

    /** Sahadaki askerlerin oyuncuya ortalama uzakligi (yonetici durumu icin); asker yoksa -1. */
    public double averageDistance(Player player) {
        double total = 0;
        int count = 0;
        for (Soldier soldier : band(player).soldiers.values()) {
            Entity body = soldier.walker().getEntity();
            if (body != null && body.getWorld().equals(player.getWorld())) {
                total += body.getLocation().distance(player.getLocation());
                count++;
            }
        }
        return count == 0 ? -1 : total / count;
    }

    public int fieldCount(Player player, String troopId) {
        int count = 0;
        for (Soldier soldier : band(player).soldiers.values()) {
            if (soldier.troopId.equals(troopId)) {
                count++;
            }
        }
        return count;
    }

    public void setOrder(Player player, FieldOrder order) {
        Band band = band(player);
        band.order = order;
        if (order == FieldOrder.HOLD) {
            band.holdAt = player.getLocation();
            band.holdHeading = band.heading;
        }
        if (order != FieldOrder.CHARGE) {
            // Hucumdan donen asker eldeki kovalamayi birakip yerine gelsin.
            for (Soldier soldier : band.soldiers.values()) {
                if (!soldier.walker().isSpawned()) {
                    continue;
                }
                soldier.walker().getNavigator().cancelNavigation();
                soldier.marchTarget = null;
            }
        }
    }

    public void setFormation(Player player, FieldFormation formation) {
        band(player).formation = formation;
    }

    /** @return yeni durum: askerler sahada mi */
    public boolean toggleDeployed(Player player) {
        Band band = band(player);
        band.deployed = !band.deployed;
        if (!band.deployed) {
            dismissAll(band);
        }
        return band.deployed;
    }

    /** Oyuncu cikinca askerleri kaldirir. */
    public void forget(Player player) {
        Band band = bands.remove(player.getUniqueId());
        if (band != null) {
            dismissAll(band);
        }
    }

    // ---------------------------------------------------------------- kimlik

    public NpcTags tags() {
        return tags;
    }

    /** Varligin tarafi: oyuncunun kendisi ya da askerinin/atinin sahibi. */
    public Optional<UUID> sideOf(Entity entity) {
        if (entity instanceof Player player && !NpcTags.isAnyNpc(player)) {
            return Optional.of(player.getUniqueId());
        }
        return tags.owner(entity);
    }

    /** Vuran ya da atan varligin komutani: oyuncunun kendisi ya da askerinin sahibi (cevrimici ise). */
    public Optional<Player> commander(Entity damager) {
        return sideOf(resolve(damager)).map(Bukkit::getPlayer);
    }

    /** Mermiyi atana cevirir. */
    public static Entity resolve(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Entity entity ? entity : damager;
        }
        return damager;
    }

    /** Son vurusu yapan varlik (mermi ise atan). */
    public static Optional<Entity> lastDamager(LivingEntity victim) {
        if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent event) {
            return Optional.of(resolve(event.getDamager()));
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- savas olaylari

    /** Oyuncuya saldiran ya da oyuncunun saldirdigi yaratik, askerlerin oncelikli hedefi olur. */
    public void recordThreat(Player owner, LivingEntity threat) {
        if (sideOf(threat).filter(owner.getUniqueId()::equals).isPresent()) {
            return;
        }
        Band band = bands.get(owner.getUniqueId());
        if (band != null) {
            band.threat = threat;
            band.threatUntil = System.currentTimeMillis() + THREAT_MILLIS;
        }
    }

    /** Vurulan asker saldirgana doner. */
    public void onSoldierAttacked(Entity victim, LivingEntity attacker) {
        Optional<UUID> owner = tags.owner(victim);
        Band band = owner.map(bands::get).orElse(null);
        if (band == null || sideOf(attacker).equals(owner)) {
            return;
        }
        for (Soldier soldier : band.soldiers.values()) {
            if (victim.equals(soldier.npc.getEntity()) || (soldier.mount != null && victim.equals(soldier.mount.getEntity()))) {
                if (!soldier.walker().isSpawned()) {
                    return;
                }
                Navigator navigator = soldier.walker().getNavigator();
                if (navigator.getEntityTarget() == null) {
                    navigator.setTarget(attacker, true);
                }
                return;
            }
        }
    }

    /** Oyuncunun vurmasiyla askerlerin de saldirmasi gereken yaratik mi (koylu, inek vb. degil). */
    public boolean isFoe(LivingEntity entity, Player owner) {
        if (tags.isBandit(entity)) {
            return true;
        }
        if (entity instanceof Player || tags.isRetinue(entity) || NpcTags.isAnyNpc(entity)) {
            return false;
        }
        return (entity instanceof Enemy && !(entity instanceof Creeper))
                || (entity instanceof Mob mob && owner.equals(mob.getTarget()));
    }

    /** Sahadaki asker dustu: partide olur ya da yaralanir, yerine yedekten biri gelir. */
    public void onSoldierDeath(LivingEntity dead) {
        Optional<String> troopId = tags.troop(dead);
        Optional<UUID> ownerId = tags.owner(dead);
        if (troopId.isEmpty() || ownerId.isEmpty()) {
            return;
        }
        Band band = bands.get(ownerId.get());
        if (band != null) {
            Iterator<Soldier> it = band.soldiers.values().iterator();
            while (it.hasNext()) {
                Soldier soldier = it.next();
                if (dead.equals(soldier.npc.getEntity())) {
                    it.remove();
                    // Citizens olumu kendi isler; kaydi bir sonraki tikte sileriz.
                    Bukkit.getScheduler().runTask(plugin, soldier::destroy);
                    break;
                }
            }
        }
        Player owner = Bukkit.getPlayer(ownerId.get());
        if (owner == null) {
            return;
        }
        Party party = game.profile(owner).party();
        if (party.count(troopId.get()) == 0) {
            return;
        }
        boolean fatal = FieldDeployment.fallIsFatal(random.nextDouble());
        party.applyCasualties(troopId.get(), fatal ? 1 : 0, fatal ? 0 : 1);
        String name = Msg.esc(game.tree().find(troopId.get()).map(TroopType::name).orElse(troopId.get()));
        owner.sendActionBar(MM.deserialize(fatal
                ? "<red>✝ " + name + " öldü."
                : "<gold>✚ " + name + " yaralandı; iyileşince saflara döner."));
    }

    /** Askerin oldurdugu yaratigin deneyimi yigina yazilir (terfi icin). */
    public void creditKill(Entity soldier, LivingEntity victim) {
        Optional<String> troopId = tags.troop(soldier);
        Optional<Player> owner = tags.owner(soldier).map(Bukkit::getPlayer);
        if (troopId.isEmpty() || owner.isEmpty() || tags.isBandit(soldier)) {
            return;
        }
        AttributeInstance health = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        game.profile(owner.get()).party().addXp(troopId.get(),
                FieldDeployment.killXp(health == null ? 20 : health.getValue()));
    }

    // ---------------------------------------------------------------- dongu

    private void tick() {
        ticks++;
        boolean sync = ticks % SYNC_EVERY == 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Band band = band(player);
            if (!band.deployed || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                dismissAll(band);
                continue;
            }
            prune(band);
            updateHeading(player, band);
            if (sync) {
                sync(player, band);
            }
            if (!band.soldiers.isEmpty()) {
                command(player, band);
            }
        }
    }

    /** Sahadakileri partiyle esitler: fazlalar cekilir, eksikler yedekten cikar. */
    private void sync(Player player, Band band) {
        Map<String, Integer> wanted = FieldDeployment.select(game.profile(player).party(), game.tree(),
                game.data().settings().fieldCap());
        Map<String, Integer> have = new HashMap<>();
        Iterator<Soldier> it = band.soldiers.values().iterator();
        while (it.hasNext()) {
            Soldier soldier = it.next();
            int count = have.getOrDefault(soldier.troopId, 0) + 1;
            if (count > wanted.getOrDefault(soldier.troopId, 0)) {
                soldier.destroy();
                it.remove();
            } else {
                have.put(soldier.troopId, count);
            }
        }
        int budget = SPAWN_PER_SYNC;
        for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
            int missing = entry.getValue() - have.getOrDefault(entry.getKey(), 0);
            for (int i = 0; i < missing && budget > 0; i++, budget--) {
                spawn(player, band, game.tree().get(entry.getKey()));
            }
        }
    }

    private void command(Player player, Band band) {
        boolean holding = band.order == FieldOrder.HOLD && band.holdAt != null;
        Location anchor = holding ? band.holdAt : player.getLocation();
        float heading = holding ? band.holdHeading : band.heading;
        List<LivingEntity> foes = foes(player, band, anchor);
        LivingEntity threat = band.threat != null && System.currentTimeMillis() < band.threatUntil
                && keepTarget(band.threat, player, band, anchor) ? band.threat : null;
        int index = 0;
        for (Soldier soldier : band.soldiers.values()) {
            NPC walker = soldier.walker();
            Entity body = walker.getEntity();
            if (body == null || !walker.isSpawned()) {
                continue;
            }
            FieldFormation.Slot slot = band.formation.slot(index++);
            Location slotPoint = slotLocation(anchor, heading, slot);
            Location point = ground(slotPoint).orElse(slotPoint);
            if (!body.getWorld().equals(anchor.getWorld())
                    || body.getLocation().distanceSquared(anchor) > REGROUP_DISTANCE * REGROUP_DISTANCE) {
                relocate(soldier, walker, point, anchor);
                continue;
            }
            Navigator navigator = walker.getNavigator();
            LivingEntity target = navigator.getEntityTarget() != null
                    && navigator.getEntityTarget().getTarget() instanceof LivingEntity living ? living : null;
            if (target != null && !keepTarget(target, player, band, anchor)) {
                navigator.cancelNavigation();
                target = null;
            }
            if (target == null) {
                LivingEntity chosen = threat != null ? threat : nearest(foes, body.getLocation());
                if (chosen != null) {
                    navigator.setTarget(chosen, true);
                    soldier.marchTarget = null;
                    continue;
                }
            } else {
                continue;
            }
            march(soldier, walker, body, point, heading, anchor);
        }
    }

    private List<LivingEntity> foes(Player player, Band band, Location anchor) {
        int range = band.order.engageRange();
        List<LivingEntity> foes = new ArrayList<>();
        for (Entity entity : anchor.getWorld().getNearbyEntities(anchor, range, range / 2.0 + 2, range)) {
            if (entity instanceof LivingEntity living && !living.isDead() && isFoe(living, player)) {
                foes.add(living);
            }
        }
        return foes;
    }

    private boolean keepTarget(LivingEntity target, Player player, Band band, Location anchor) {
        return target.isValid() && !target.isDead()
                && target.getWorld().equals(anchor.getWorld())
                && target.getLocation().distanceSquared(anchor) <= square(band.order.engageRange() + TARGET_LEASH)
                && sideOf(target).filter(player.getUniqueId()::equals).isEmpty()
                && !(target instanceof Player p && !NpcTags.isAnyNpc(p)
                        && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR));
    }

    private static LivingEntity nearest(List<LivingEntity> foes, Location from) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity foe : foes) {
            double distance = foe.getLocation().distanceSquared(from);
            if (distance < bestDistance) {
                best = foe;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void march(Soldier soldier, NPC walker, Entity body, Location point, float heading, Location anchor) {
        Location at = body.getLocation();
        double dx = point.getX() - at.getX();
        double dz = point.getZ() - at.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        Navigator navigator = walker.getNavigator();
        if (stuck(soldier, at, distance)) {
            // Yol bulunamadi (magara, dar gecit): yerinde saymak yerine yerine gelsin.
            relocate(soldier, walker, point, anchor);
            return;
        }
        if (distance < SLOT_TOLERANCE) {
            if (navigator.isNavigating()) {
                navigator.cancelNavigation();
            }
            soldier.marchTarget = null;
            if (!soldier.facing) {
                walker.faceLocation(point.clone().add(-Math.sin(Math.toRadians(heading)) * 8, 0,
                        Math.cos(Math.toRadians(heading)) * 8));
                soldier.facing = true;
            }
            return;
        }
        soldier.facing = false;
        if (soldier.marchTarget != null && navigator.isNavigating()
                && soldier.marchTarget.getWorld().equals(point.getWorld())
                && soldier.marchTarget.distanceSquared(point) < REPATH_DISTANCE * REPATH_DISTANCE) {
            return;
        }
        navigator.setTarget(point);
        navigator.getLocalParameters().distanceMargin(0.8).speedModifier(distance > 8 ? 1.45f : 1.1f);
        soldier.marchTarget = point;
    }

    /** Isinlanma yalnizca kati zemine; uygun zemin yoksa asker yurumeye devam eder. */
    private static void relocate(Soldier soldier, NPC walker, Location point, Location anchor) {
        Optional<Location> ground = ground(point).or(() -> ground(anchor));
        if (ground.isEmpty()) {
            return;
        }
        walker.getNavigator().cancelNavigation();
        walker.teleport(ground.get(), PlayerTeleportEvent.TeleportCause.PLUGIN);
        soldier.marchTarget = null;
        soldier.progressAt = null;
    }

    /** Yerinden uzak olup {@link #STUCK_MILLIS} boyunca bir bloktan fazla ilerleyemeyen asker takilmistir. */
    private static boolean stuck(Soldier soldier, Location at, double distanceToSlot) {
        long now = System.currentTimeMillis();
        if (distanceToSlot < STUCK_DISTANCE || soldier.progressAt == null
                || !soldier.progressAt.getWorld().equals(at.getWorld())
                || soldier.progressAt.distanceSquared(at) > 1.0) {
            soldier.progressAt = at;
            soldier.progressSince = now;
            return false;
        }
        if (now - soldier.progressSince > STUCK_MILLIS) {
            soldier.progressAt = null;
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- NPC yonetimi

    private void spawn(Player player, Band band, TroopType troop) {
        Location at = safe(slotLocation(player.getLocation(), band.heading, band.formation.slot(band.soldiers.size())),
                player.getLocation());
        FieldDeployment.Stats stats = FieldDeployment.stats(troop);
        Skin skin = game.data().cultureSkins().getOrDefault(troop.culture(), Skin.NONE);
        Optional<NPC> spawned = bodies.spawnHuman(tierColor(troop.tier()) + Msg.esc(troop.name()), false, at, skin,
                SoldierKit.outfit(troop), stats.maxHealth());
        if (spawned.isEmpty()) {
            return;
        }
        NPC npc = spawned.get();
        tags.tagSoldier(npc.getEntity(), player.getUniqueId(), troop.id());
        Soldier soldier = new Soldier(npc, troop.id());
        if (troop.category().isMounted()) {
            bodies.spawnHorse(at, SoldierKit.horseArmor(troop, SoldierKit.color(troop.culture())), SoldierKit.horseColor(troop))
                    .ifPresent(horse -> {
                        tags.tagMount(horse.getEntity(), player.getUniqueId());
                        bodies.mount(npc, horse);
                        soldier.mount = horse;
                    });
        }
        NPC walker = soldier.walker();
        NpcBodies.followerDefaults(walker);
        if (troop.category().isRanged()) {
            NpcBodies.ranged(walker, () -> living(npc), stats.arrowDamage(), stats.shootCooldownTicks());
        } else {
            NpcBodies.melee(walker, () -> living(npc), stats.attackDamage(), Math.max(10, 22 - troop.tier() * 2),
                    soldier.mount != null ? 3.6 : 2.8);
        }
        band.soldiers.put(npc.getUniqueId(), soldier);
    }

    private static LivingEntity living(NPC npc) {
        return npc.isSpawned() && npc.getEntity() instanceof LivingEntity living ? living : null;
    }

    private void prune(Band band) {
        Iterator<Soldier> it = band.soldiers.values().iterator();
        while (it.hasNext()) {
            Soldier soldier = it.next();
            if (!soldier.npc.isSpawned()) {
                soldier.destroy();
                it.remove();
            }
        }
    }

    private void dismissAll(Band band) {
        band.soldiers.values().forEach(Soldier::destroy);
        band.soldiers.clear();
    }

    private Band band(Player player) {
        return bands.computeIfAbsent(player.getUniqueId(), id -> new Band());
    }

    /** Lider yurudukce formasyon yuruyus yonune doner; yerinde etrafa bakmak diziyi bozmaz. */
    private static void updateHeading(Player player, Band band) {
        Location now = player.getLocation();
        if (band.lastPosition == null || !band.lastPosition.getWorld().equals(now.getWorld())) {
            band.lastPosition = now;
            band.heading = now.getYaw();
            return;
        }
        double dx = now.getX() - band.lastPosition.getX();
        double dz = now.getZ() - band.lastPosition.getZ();
        if (dx * dx + dz * dz > 1.0) {
            band.heading = (float) Math.toDegrees(Math.atan2(-dx, dz));
            band.lastPosition = now;
        }
    }

    /** Liderin {@code heading} yonune gore formasyondaki yer. */
    private static Location slotLocation(Location anchor, float heading, FieldFormation.Slot slot) {
        double rad = Math.toRadians(heading);
        double forwardX = -Math.sin(rad);
        double forwardZ = Math.cos(rad);
        double rightX = -forwardZ;
        double rightZ = forwardX;
        return new Location(anchor.getWorld(),
                anchor.getX() - forwardX * slot.back() + rightX * slot.side(),
                anchor.getY(),
                anchor.getZ() - forwardZ * slot.back() + rightZ * slot.side(),
                heading, 0);
    }

    /** Ayakta durulabilecek en yakin yukseklik; bulunamazsa yedek konum. */
    static Location safe(Location wanted, Location fallback) {
        return ground(wanted).orElse(fallback);
    }

    /**
     * Ayni x/z'de, istenen yukseklige en yakin kati zemin (su ve hava degil). Oyuncu tepede ya da
     * havadayken asker onun yuksekligine degil, ayaginin altindaki zemine hedeflenir.
     */
    static Optional<Location> ground(Location wanted) {
        for (int dy : GROUND_SEARCH) {
            Block feet = wanted.getBlock().getRelative(0, dy, 0);
            if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(BlockFace.UP).isPassable()
                    && feet.getRelative(BlockFace.DOWN).getType().isSolid()) {
                Location at = feet.getLocation().add(0.5, 0, 0.5);
                at.setYaw(wanted.getYaw());
                return Optional.of(at);
            }
        }
        return Optional.empty();
    }

    private static double square(double value) {
        return value * value;
    }

    /** Kademe rengi (MiniMessage acilis etiketi); ad etiketi ve menuler. */
    public static String tierColor(int tier) {
        return switch (tier) {
            case 1, 2 -> "<gray>";
            case 3 -> "<white>";
            case 4 -> "<green>";
            case 5 -> "<aqua>";
            default -> "<gold>";
        };
    }

    private static final class Band {
        private final Map<UUID, Soldier> soldiers = new LinkedHashMap<>();
        private FieldOrder order = FieldOrder.FOLLOW;
        private FieldFormation formation = FieldFormation.LINE;
        private boolean deployed = true;
        private Location holdAt;
        private float holdHeading;
        private float heading;
        private Location lastPosition;
        private LivingEntity threat;
        private long threatUntil;
    }

    private static final class Soldier {
        private final NPC npc;
        private final String troopId;
        private NPC mount;
        private Location marchTarget;
        private boolean facing;
        private Location progressAt;
        private long progressSince;

        private Soldier(NPC npc, String troopId) {
            this.npc = npc;
            this.troopId = troopId;
        }

        /** Yolu yuruyen NPC: atliysa at, degilse askerin kendisi. */
        private NPC walker() {
            return mount != null && mount.isSpawned() ? mount : npc;
        }

        private void destroy() {
            NpcBodies.destroy(npc);
            NpcBodies.destroy(mount);
        }
    }
}
