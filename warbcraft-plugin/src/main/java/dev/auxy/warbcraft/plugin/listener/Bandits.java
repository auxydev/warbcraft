package dev.auxy.warbcraft.plugin.listener;

import dev.auxy.warbcraft.core.bandit.BanditKind;
import dev.auxy.warbcraft.core.bandit.BanditSpawner;
import dev.auxy.warbcraft.core.field.FieldDeployment;
import dev.auxy.warbcraft.core.troop.TroopType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.data.BanditData;
import dev.auxy.warbcraft.plugin.data.Skin;
import dev.auxy.warbcraft.plugin.field.SoldierKit;
import dev.auxy.warbcraft.plugin.npc.NpcBodies;
import dev.auxy.warbcraft.plugin.npc.NpcTags;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Dolasan haydut ceteleri. Oyuncularin cevresinde ara ara, yerlesim disinda dogar; turunu biyom
 * belirler (kurallar cekirdekte: {@link BanditSpawner}). Cete evinin cevresinde dolasir, yakina gelen
 * oyuncuya ya da askerine saldirir, kimse yakinda degilse bir sure sonra dagilir.
 */
public final class Bandits {

    private static final long TICK_PERIOD = 10L;
    private static final int SPAWN_CHECK_EVERY = 2;
    private static final double NEARBY_GANG_RADIUS = 96;
    private static final double AGGRO_RADIUS = 16;
    private static final double LEASH = 48;
    private static final double WANDER_RADIUS = 14;
    private static final double DESPAWN_DISTANCE = 112;
    private static final long DESPAWN_AFTER_MILLIS = 60_000;
    private static final int SPAWN_TRIES = 8;

    private final Plugin plugin;
    private final Game game;
    private final NpcBodies bodies;
    private final NpcTags tags;
    private final List<Gang> gangs = new ArrayList<>();
    private final Map<UUID, Long> nextAttempt = new HashMap<>();
    private final Random random = new Random();
    private int ticks;

    public Bandits(Plugin plugin, Game game, NpcBodies bodies, NpcTags tags) {
        this.plugin = plugin;
        this.game = game;
        this.bodies = bodies;
        this.tags = tags;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void shutdown() {
        gangs.forEach(Gang::destroy);
        gangs.clear();
    }

    public boolean isBandit(Entity entity) {
        return tags.isBandit(entity);
    }

    public List<String> kindIds() {
        return List.copyOf(data().kinds().keySet());
    }

    /**
     * Yonetici komutu: verilen turde (yoksa biyoma gore) bir ceteyi yakina dogurur.
     *
     * @return dogan haydut sayisi
     */
    public int spawnNear(Location around, String kindId) {
        BanditKind kind = kindId == null
                ? BanditSpawner.kindFor(data().kinds().values(), biomeKey(around.getBlock())).orElseThrow()
                : data().kinds().get(kindId);
        if (kind == null) {
            throw new IllegalArgumentException("Böyle bir haydut türü yok: " + kindId);
        }
        double angle = random.nextDouble() * Math.PI * 2;
        Location at = around.clone().add(Math.cos(angle) * 8, 0, Math.sin(angle) * 8);
        Block ground = ground(at);
        Gang gang = spawnGang(kind, ground.getLocation().add(0.5, 1, 0.5));
        return gang == null ? 0 : gang.members.size();
    }

    /** Yonetici icin: yakindaki ceteler, kac kisi, ne yapiyorlar. */
    public List<String> describeNear(Location around, double radius) {
        List<String> lines = new ArrayList<>();
        for (Gang gang : gangs) {
            if (gang.near(around, radius)) {
                Location at = gang.leaderLocation();
                String doing = gang.target == null ? "dolaşıyor"
                        : "saldırıyor: " + (gang.target instanceof Player p && !NpcTags.isAnyNpc(p) ? p.getName()
                        : tags.troop(gang.target).orElse(gang.target.getType().name().toLowerCase(Locale.ROOT)));
                long riders = gang.members.stream().filter(m -> m.mount != null).count();
                long mounted = gang.members.stream().filter(m -> m.mount != null && m.npc.getEntity() != null
                        && m.npc.getEntity().getVehicle() != null).count();
                lines.add(Msg.esc(gang.kind.name()) + " · " + gang.members.size() + " kişi"
                        + (riders > 0 ? " (atlı " + mounted + "/" + riders + " atında)" : "") + " · "
                        + (int) at.distance(around) + " blok · " + Msg.esc(doing));
            }
        }
        return lines;
    }

    /** Vurulan haydudun butun cetesi saldirgana doner. */
    public void onBanditAttacked(Entity victim, LivingEntity attacker) {
        if (isBandit(attacker)) {
            return;
        }
        for (Gang gang : gangs) {
            if (gang.contains(victim)) {
                gang.attack(attacker);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- dongu

    private void tick() {
        ticks++;
        if (ticks % SPAWN_CHECK_EVERY == 0) {
            trySpawns();
        }
        long now = System.currentTimeMillis();
        Iterator<Gang> it = gangs.iterator();
        while (it.hasNext()) {
            Gang gang = it.next();
            gang.prune();
            if (gang.members.isEmpty() || gang.abandoned(now)) {
                gang.destroy();
                it.remove();
                continue;
            }
            gang.update(now);
        }
    }

    private void trySpawns() {
        BanditData data = data();
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            if (now < nextAttempt.getOrDefault(player.getUniqueId(), 0L)) {
                continue;
            }
            nextAttempt.put(player.getUniqueId(), now + data.settings().intervalSeconds() * 1000L);
            int nearby = (int) gangs.stream().filter(g -> g.near(player.getLocation(), NEARBY_GANG_RADIUS)).count();
            if (BanditSpawner.shouldSpawn(data.settings(), nearby, random.nextDouble())) {
                spawnAround(player.getLocation());
            }
        }
    }

    private void spawnAround(Location center) {
        BanditSpawner.Settings settings = data().settings();
        World world = center.getWorld();
        for (int i = 0; i < SPAWN_TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = settings.minDistance() + random.nextDouble() * (settings.maxDistance() - settings.minDistance());
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (ground.isLiquid() || !ground.getType().isSolid() || Math.abs(ground.getY() - center.getY()) > 24) {
                continue;
            }
            Location at = ground.getLocation().add(0.5, 1, 0.5);
            if (game.settlementAt(at).isPresent()) {
                continue;
            }
            Optional<BanditKind> kind = BanditSpawner.kindFor(data().kinds().values(), biomeKey(ground));
            if (kind.isPresent()) {
                spawnGang(kind.get(), at);
                return;
            }
        }
    }

    private Gang spawnGang(BanditKind kind, Location home) {
        BanditData.Look look = data().looks().get(kind.id());
        Gang gang = new Gang(kind, home);
        for (Map.Entry<String, Integer> entry : BanditSpawner.rollGang(kind, random).entrySet()) {
            TroopType troop = game.tree().get(entry.getKey());
            for (int i = 0; i < entry.getValue(); i++) {
                Location at = home.clone().add(random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2);
                spawnMember(gang, troop, at, look);
            }
        }
        if (gang.members.isEmpty()) {
            return null;
        }
        gangs.add(gang);
        return gang;
    }

    private void spawnMember(Gang gang, TroopType troop, Location at, BanditData.Look look) {
        FieldDeployment.Stats stats = FieldDeployment.stats(troop);
        Skin skin = look == null ? Skin.NONE : look.skin();
        Optional<NPC> spawned = bodies.spawnHuman("<red>" + Msg.esc(troop.name()), true, at, skin,
                SoldierKit.outfit(troop), stats.maxHealth());
        if (spawned.isEmpty()) {
            return;
        }
        NPC npc = spawned.get();
        tags.tagBandit(npc.getEntity(), troop.id());
        Member member = new Member(npc);
        if (troop.category().isMounted()) {
            bodies.spawnHorse(at, SoldierKit.horseArmor(troop, look == null ? SoldierKit.color("haydut") : look.color()),
                    SoldierKit.horseColor(troop)).ifPresent(horse -> {
                        tags.tagBandit(horse.getEntity(), troop.id());
                        bodies.mount(npc, horse);
                        member.mount = horse;
                    });
        }
        NPC walker = member.walker();
        NpcBodies.roamerDefaults(walker);
        if (troop.category().isRanged()) {
            NpcBodies.ranged(walker, () -> living(npc), stats.arrowDamage(), stats.shootCooldownTicks());
        } else {
            NpcBodies.melee(walker, () -> living(npc), stats.attackDamage(), Math.max(10, 22 - troop.tier() * 2),
                    member.mount != null ? 3.6 : 2.8);
        }
        gang.members.add(member);
    }

    private static LivingEntity living(NPC npc) {
        return npc.isSpawned() && npc.getEntity() instanceof LivingEntity living ? living : null;
    }

    private boolean isTargetable(Entity entity) {
        if (entity instanceof Player player && !NpcTags.isAnyNpc(player)) {
            return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
        }
        return tags.isRetinue(entity) && entity instanceof LivingEntity;
    }

    private BanditData data() {
        return game.data().bandits();
    }

    /** Agac yapraklarini saymayan zemin: haydut agac tepesinde dogup orada kalmasin. */
    private static Block ground(Location at) {
        return at.getWorld().getHighestBlockAt(at.getBlockX(), at.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    private static String biomeKey(Block block) {
        return block.getBiome().getKey().getKey();
    }

    private final class Gang {
        private final BanditKind kind;
        private final Location home;
        private final List<Member> members = new ArrayList<>();
        private LivingEntity target;
        private long lastSeenPlayer = System.currentTimeMillis();
        private long nextWander;

        private Gang(BanditKind kind, Location home) {
            this.kind = kind;
            this.home = home;
        }

        private void update(long now) {
            Location center = leaderLocation();
            if (target != null && !keep(target)) {
                target = null;
            }
            if (target == null) {
                target = findTarget(center);
            }
            if (target != null) {
                attack(target);
            } else if (now >= nextWander) {
                wander();
                nextWander = now + 12_000 + random.nextInt(8_000);
            }
            for (Player player : center.getWorld().getPlayers()) {
                if (!NpcTags.isAnyNpc(player) && player.getLocation().distanceSquared(center) < DESPAWN_DISTANCE * DESPAWN_DISTANCE) {
                    lastSeenPlayer = now;
                    break;
                }
            }
        }

        private void attack(LivingEntity enemy) {
            target = enemy;
            for (Member member : members) {
                if (!member.walker().isSpawned()) {
                    continue;
                }
                Navigator navigator = member.walker().getNavigator();
                if (navigator.getEntityTarget() == null || navigator.getEntityTarget().getTarget() != enemy) {
                    navigator.setTarget(enemy, true);
                }
            }
        }

        private void wander() {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * WANDER_RADIUS;
            Block ground = ground(home.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance));
            Location point = ground.getLocation().add(0.5, 1, 0.5);
            for (Member member : members) {
                if (!member.walker().isSpawned()) {
                    continue;
                }
                member.walker().getNavigator().setTarget(point.clone().add(random.nextDouble() * 3 - 1.5, 0, random.nextDouble() * 3 - 1.5));
            }
        }

        private LivingEntity findTarget(Location center) {
            LivingEntity best = null;
            double bestDistance = AGGRO_RADIUS * AGGRO_RADIUS;
            for (Entity entity : center.getWorld().getNearbyEntities(center, AGGRO_RADIUS, 8, AGGRO_RADIUS)) {
                if (entity instanceof LivingEntity living && !living.isDead() && isTargetable(living)) {
                    double distance = living.getLocation().distanceSquared(center);
                    if (distance < bestDistance) {
                        best = living;
                        bestDistance = distance;
                    }
                }
            }
            return best;
        }

        private boolean keep(LivingEntity enemy) {
            return enemy.isValid() && !enemy.isDead() && enemy.getWorld().equals(home.getWorld())
                    && enemy.getLocation().distanceSquared(home) < LEASH * LEASH && isTargetable(enemy);
        }

        private Location leaderLocation() {
            for (Member member : members) {
                Entity entity = member.walker().getEntity();
                if (entity != null) {
                    return entity.getLocation();
                }
            }
            return home;
        }

        private boolean near(Location location, double radius) {
            return location.getWorld().equals(home.getWorld()) && leaderLocation().distanceSquared(location) < radius * radius;
        }

        private boolean contains(Entity entity) {
            for (Member member : members) {
                if (entity.equals(member.npc.getEntity()) || (member.mount != null && entity.equals(member.mount.getEntity()))) {
                    return true;
                }
            }
            return false;
        }

        private boolean abandoned(long now) {
            return now - lastSeenPlayer > DESPAWN_AFTER_MILLIS;
        }

        private void prune() {
            Iterator<Member> it = members.iterator();
            while (it.hasNext()) {
                Member member = it.next();
                if (!member.npc.isSpawned()) {
                    member.destroy();
                    it.remove();
                }
            }
        }

        private void destroy() {
            members.forEach(Member::destroy);
            members.clear();
        }

        @Override
        public String toString() {
            return kind.id() + "@" + home.getBlockX() + "," + home.getBlockZ();
        }
    }

    private static final class Member {
        private final NPC npc;
        private NPC mount;

        private Member(NPC npc) {
            this.npc = npc;
        }

        private NPC walker() {
            return mount != null && mount.isSpawned() ? mount : npc;
        }

        private void destroy() {
            NpcBodies.destroy(npc);
            NpcBodies.destroy(mount);
        }
    }
}
