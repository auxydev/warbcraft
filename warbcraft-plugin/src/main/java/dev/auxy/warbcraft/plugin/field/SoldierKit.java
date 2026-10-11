package dev.auxy.warbcraft.plugin.field;

import dev.auxy.warbcraft.core.troop.TroopCategory;
import dev.auxy.warbcraft.core.troop.TroopType;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Horse;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Map;

/**
 * Sahadaki askerin gorunumu: kultur rengi deri parcalarda, kademe arttikca zincir, demir ve elmas.
 * Yalnizca gorsel; savas degerleri cekirdekteki {@code FieldDeployment.stats} ile gelir.
 */
public final class SoldierKit {

    private static final Map<String, Color> CULTURE_COLORS = Map.of(
            "valdor", Color.fromRGB(0x2E5FA8),
            "nordhem", Color.fromRGB(0x9AA3AD),
            "aurelya", Color.fromRGB(0x7B2D8B),
            "karahan", Color.fromRGB(0xC8962E),
            "haydut", Color.fromRGB(0x5A4632));

    private SoldierKit() {
    }

    public static Color color(String culture) {
        return CULTURE_COLORS.getOrDefault(culture, Color.fromRGB(0x777777));
    }

    static void dress(EntityEquipment equipment, TroopType troop) {
        int tier = troop.tier();
        Color color = color(troop.culture());
        equipment.setHelmet(tier >= 6 ? item(Material.DIAMOND_HELMET)
                : tier >= 4 ? item(Material.IRON_HELMET)
                : tier >= 3 ? item(Material.CHAINMAIL_HELMET)
                : tier >= 2 ? leather(Material.LEATHER_HELMET, color) : null);
        equipment.setChestplate(tier >= 6 ? item(Material.DIAMOND_CHESTPLATE)
                : tier >= 5 ? item(Material.IRON_CHESTPLATE)
                : tier >= 4 ? item(Material.CHAINMAIL_CHESTPLATE)
                : leather(Material.LEATHER_CHESTPLATE, color));
        equipment.setLeggings(tier >= 4 ? item(Material.IRON_LEGGINGS)
                : tier >= 3 ? item(Material.CHAINMAIL_LEGGINGS)
                : tier >= 2 ? leather(Material.LEATHER_LEGGINGS, color) : null);
        // Cizmeler hep kultur renginde: agir zirhli askerde de taraf belli olsun.
        equipment.setBoots(leather(Material.LEATHER_BOOTS, color));
        equipment.setItemInMainHand(item(weapon(troop)));
        boolean shield = troop.category() == TroopCategory.INFANTRY && tier >= 2;
        equipment.setItemInOffHand(shield ? item(Material.SHIELD) : null);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.BODY) {
                equipment.setDropChance(slot, 0f);
            }
        }
    }

    static void dressHorse(Horse horse, TroopType troop) {
        int tier = troop.tier();
        horse.getInventory().setSaddle(item(Material.SADDLE));
        horse.getInventory().setArmor(tier >= 6 ? item(Material.DIAMOND_HORSE_ARMOR)
                : tier >= 4 ? item(Material.IRON_HORSE_ARMOR)
                : leather(Material.LEATHER_HORSE_ARMOR, color(troop.culture())));
        horse.setColor(tier >= 5 ? Horse.Color.WHITE : Horse.Color.BROWN);
        horse.setStyle(Horse.Style.NONE);
    }

    /** Menu simgesi icin de kullanilir. */
    public static Material weapon(TroopType troop) {
        int tier = troop.tier();
        if (troop.category().isRanged()) {
            return troop.culture().equals("valdor") ? Material.CROSSBOW : Material.BOW;
        }
        boolean axes = troop.culture().equals("nordhem");
        if (tier >= 5) {
            return axes ? Material.DIAMOND_AXE : Material.DIAMOND_SWORD;
        }
        if (tier >= 3) {
            return axes ? Material.IRON_AXE : Material.IRON_SWORD;
        }
        if (tier >= 2) {
            return axes ? Material.STONE_AXE : Material.STONE_SWORD;
        }
        return axes ? Material.WOODEN_AXE : Material.WOODEN_SWORD;
    }

    private static ItemStack item(Material material) {
        return new ItemStack(material);
    }

    private static ItemStack leather(Material material, Color color) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(color));
        return stack;
    }
}
