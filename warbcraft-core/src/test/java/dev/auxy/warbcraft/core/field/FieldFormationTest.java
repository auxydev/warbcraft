package dev.auxy.warbcraft.core.field;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldFormationTest {

    @Test
    void firstRowIsCenteredBehindLeader() {
        FieldFormation line = FieldFormation.LINE;
        assertEquals(FieldFormation.LEADER_GAP, line.slot(0).back());
        assertEquals(-2 * line.spacing(), line.slot(0).side(), 1e-9);
        assertEquals(0, line.slot(2).side(), 1e-9);
        assertEquals(2 * line.spacing(), line.slot(4).side(), 1e-9);
    }

    @Test
    void nextRowStepsBack() {
        FieldFormation column = FieldFormation.COLUMN;
        assertEquals(column.slot(0).back() + column.spacing(), column.slot(2).back(), 1e-9);
    }

    @Test
    void slotsNeverOverlap() {
        for (FieldFormation formation : FieldFormation.values()) {
            Set<FieldFormation.Slot> seen = new HashSet<>();
            for (int i = 0; i < 40; i++) {
                assertTrue(seen.add(formation.slot(i)), formation + " " + i);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> FieldFormation.LINE.slot(-1));
    }

    @Test
    void nextCyclesThroughAll() {
        assertEquals(FieldFormation.COLUMN, FieldFormation.LINE.next());
        assertEquals(FieldFormation.LINE, FieldFormation.LOOSE.next());
    }
}
