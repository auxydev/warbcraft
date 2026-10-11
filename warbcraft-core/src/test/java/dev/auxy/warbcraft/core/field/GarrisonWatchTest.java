package dev.auxy.warbcraft.core.field;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GarrisonWatchTest {

    @Test
    void scalesWithGarrisonWithinBounds() {
        assertEquals(0, GarrisonWatch.visibleGuards(0));
        assertEquals(1, GarrisonWatch.visibleGuards(1));
        assertEquals(GarrisonWatch.MIN_GUARDS, GarrisonWatch.visibleGuards(20));
        assertEquals(5, GarrisonWatch.visibleGuards(84));
        assertEquals(GarrisonWatch.MAX_GUARDS, GarrisonWatch.visibleGuards(500));
    }
}
