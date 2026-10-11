package dev.auxy.warbcraft.plugin.world;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.field.FieldDeployment;
import dev.auxy.warbcraft.core.field.GarrisonWatch;
import dev.auxy.warbcraft.core.npc.NpcKing;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.troop.TroopType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.data.SettlementSite;
import dev.auxy.warbcraft.plugin.data.Skin;
import dev.auxy.warbcraft.plugin.field.Retinue;
import dev.auxy.warbcraft.plugin.field.SoldierKit;
import dev.auxy.warbcraft.plugin.npc.NpcBodies;
import dev.auxy.warbcraft.plugin.npc.NpcTags;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Yerlesimleri dunyada canlandirir: merkezde ad tabelasi, baskentte kral ve muhafizlari, sehir ve
 * kalelerde garnizondan nobetciler. Yalnizca yakinda gercek bir oyuncu varken var olurlar
 * (performans); oyuncu uzaklasinca kaldirilir, garnizon sayisi korunur.
 *
 * <p>Nobetciler yerlesimin icinde dolasir; yerlesimin kralligiyla savastaki oyunculara ve onlarin
 * askerlerine, haydutlara saldirir. Olen nobetci garnizondan duser. Yerlesime girene baslik gosterilir.
 */
public final class SettlementLife implements Listener {

    private static final long TICK_PERIOD = 10L;
    private static final int SYNC_EVERY = 4;
    private static final double ACTIVATE_DISTANCE = 96;
    private static final long DEACTIVATE_AFTER_MILLIS = 30_000;
    private static final int ROYAL_GUARDS = 2;
    private static final double LEASH_EXTRA = 16;
    private static final double SIGN_HEIGHT = 4.5;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final Game game;
    private final NpcBodies bodies;
    private final NpcTags tags;
    private final Consumer<Player> openKingMenu;
    private final Map<String, Post> posts = new HashMap<>();
    private final Map<UUID, String> playerIn = new HashMap<>();
    private final Random random = new Random();
    private int ticks;

    public SettlementLife(Plugin plugin, Game game, NpcBodies bodies, NpcTags tags, Consumer<Player> openKingMenu) {
        this.plugin = plugin;
        this.game = game;
        this.bodies = bodies;
        this.tags = tags;
        this.openKingMenu = openKingMenu;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void shutdown() {
        posts.values().forEach(Post::deactivate);
        posts.clear();
    }

    /** Krallarin oturdugu yerin merkezi (web harita icin). */
    public Optional<Location> seatOf(NpcKing king) {
        return Optional.ofNullable(game.data().sites().get(king.seatId())).map(SettlementLife::center);
    }

    /** Yonetici icin: etkin yerlesimler, nobetciler ve ne yaptiklari. */
    public List<String> describeActive() {
        List<String> lines = new ArrayList<>();
        for (Post post : posts.values()) {
            if (!post.active) {
                continue;
            }
            long navigating = post.guards.stream().filter(g -> g.npc.isSpawned() && g.npc.getNavigator().isNavigating()).count();
            lines.add(Msg.esc(post.settlement.name()) + " · kral " + (post.king != null && post.king.isSpawned() ? "var" : "yok")
                    + " · nöbetçi " + post.guards.size() + " (" + navigating + " yürüyor)"
                    + (post.target == null ? "" : " · hedef " + Msg.esc(post.target.getName())));
        }
        return lines;
    }

    // ---------------------------------------------------------------- dongu

    private void tick() {
        ticks++;
        boolean sync = ticks % SYNC_EVERY == 0;
        long now = System.currentTimeMillis();
        List<Player> players = NpcTags.realPlayers();
        for (SettlementSite site : game.data().sites().values()) {
            Optional<Settlement> settlement = game.realm().findSettlement(site.settlementId());
            World world = Bukkit.getWorld(site.world());
            if (settlement.isEmpty() || world == null) {
                continue;
            }
            Post post = posts.computeIfAbsent(site.settlementId(), id -> new Post(settlement.get(), site));
            boolean someoneNear = false;
            for (Player player : players) {
                // Zemin hesabi parca yukler; uzaklik icin dosyadaki x/z yeter.
                double dx = player.getLocation().getX() - site.x();
                double dz = player.getLocation().getZ() - site.z();
                if (player.getWorld().equals(world) && dx * dx + dz * dz < square(ACTIVATE_DISTANCE + site.radius())) {
                    someoneNear = true;
                    break;
                }
            }
            if (someoneNear) {
                post.lastSeen = now;
                if (!post.active) {
                    post.activate();
                }
            } else if (post.active && now - post.lastSeen > DEACTIVATE_AFTER_MILLIS) {
                post.deactivate();
            }
            if (post.active) {
                if (sync) {
                    post.sync();
                }
                post.command(now);
            }
        }
        announceEntries(players);
    }

    /** Yerlesime girene baslik, cikana eylem cubugu. */
    private void announceEntries(List<Player> players) {
        for (Player player : players) {
            Optional<Settlement> here = game.settlementAt(player.getLocation());
            String now = here.map(Settlement::id).orElse(null);
            String before = playerIn.get(player.getUniqueId());
            if (Objects.equals(now, before)) {
                continue;
            }
            if (now == null) {
                playerIn.remove(player.getUniqueId());
                game.realm().findSettlement(before).ifPresent(left ->
                        player.sendActionBar(MM.deserialize("<gray>" + Msg.esc(left.name()) + " sınırından çıktın.")));
                continue;
            }
            playerIn.put(player.getUniqueId(), now);
            Settlement settlement = here.get();
            player.showTitle(Title.title(MM.deserialize(color(settlement) + Msg.esc(settlement.name())),
                    MM.deserialize("<gray>" + subtitle(settlement)),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(600))));
        }
    }

    // ---------------------------------------------------------------- olaylar

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuardDeath(EntityDeathEvent event) {
        Optional<String> settlementId = tags.guardOf(event.getEntity());
        if (settlementId.isEmpty()) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (event instanceof PlayerDeathEvent death) {
            death.deathMessage(null);
        }
        Post post = posts.get(settlementId.get());
        if (post != null) {
            post.guards.removeIf(guard -> event.getEntity().equals(guard.npc.getEntity()));
        }
        Party garrison = game.garrisons().get(settlementId.get());
        Optional<String> troopId = tags.troop(event.getEntity());
        if (garrison != null && troopId.isPresent() && garrison.count(troopId.get()) > 0) {
            boolean fatal = FieldDeployment.fallIsFatal(random.nextDouble());
            garrison.applyCasualties(troopId.get(), fatal ? 1 : 0, fatal ? 0 : 1);
        }
    }

    /** Nobetciye vurulursa butun nobetciler saldirgana doner (savas olmasa bile). */
    @EventHandler(ignoreCancelled = true)
    public void onGuardHit(EntityDamageByEntityEvent event) {
        Optional<String> settlementId = tags.guardOf(event.getEntity());
        if (settlementId.isEmpty() || !(Retinue.resolve(event.getDamager()) instanceof LivingEntity attacker)) {
            return;
        }
        if (tags.guardOf(attacker).isPresent()) {
            event.setCancelled(true);
            return;
        }
        Post post = posts.get(settlementId.get());
        if (post != null) {
            post.attack(attacker);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKingClick(PlayerInteractEntityEvent event) {
        if (tags.king(event.getRightClicked()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        openKingMenu.accept(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerIn.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- yardimcilar

    private Optional<Kingdom> kingdomOf(Settlement settlement) {
        return game.realm().kingdomOf(settlement);
    }

    /** Bu yerlesimin nobetcilerinin saldiracagi biri mi? */
    private boolean hostile(Settlement settlement, LivingEntity entity) {
        if (tags.isBandit(entity)) {
            return true;
        }
        Optional<Kingdom> owner = kingdomOf(settlement);
        if (owner.isEmpty()) {
            return false;
        }
        UUID playerId;
        if (entity instanceof Player player && !NpcTags.isAnyNpc(player)) {
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
                return false;
            }
            playerId = player.getUniqueId();
        } else if (tags.isRetinue(entity)) {
            playerId = tags.owner(entity).orElse(null);
        } else {
            return false;
        }
        if (playerId == null) {
            return false;
        }
        Optional<String> theirKingdom = game.clanOf(playerId).map(Clan::kingdomId);
        return theirKingdom.isPresent() && game.realm().atWar(owner.get().id(), theirKingdom.get());
    }

    private String color(Settlement settlement) {
        return kingdomOf(settlement).map(k -> k.npc() ? cultureTag(k.culture()) : "<yellow>").orElse("<white>");
    }

    private static String cultureTag(String culture) {
        org.bukkit.Color c = SoldierKit.color(culture);
        return String.format("<#%02x%02x%02x>", c.getRed(), c.getGreen(), c.getBlue());
    }

    private String subtitle(Settlement settlement) {
        String type = switch (settlement.type()) {
            case TOWN -> "Şehir";
            case CASTLE -> "Kale";
            case VILLAGE -> "Köy";
        };
        return type + " · " + kingdomOf(settlement).map(k -> Msg.esc(k.name())).orElse("bağımsız");
    }

    private static Location center(SettlementSite site) {
        World world = Bukkit.getWorld(site.world());
        Location wanted = new Location(world, site.x() + 0.5, site.y(), site.z() + 0.5);
        if (world == null) {
            return wanted;
        }
        // Konum dosyadaki y'ye degil, o x/z'deki zemine oturur (yeni haritada y degisir).
        return world.getHighestBlockAt(wanted, HeightMap.MOTION_BLOCKING_NO_LEAVES).getLocation().add(0.5, 1, 0.5);
    }

    private static double square(double value) {
        return value * value;
    }

    private static LivingEntity living(NPC npc) {
        return npc.isSpawned() && npc.getEntity() instanceof LivingEntity living ? living : null;
    }

    /** Bir yerlesimin dunyadaki hali. */
    private final class Post {
        private final Settlement settlement;
        private final SettlementSite site;
        private final List<Guard> guards = new ArrayList<>();
        private boolean active;
        private long lastSeen;
        private NPC king;
        private TextDisplay sign;
        /** Etkinken hesaplanan zemin merkezi (her tikte zemin aranmasin). */
        private Location center;
        private LivingEntity target;
        /** Savas olmasa da nobetciye saldiran; o da kovalanir. */
        private LivingEntity provoker;

        private Post(Settlement settlement, SettlementSite site) {
            this.settlement = settlement;
            this.site = site;
        }

        private void activate() {
            active = true;
            center = center(site);
            sign = center.getWorld().spawn(center.clone().add(0, SIGN_HEIGHT, 0), TextDisplay.class, display -> {
                display.setPersistent(false);
                display.setBillboard(Display.Billboard.CENTER);
                display.setShadowed(true);
                display.text(MM.deserialize("<bold>" + color(settlement) + Msg.esc(settlement.name()) + "</bold>\n<gray>"
                        + subtitle(settlement)));
            });
            spawnKing(center);
            sync();
        }

        private void deactivate() {
            active = false;
            if (sign != null) {
                sign.remove();
                sign = null;
            }
            NpcBodies.destroy(king);
            king = null;
            guards.forEach(Guard::destroy);
            guards.clear();
            target = null;
        }

        private void spawnKing(Location center) {
            for (NpcKing npcKing : game.data().kings().values()) {
                if (!npcKing.seatId().equals(settlement.id())) {
                    continue;
                }
                // Kral yerlesimin sahibi kralliga hala hukmediyorsa tahtindadir.
                if (kingdomOf(settlement).filter(k -> k.id().equals(npcKing.kingdomId())).isEmpty()) {
                    continue;
                }
                Skin skin = game.data().kingSkins().getOrDefault(npcKing.id(), Skin.NONE);
                List<TroopType> best = game.tree().all().stream()
                        .filter(t -> t.culture().equals(settlement.culture())).toList();
                TroopType outfitOf = best.stream().max((a, b) -> Integer.compare(a.tier(), b.tier())).orElse(null);
                SoldierKit.Outfit outfit = outfitOf == null ? new SoldierKit.Outfit(null, null, null, null, null, null)
                        : new SoldierKit.Outfit(null, null, null, null, SoldierKit.outfit(outfitOf).mainHand(), null);
                bodies.spawnHuman("<gold><bold>" + Msg.esc(npcKing.name()), true, center, skin, outfit, 60)
                        .ifPresent(npc -> {
                            npc.setProtected(true);
                            npc.getOrAddTrait(LookClose.class).lookClose(true);
                            tags.tagKing(npc.getEntity(), npcKing.id());
                            king = npc;
                        });
            }
        }

        /** Gorunen nobetcileri garnizonla esitler. */
        private void sync() {
            guards.removeIf(guard -> {
                if (!guard.npc.isSpawned()) {
                    guard.destroy();
                    return true;
                }
                return false;
            });
            if (king != null && !king.isSpawned()) {
                NpcBodies.destroy(king);
                king = null;
                spawnKing(center);
            }
            Party garrison = game.garrisons().get(settlement.id());
            if (garrison == null) {
                return;
            }
            Map<String, Integer> wanted = FieldDeployment.select(garrison, game.tree(),
                    GarrisonWatch.visibleGuards(garrison.healthyCount()));
            Map<String, Integer> have = new HashMap<>();
            for (Guard guard : guards) {
                have.merge(guard.troopId, 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
                int missing = entry.getValue() - have.getOrDefault(entry.getKey(), 0);
                for (int i = 0; i < missing; i++) {
                    spawnGuard(game.tree().get(entry.getKey()), center);
                }
            }
        }

        private void spawnGuard(TroopType troop, Location center) {
            FieldDeployment.Stats stats = FieldDeployment.stats(troop);
            Location at = Retinue.ground(center.clone().add(random.nextDouble() * 6 - 3, 0, random.nextDouble() * 6 - 3))
                    .orElse(center);
            Skin skin = game.data().cultureSkins().getOrDefault(troop.culture(), Skin.NONE);
            Optional<NPC> spawned = bodies.spawnHuman(Retinue.tierColor(troop.tier()) + Msg.esc(troop.name()), false, at,
                    skin, SoldierKit.outfit(troop), stats.maxHealth());
            if (spawned.isEmpty()) {
                return;
            }
            NPC npc = spawned.get();
            tags.tagGuard(npc.getEntity(), settlement.id(), troop.id());
            NpcBodies.roamerDefaults(npc);
            if (troop.category().isRanged()) {
                NpcBodies.ranged(npc, () -> living(npc), stats.arrowDamage(), stats.shootCooldownTicks());
            } else {
                NpcBodies.melee(npc, () -> living(npc), stats.attackDamage(), Math.max(10, 22 - troop.tier() * 2), 2.8);
            }
            boolean royal = king != null && guards.stream().filter(g -> g.royal).count() < ROYAL_GUARDS;
            guards.add(new Guard(npc, troop.id(), royal));
        }

        private void command(long now) {
            double leash = site.radius() + LEASH_EXTRA;
            if (target != null && (!target.isValid() || target.isDead() || !target.getWorld().equals(center.getWorld())
                    || target.getLocation().distanceSquared(center) > leash * leash || !stillHostile(target))) {
                target = null;
                guards.forEach(guard -> {
                    if (guard.npc.isSpawned()) {
                        guard.npc.getNavigator().cancelNavigation();
                    }
                });
            }
            if (target == null) {
                target = findHostile(center);
            }
            if (target != null) {
                attack(target);
                return;
            }
            for (Guard guard : guards) {
                if (!guard.npc.isSpawned() || now < guard.nextMove) {
                    continue;
                }
                guard.nextMove = now + 12_000 + random.nextInt(10_000);
                Location point;
                if (guard.royal && king != null && king.isSpawned()) {
                    // Kralin muhafizlari tahtin iki yaninda bekler.
                    int side = guards.indexOf(guard) % 2 == 0 ? 1 : -1;
                    point = king.getEntity().getLocation().clone().add(side * 2.0, 0, 1.0);
                } else {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = random.nextDouble() * site.radius() * 0.7;
                    point = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                }
                Retinue.ground(point).ifPresent(ground -> guard.npc.getNavigator().setTarget(ground));
            }
        }

        private boolean stillHostile(LivingEntity entity) {
            // Kendisine saldirani (savas olmasa da) kovalamaya devam eder; digerleri icin savas sarti.
            return entity.equals(provoker) || hostile(settlement, entity);
        }

        private void attack(LivingEntity enemy) {
            if (!hostile(settlement, enemy)) {
                provoker = enemy;
            }
            target = enemy;
            for (Guard guard : guards) {
                if (!guard.npc.isSpawned()) {
                    continue;
                }
                Navigator navigator = guard.npc.getNavigator();
                if (navigator.getEntityTarget() == null || navigator.getEntityTarget().getTarget() != enemy) {
                    navigator.setTarget(enemy, true);
                }
            }
        }

        private LivingEntity findHostile(Location center) {
            double range = site.radius() + 8;
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Entity entity : center.getWorld().getNearbyEntities(center, range, 16, range)) {
                if (entity instanceof LivingEntity living && !living.isDead() && hostile(settlement, living)) {
                    double distance = living.getLocation().distanceSquared(center);
                    if (distance < bestDistance) {
                        best = living;
                        bestDistance = distance;
                    }
                }
            }
            return best;
        }
    }

    private static final class Guard {
        private final NPC npc;
        private final String troopId;
        private final boolean royal;
        private long nextMove;

        private Guard(NPC npc, String troopId, boolean royal) {
            this.npc = npc;
            this.troopId = troopId;
            this.royal = royal;
        }

        private void destroy() {
            NpcBodies.destroy(npc);
        }
    }
}
