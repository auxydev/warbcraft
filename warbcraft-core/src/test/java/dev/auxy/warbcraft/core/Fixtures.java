package dev.auxy.warbcraft.core;

import dev.auxy.warbcraft.core.troop.TroopCategory;
import dev.auxy.warbcraft.core.troop.TroopTree;
import dev.auxy.warbcraft.core.troop.TroopType;

import java.util.List;

public final class Fixtures {

    private Fixtures() {
    }

    public static TroopType troop(String id, String culture, int tier, TroopCategory category, int power, int wage,
                                  int upgradeXp, String... upgradesTo) {
        return new TroopType(id, id, culture, tier, category, power, wage, upgradeXp, List.of(upgradesTo));
    }

    /** Kucuk bir Vlandia agaci: acemi -> piyade -> (agir piyade | arbaletci) -> ... -> sovalye (6). */
    public static TroopTree tree() {
        return TroopTree.of(List.of(
                troop("v_recruit", "vlandia", 1, TroopCategory.INFANTRY, 10, 1, 25, "v_footman"),
                troop("v_footman", "vlandia", 2, TroopCategory.INFANTRY, 16, 2, 50, "v_spearman", "v_crossbow"),
                troop("v_spearman", "vlandia", 3, TroopCategory.INFANTRY, 24, 4, 100, "v_sergeant"),
                troop("v_crossbow", "vlandia", 3, TroopCategory.ARCHER, 24, 4, 0),
                troop("v_sergeant", "vlandia", 4, TroopCategory.INFANTRY, 34, 6, 200, "v_champion"),
                troop("v_champion", "vlandia", 5, TroopCategory.CAVALRY, 46, 9, 400, "v_knight"),
                troop("v_knight", "vlandia", 6, TroopCategory.CAVALRY, 60, 14, 0),
                troop("looter", "bandit", 1, TroopCategory.INFANTRY, 7, 0, 0)));
    }
}
