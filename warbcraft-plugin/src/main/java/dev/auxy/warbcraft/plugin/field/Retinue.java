package dev.auxy.warbcraft.plugin.field;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import dev.auxy.warbcraft.core.field.FieldDeployment;
import dev.auxy.warbcraft.core.field.FieldFormation;
import dev.auxy.warbcraft.core.field.FieldOrder;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.troop.TroopType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.listener.Bandits;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Oyuncunun partisinden sahaya cikan askerler. Her oyuncunun arkasinda en fazla
 * {@code oyun.sahada-asker} asker yurur (secim cekirdekte: {@link FieldDeployment#select}).
 * Askerler kalici degildir: dunya kaydina yazilmaz, oyuncu cikinca kaybolur, parti degisince
 * (toplama, terfi, terhis, yaralanma) sahadaki dizilis kendiliginden guncellenir.
 *
 * <p>Asker bir Husk'tir (gunes yakmaz); yakin dovusu vanilya saldiri hedefi yapar, hedef secimi,
 * formasyon ve ok atisi buradadir. Atli birlikler bir ata biner; binicinin yol bulucusu ati surer.
 */
public final class Retinue {

    private static final long TICK_PERIOD = 5L;
    private static final int SYNC_EVERY = 4;
    private static final int SPAWN_PER_SYNC = 4;
    private static final double REGROUP_DISTANCE = 40;
    private static final double SLOT_TOLERANCE = 1.3;
    private static final double SHOOT_RANGE = 22;
    private static final double TARGET_LEASH = 12;
    private static final long THREAT_MILLIS = 10_000;
    private static final double MOVE_SPEED = 0.3;
    private static final double HORSE_SPEED = 0.3;
    private static final double HORSE_HEALTH = 30;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final Game game;
    private final Bandits bandits;
    private final NamespacedKey ownerKey;
    private final NamespacedKey troopKey;
    private final Map<UUID, Band> bands = new HashMap<>();
    private final Random random = new Random();
    private int ticks;

    public Retinue(Plugin plugin, Game game, Bandits bandits) {
        this.plugin = plugin;
        this.game = game;
        this.bandits = bandits;
        this.ownerKey = new NamespacedKey(plugin, "asker_sahibi");
        this.troopKey = new NamespacedKey(plugin, "asker_birlik");
    }

    public void start() {
        purgeStrays();
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
            forEachMob(band, mob -> mob.setTarget(null));
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

    /** Askerler ve atlari. */
    public boolean isTagged(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    public boolean isSoldier(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(troopKey, PersistentDataType.STRING);
    }

    /** Varligin tarafi: oyuncunun kendisi ya da askerinin/atinin sahibi. */
    public Optional<UUID> sideOf(Entity entity) {
        if (entity instanceof Player player) {
            return Optional.of(player.getUniqueId());
        }
        if (entity == null) {
            return Optional.empty();
        }
        String owner = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return owner == null ? Optional.empty() : Optional.of(UUID.fromString(owner));
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

    /** Oyuncunun vurmasiyla askerlerin de saldirmasi gereken yaratik mi (koylu, inek vb. degil). */
    public boolean isFoe(LivingEntity entity, Player owner) {
        if (entity instanceof Player || isTagged(entity)) {
            return false;
        }
        return bandits.isBandit(entity)
                || (entity instanceof Enemy && !(entity instanceof Creeper))
                || (entity instanceof Mob mob && owner.equals(mob.getTarget()));
    }

    /** Sahadaki asker dustu: partide olur ya da yaralanir, yerine yedekten biri gelir. */
    public void onSoldierDeath(LivingEntity dead) {
        String troopId = dead.getPersistentDataContainer().get(troopKey, PersistentDataType.STRING);
        Optional<UUID> ownerId = sideOf(dead);
        if (troopId == null || ownerId.isEmpty()) {
            return;
        }
        Band band = bands.get(ownerId.get());
        Soldier soldier = band == null ? null : band.soldiers.remove(dead.getUniqueId());
        if (soldier != null) {
            removeEntity(soldier.mountId);
        }
        Player owner = Bukkit.getPlayer(ownerId.get());
        if (owner == null) {
            return;
        }
        Party party = game.profile(owner).party();
        if (party.count(troopId) == 0) {
            return;
        }
        boolean fatal = FieldDeployment.fallIsFatal(random.nextDouble());
        party.applyCasualties(troopId, fatal ? 1 : 0, fatal ? 0 : 1);
        String name = Msg.esc(game.tree().find(troopId).map(TroopType::name).orElse(troopId));
        owner.sendActionBar(MM.deserialize(fatal
                ? "<red>✝ " + name + " öldü."
                : "<gold>✚ " + name + " yaralandı; iyileşince saflara döner."));
    }

    /** Askerin oldurdugu yaratigin deneyimi yigina yazilir (terfi icin). */
    public void creditKill(Entity soldier, LivingEntity victim) {
        String troopId = soldier.getPersistentDataContainer().get(troopKey, PersistentDataType.STRING);
        Optional<Player> owner = sideOf(soldier).map(Bukkit::getPlayer);
        if (troopId == null || owner.isEmpty()) {
            return;
        }
        AttributeInstance health = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        game.profile(owner.get()).party().addXp(troopId, FieldDeployment.killXp(health == null ? 20 : health.getValue()));
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
                removeEntity(soldier.id);
                removeEntity(soldier.mountId);
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
            if (!(Bukkit.getEntity(soldier.id) instanceof Mob mob)) {
                continue;
            }
            FieldFormation.Slot slot = band.formation.slot(index++);
            Entity body = mob.getVehicle() != null ? mob.getVehicle() : mob;
            if (!body.getWorld().equals(anchor.getWorld())
                    || body.getLocation().distanceSquared(anchor) > REGROUP_DISTANCE * REGROUP_DISTANCE) {
                body.teleport(safe(slotLocation(anchor, heading, slot), anchor), TeleportFlag.EntityState.RETAIN_PASSENGERS);
                mob.setTarget(null);
                continue;
            }
            Optional<TroopType> troop = game.tree().find(soldier.troopId);
            if (troop.isEmpty()) {
                continue;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && !keepTarget(target, player, band, anchor)) {
                mob.setTarget(null);
                target = null;
            }
            if (target == null) {
                target = threat != null ? threat : nearest(foes, body.getLocation());
                if (target != null) {
                    mob.setTarget(target);
                }
            }
            if (target != null) {
                if (troop.get().category().isRanged()) {
                    shoot(mob, soldier, troop.get(), target);
                }
                // Yakin dovuscu: vanilya saldiri hedefi kovalar ve vurur.
                continue;
            }
            march(mob, body, slotLocation(anchor, heading, slot));
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
                && !(target instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR));
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

    private void march(Mob mob, Entity body, Location point) {
        Location at = body.getLocation();
        double dx = point.getX() - at.getX();
        double dz = point.getZ() - at.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < SLOT_TOLERANCE) {
            if (mob.getPathfinder().hasPath()) {
                mob.getPathfinder().stopPathfinding();
            }
            return;
        }
        double speed = distance > 8 ? 1.6 : distance > 3 ? 1.25 : 1.0;
        mob.getPathfinder().moveTo(point, speed);
    }

    private void shoot(Mob mob, Soldier soldier, TroopType troop, LivingEntity target) {
        soldier.cooldown -= (int) TICK_PERIOD;
        double distance = mob.getLocation().distance(target.getLocation());
        if (distance > SHOOT_RANGE || !mob.hasLineOfSight(target)) {
            mob.getPathfinder().moveTo(target, 1.2);
            return;
        }
        mob.getPathfinder().stopPathfinding();
        mob.lookAt(target);
        if (soldier.cooldown > 0) {
            return;
        }
        FieldDeployment.Stats stats = FieldDeployment.stats(troop);
        soldier.cooldown = stats.shootCooldownTicks();
        Vector aim = target.getLocation().add(0, target.getHeight() * 0.6, 0).toVector()
                .subtract(mob.getEyeLocation().toVector());
        double horizontal = Math.hypot(aim.getX(), aim.getZ());
        aim.setY(aim.getY() + horizontal * 0.15);
        Arrow arrow = mob.launchProjectile(Arrow.class, aim.normalize().multiply(1.6));
        arrow.setDamage(stats.arrowDamage());
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1f);
    }

    // ---------------------------------------------------------------- varlik yonetimi

    private void spawn(Player player, Band band, TroopType troop) {
        Location at = safe(slotLocation(player.getLocation(), band.heading, band.formation.slot(band.soldiers.size())),
                player.getLocation());
        World world = at.getWorld();
        FieldDeployment.Stats stats = FieldDeployment.stats(troop);
        Husk husk = world.spawn(at, Husk.class, false, h -> prepare(h, troop, stats, player));
        if (!husk.isValid()) {
            return;
        }
        strip(husk, troop.category().isRanged());
        Soldier soldier = new Soldier(husk.getUniqueId(), troop.id());
        if (troop.category().isMounted()) {
            Horse horse = world.spawn(at, Horse.class, false, h -> prepareHorse(h, troop, player));
            if (horse.isValid()) {
                Bukkit.getMobGoals().removeAllGoals(horse);
                horse.addPassenger(husk);
                soldier.mountId = horse.getUniqueId();
            }
        }
        band.soldiers.put(soldier.id, soldier);
    }

    private void prepare(Husk husk, TroopType troop, FieldDeployment.Stats stats, Player owner) {
        husk.setAdult();
        husk.setShouldBurnInDay(false);
        husk.setCanPickupItems(false);
        husk.setCanBreakDoors(false);
        husk.setPersistent(false);
        husk.setRemoveWhenFarAway(false);
        husk.customName(MM.deserialize(tierColor(troop.tier()) + Msg.esc(troop.name())));
        husk.setCustomNameVisible(false);
        setBase(husk, Attribute.GENERIC_MAX_HEALTH, stats.maxHealth());
        husk.setHealth(stats.maxHealth());
        setBase(husk, Attribute.GENERIC_ATTACK_DAMAGE, stats.attackDamage());
        setBase(husk, Attribute.GENERIC_MOVEMENT_SPEED, MOVE_SPEED);
        setBase(husk, Attribute.GENERIC_FOLLOW_RANGE, 48);
        setBase(husk, Attribute.ZOMBIE_SPAWN_REINFORCEMENTS, 0);
        SoldierKit.dress(husk.getEquipment(), troop);
        PersistentDataContainer data = husk.getPersistentDataContainer();
        data.set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        data.set(troopKey, PersistentDataType.STRING, troop.id());
    }

    private void prepareHorse(Horse horse, TroopType troop, Player owner) {
        horse.setAdult();
        horse.setTamed(true);
        horse.setPersistent(false);
        horse.setRemoveWhenFarAway(false);
        setBase(horse, Attribute.GENERIC_MAX_HEALTH, HORSE_HEALTH);
        horse.setHealth(HORSE_HEALTH);
        setBase(horse, Attribute.GENERIC_MOVEMENT_SPEED, HORSE_SPEED);
        SoldierKit.dressHorse(horse, troop);
        horse.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
    }

    /**
     * Hedef bulma ve gezinme davranislarini siler; yakin dovuscude vanilya saldiri hedefi kalir.
     * Menzilli asker yerinden atis yapar (bkz. {@link #shoot}).
     */
    private static <T extends Mob> void strip(T mob, boolean ranged) {
        MobGoals goals = Bukkit.getMobGoals();
        goals.removeAllGoals(mob, GoalType.TARGET);
        for (Goal<T> goal : new ArrayList<>(goals.getAllGoals(mob))) {
            String key = goal.getKey().getNamespacedKey().getKey();
            boolean keep = key.contains("look") || (!ranged && key.contains("attack"));
            if (!keep) {
                goals.removeGoal(mob, goal);
            }
        }
    }

    private void prune(Band band) {
        Iterator<Soldier> it = band.soldiers.values().iterator();
        while (it.hasNext()) {
            Soldier soldier = it.next();
            Entity entity = Bukkit.getEntity(soldier.id);
            if (entity == null || !entity.isValid()) {
                removeEntity(soldier.mountId);
                it.remove();
            }
        }
    }

    private void dismissAll(Band band) {
        for (Soldier soldier : band.soldiers.values()) {
            removeEntity(soldier.id);
            removeEntity(soldier.mountId);
        }
        band.soldiers.clear();
    }

    private static void removeEntity(UUID id) {
        if (id != null) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    /** Onceki calismadan kalmis (cokmede kaydedilmis olabilecek) askerleri temizler. */
    private void purgeStrays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isTagged(entity)) {
                    entity.remove();
                }
            }
        }
    }

    private void forEachMob(Band band, Consumer<Mob> action) {
        for (Soldier soldier : band.soldiers.values()) {
            if (Bukkit.getEntity(soldier.id) instanceof Mob mob) {
                action.accept(mob);
            }
        }
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
    private static Location safe(Location wanted, Location fallback) {
        for (int dy : new int[] {0, 1, -1, 2, -2, 3}) {
            Block feet = wanted.getBlock().getRelative(0, dy, 0);
            if (feet.isPassable() && feet.getRelative(BlockFace.UP).isPassable()
                    && feet.getRelative(BlockFace.DOWN).getType().isSolid()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return fallback;
    }

    private static void setBase(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
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
        private final UUID id;
        private final String troopId;
        private UUID mountId;
        private int cooldown;

        private Soldier(UUID id, String troopId) {
            this.id = id;
            this.troopId = troopId;
        }
    }
}
