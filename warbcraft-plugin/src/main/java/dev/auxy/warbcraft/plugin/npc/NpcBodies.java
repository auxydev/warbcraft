package dev.auxy.warbcraft.plugin.npc;

import dev.auxy.warbcraft.plugin.data.Skin;
import dev.auxy.warbcraft.plugin.field.SoldierKit;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.TeleportStuckAction;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.HorseModifiers;
import net.citizensnpcs.trait.MountTrait;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Skinli insan NPC'ler (Citizens). Kayitlar yalnizca bellektedir: sunucu kapaninca ya da varlik
 * yuklenmeyen bir parcada kalinca NPC yok olur, Citizens'in kayit dosyasina yazilmaz.
 * Yurume ve hedef kovalama Citizens'in yol bulucusuyla, vurus ve ok atisi buradaki stratejiyle olur.
 */
public final class NpcBodies {

    private static final double HORSE_HEALTH = 30;
    private static final double HORSE_SPEED = 0.3;
    private static final float PATH_RANGE = 48f;
    private static final double ARCHER_RANGE = 18;
    private static final double ARCHER_KEEP_DISTANCE = 9;

    private final NPCRegistry registry = CitizensAPI.createInMemoryNPCRegistry("warbcraft");

    /** Insan NPC. Dogamazsa (orn. yuklu olmayan parca) bos doner. */
    public Optional<NPC> spawnHuman(String nameMini, boolean nameplate, Location at, Skin skin,
                                    SoldierKit.Outfit outfit, double maxHealth) {
        // Citizens adlari MiniMessage olarak okur; eski renk kodlari (section) olum mesajinda uyari doker.
        NPC npc = registry.createNPC(EntityType.PLAYER, nameMini);
        common(npc, nameplate);
        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        if (skin.isSigned()) {
            skinTrait.setSkinPersistent("warbcraft-" + Integer.toHexString(skin.texture().hashCode()),
                    skin.signature(), skin.texture());
        } else if (skin.isPlayerName()) {
            skinTrait.setSkinName(skin.playerName());
        } else {
            skinTrait.setFetchDefaultSkin(false);
        }
        Equipment equipment = npc.getOrAddTrait(Equipment.class);
        equipment.set(Equipment.EquipmentSlot.HELMET, orAir(outfit.helmet()));
        equipment.set(Equipment.EquipmentSlot.CHESTPLATE, orAir(outfit.chestplate()));
        equipment.set(Equipment.EquipmentSlot.LEGGINGS, orAir(outfit.leggings()));
        equipment.set(Equipment.EquipmentSlot.BOOTS, orAir(outfit.boots()));
        equipment.set(Equipment.EquipmentSlot.HAND, orAir(outfit.mainHand()));
        equipment.set(Equipment.EquipmentSlot.OFF_HAND, orAir(outfit.offHand()));
        return spawn(npc, at, maxHealth);
    }

    public Optional<NPC> spawnHorse(Location at, ItemStack armor, Horse.Color color) {
        NPC npc = registry.createNPC(EntityType.HORSE, "");
        common(npc, false);
        HorseModifiers horse = npc.getOrAddTrait(HorseModifiers.class);
        // Eyer yok: eyerli atta oyuncu tipindeki binici atin kontrolunu alir ve NPC tus basmadigi icin
        // at yerinde sayar. Eyersiz atin yolunu Citizens'in yol bulucusu yurutur.
        horse.setSaddle(null);
        horse.setArmor(armor);
        horse.setColor(color);
        horse.setStyle(Horse.Style.NONE);
        Optional<NPC> spawned = spawn(npc, at, HORSE_HEALTH);
        // Vanilya at hizi rastgeledir; suvari kosan oyuncuya yetisebilsin diye sabitlenir.
        spawned.map(NPC::getEntity).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                .map(horseEntity -> horseEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED))
                .ifPresent(speed -> speed.setBaseValue(HORSE_SPEED));
        return spawned;
    }

    public void mount(NPC rider, NPC horse) {
        rider.getOrAddTrait(MountTrait.class).setMountedOn(horse.getUniqueId());
    }

    /**
     * Yakin dovus: {@code walker} hedefe yurur (atli birlikte at), menzile girince {@code striker} vurur.
     *
     * @param reach vurus mesafesi (blok)
     */
    public static void melee(NPC walker, Supplier<LivingEntity> striker, double damage, int delayTicks, double reach) {
        NavigatorParameters params = walker.getNavigator().getDefaultParameters();
        params.attackRange(reach).attackDelayTicks(delayTicks).attackStrategy((attacker, target) -> {
            LivingEntity hitter = striker.get();
            if (hitter == null) {
                return false;
            }
            hitter.swingMainHand();
            target.damage(damage, hitter);
            return true;
        });
    }

    /** Menzilli: hedefe belli bir uzakliga kadar yaklasir, gorus hatti varsa ok atar. */
    public static void ranged(NPC walker, Supplier<LivingEntity> shooter, double arrowDamage, int delayTicks) {
        NavigatorParameters params = walker.getNavigator().getDefaultParameters();
        params.attackRange(ARCHER_RANGE).distanceMargin(ARCHER_KEEP_DISTANCE).attackDelayTicks(delayTicks)
                .attackStrategy((attacker, target) -> {
                    LivingEntity archer = shooter.get();
                    if (archer == null) {
                        return false;
                    }
                    shoot(archer, target, arrowDamage);
                    return true;
                });
    }

    /** Takilan NPC hedefine isinlanir (dar gecit, agac, cukur); yerinde donup kalmasindan iyidir. */
    public static void followerDefaults(NPC walker) {
        walker.getNavigator().getDefaultParameters().range(PATH_RANGE).stuckAction(TeleportStuckAction.INSTANCE);
    }

    public static void roamerDefaults(NPC walker) {
        walker.getNavigator().getDefaultParameters().range(PATH_RANGE).stuckAction(TeleportStuckAction.INSTANCE);
    }

    public static void shoot(LivingEntity archer, LivingEntity target, double arrowDamage) {
        Vector aim = target.getLocation().add(0, target.getHeight() * 0.6, 0).toVector()
                .subtract(archer.getEyeLocation().toVector());
        double horizontal = Math.hypot(aim.getX(), aim.getZ());
        aim.setY(aim.getY() + horizontal * 0.15);
        Arrow arrow = archer.launchProjectile(Arrow.class, aim.normalize().multiply(1.6));
        arrow.setDamage(arrowDamage);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        archer.getWorld().playSound(archer.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1f);
    }

    public Optional<NPC> npcOf(Entity entity) {
        return entity == null ? Optional.empty() : Optional.ofNullable(registry.getNPC(entity));
    }

    public static void destroy(NPC npc) {
        if (npc != null) {
            npc.destroy();
        }
    }

    public void destroyAll() {
        registry.deregisterAll();
    }

    private static void common(NPC npc, boolean nameplate) {
        npc.setProtected(false);
        npc.data().set(NPC.Metadata.NAMEPLATE_VISIBLE, nameplate);
        // Renkli ad hologramla gosterilir; oyuncu profil adina renk kodu girmesin (Adventure uyarisi).
        npc.data().set(NPC.Metadata.ALWAYS_USE_NAME_HOLOGRAM, true);
        npc.data().set(NPC.Metadata.DROPS_ITEMS, false);
        npc.data().set(NPC.Metadata.RESPAWN_DELAY, -1);
        npc.data().set(NPC.Metadata.COLLIDABLE, false);
        npc.data().set(NPC.Metadata.SHOULD_SAVE, false);
        npc.data().set(NPC.Metadata.REMOVE_FROM_TABLIST, true);
    }

    private static ItemStack orAir(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item;
    }

    private static Optional<NPC> spawn(NPC npc, Location at, double maxHealth) {
        if (!npc.spawn(at) || !(npc.getEntity() instanceof LivingEntity living)) {
            npc.destroy();
            return Optional.empty();
        }
        AttributeInstance health = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(maxHealth);
            living.setHealth(maxHealth);
        }
        return Optional.of(npc);
    }
}
