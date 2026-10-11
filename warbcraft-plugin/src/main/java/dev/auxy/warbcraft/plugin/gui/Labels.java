package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.troop.TroopCategory;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Material;

/** Menulerde ortak Turkce adlar ve simgeler. */
final class Labels {

    private Labels() {
    }

    static String settlement(Settlement settlement) {
        return Msg.esc(settlement.name()) + " <dark_gray>(" + type(settlement) + ")</dark_gray>";
    }

    static String type(Settlement settlement) {
        return switch (settlement.type()) {
            case TOWN -> "şehir";
            case CASTLE -> "kale";
            case VILLAGE -> "köy";
        };
    }

    static Material typeIcon(Settlement settlement) {
        return switch (settlement.type()) {
            case TOWN -> Material.BELL;
            case CASTLE -> Material.STONE_BRICKS;
            case VILLAGE -> Material.HAY_BLOCK;
        };
    }

    static String category(TroopCategory category) {
        return switch (category) {
            case INFANTRY -> "Piyade";
            case ARCHER -> "Okçu";
            case CAVALRY -> "Süvari";
            case HORSE_ARCHER -> "Atlı okçu";
        };
    }

    static Material cultureBanner(String culture) {
        return switch (culture) {
            case "valdor" -> Material.BLUE_BANNER;
            case "nordhem" -> Material.LIGHT_GRAY_BANNER;
            case "aurelya" -> Material.PURPLE_BANNER;
            case "karahan" -> Material.YELLOW_BANNER;
            default -> Material.WHITE_BANNER;
        };
    }
}
