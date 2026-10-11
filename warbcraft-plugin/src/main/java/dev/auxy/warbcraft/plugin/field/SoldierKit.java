package dev.auxy.warbcraft.plugin.field;

import dev.auxy.warbcraft.core.troop.TroopCategory;
import dev.auxy.warbcraft.core.troop.TroopType;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Horse;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Map;

/**
 * Sahadaki askerin ve haydudun gorunumu: taraf rengi deri parcalarda, kademe arttikca zincir,
 * demir ve elmas. Yalnizca gorsel; savas degerleri cekirdekteki {@code FieldDeployment.stats} ile gelir.
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

    /** Bir NPC'nin giyecegi parcalar; bos yuva {@code null}. */
    public record Outfit(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots,
                         ItemStack mainHand, ItemStack offHand) {
    }

    public static Color color(String culture) {
        return CULTURE_COLORS.getOrDefault(culture, Color.fromRGB(0x777777));
    }

    /** Kultur renginde kiyafet (oyuncu askerleri). */
    public static Outfit outfit(TroopType troop) {
        return outfit(troop, color(troop.culture()));
    }

    /** Verilen renkte kiyafet (haydut turleri kendi rengini tasir). */
    public static Outfit outfit(TroopType troop, Color color) {
        int tier = troop.tier();
        ItemStack helmet = tier >= 6 ? item(Material.DIAMOND_HELMET)
                : tier >= 4 ? item(Material.IRON_HELMET)
                : tier >= 3 ? item(Material.CHAINMAIL_HELMET)
                : leather(Material.LEATHER_HELMET, color);
        ItemStack chest = tier >= 6 ? item(Material.DIAMOND_CHESTPLATE)
                : tier >= 5 ? item(Material.IRON_CHESTPLATE)
                : tier >= 4 ? item(Material.CHAINMAIL_CHESTPLATE)
                : leather(Material.LEATHER_CHESTPLATE, color);
        ItemStack legs = tier >= 4 ? item(Material.IRON_LEGGINGS)
                : tier >= 3 ? item(Material.CHAINMAIL_LEGGINGS)
                : tier >= 2 ? leather(Material.LEATHER_LEGGINGS, color) : null;
        // Cizmeler hep taraf renginde: agir zirhli askerde de kimin adami oldugu belli olsun.
        ItemStack boots = leather(Material.LEATHER_BOOTS, color);
        boolean shield = troop.category() == TroopCategory.INFANTRY && tier >= 2;
        return new Outfit(helmet, chest, legs, boots, item(weapon(troop)), shield ? item(Material.SHIELD) : null);
    }

    public static ItemStack horseArmor(TroopType troop, Color color) {
        int tier = troop.tier();
        return tier >= 6 ? item(Material.DIAMOND_HORSE_ARMOR)
                : tier >= 4 ? item(Material.IRON_HORSE_ARMOR)
                : leather(Material.LEATHER_HORSE_ARMOR, color);
    }

    public static Horse.Color horseColor(TroopType troop) {
        return troop.tier() >= 5 ? Horse.Color.WHITE : troop.culture().equals("haydut") ? Horse.Color.DARK_BROWN : Horse.Color.BROWN;
    }

    /** Menu simgesi icin de kullanilir. */
    public static Material weapon(TroopType troop) {
        int tier = troop.tier();
        if (troop.category().isRanged()) {
            return troop.culture().equals("valdor") ? Material.CROSSBOW : Material.BOW;
        }
        boolean axes = troop.culture().equals("nordhem") || troop.culture().equals("haydut");
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

    static ItemStack item(Material material) {
        return new ItemStack(material);
    }

    private static ItemStack leather(Material material, Color color) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(color));
        return stack;
    }
}
