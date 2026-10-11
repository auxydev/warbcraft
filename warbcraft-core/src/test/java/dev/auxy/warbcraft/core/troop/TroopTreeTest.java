package dev.auxy.warbcraft.core.troop;

import dev.auxy.warbcraft.core.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.auxy.warbcraft.core.Fixtures.troop;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TroopTreeTest {

    @Test
    void loadsValidTree() {
        TroopTree tree = Fixtures.tree();
        assertEquals(8, tree.size());
        assertEquals(List.of("v_spearman", "v_crossbow"), tree.get("v_footman").upgradesTo());
        assertTrue(tree.find("missing").isEmpty());
    }

    @Test
    void recruitsAreTierOneOfCulture() {
        assertEquals(List.of("v_recruit"), Fixtures.tree().recruits("vlandia").stream().map(TroopType::id).toList());
    }

    @Test
    void rejectsUnknownUpgradeTarget() {
        assertThrows(IllegalArgumentException.class, () -> TroopTree.of(List.of(
                troop("a", "c", 1, TroopCategory.INFANTRY, 1, 1, 10, "ghost"))));
    }

    @Test
    void rejectsCrossCultureUpgrade() {
        assertThrows(IllegalArgumentException.class, () -> TroopTree.of(List.of(
                troop("a", "c1", 1, TroopCategory.INFANTRY, 1, 1, 10, "b"),
                troop("b", "c2", 2, TroopCategory.INFANTRY, 1, 1, 0))));
    }

    @Test
    void rejectsTierSkipAndCycles() {
        assertThrows(IllegalArgumentException.class, () -> TroopTree.of(List.of(
                troop("a", "c", 1, TroopCategory.INFANTRY, 1, 1, 10, "b"),
                troop("b", "c", 3, TroopCategory.INFANTRY, 1, 1, 0))));
        assertThrows(IllegalArgumentException.class, () -> TroopTree.of(List.of(
                troop("a", "c", 2, TroopCategory.INFANTRY, 1, 1, 10, "b"),
                troop("b", "c", 2, TroopCategory.INFANTRY, 1, 1, 10, "a"))));
    }

    @Test
    void rejectsDuplicates() {
        assertThrows(IllegalArgumentException.class, () -> TroopTree.of(List.of(
                troop("a", "c", 1, TroopCategory.INFANTRY, 1, 1, 0),
                troop("a", "c", 1, TroopCategory.INFANTRY, 1, 1, 0))));
    }

    @Test
    void troopTypeValidation() {
        assertThrows(IllegalArgumentException.class, () -> troop("a", "c", 7, TroopCategory.INFANTRY, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> troop("a", "c", 1, TroopCategory.INFANTRY, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> troop("a", "c", 1, TroopCategory.INFANTRY, 1, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> troop("a", "c", 1, TroopCategory.INFANTRY, 1, 1, 0, "b"));
    }
}
