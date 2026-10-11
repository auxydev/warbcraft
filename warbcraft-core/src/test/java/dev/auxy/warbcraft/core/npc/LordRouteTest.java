package dev.auxy.warbcraft.core.npc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LordRouteTest {

    private static final LordRoute.Stop A = new LordRoute.Stop("a", 0, 0);
    private static final LordRoute.Stop B = new LordRoute.Stop("b", 300, 0);
    private static final LordRoute.Stop C = new LordRoute.Stop("c", 0, 300);

    @Test
    void travelsAtSpeedAndRestsOnArrival() {
        LordRoute route = new LordRoute(A);
        Random random = new Random(3);
        // Baslangicta duraktadir: once konaklar, sonra yeni duraga cikar.
        route.advance(1, List.of(A, B), random);
        assertTrue(route.resting());
        route.advance(LordRoute.MAX_REST_SECONDS + 1, List.of(A, B), random);
        assertFalse(route.resting());
        assertEquals("b", route.destination().id());
        route.advance(10, List.of(A, B), random);
        assertEquals(10 * LordRoute.SPEED, route.x(), 1e-9);
        route.advance(1000, List.of(A, B), random);
        assertEquals(300, route.x(), 1e-9);
        assertTrue(route.resting());
    }

    @Test
    void nextStopDiffersFromCurrent() {
        Random random = new Random(11);
        for (int i = 0; i < 50; i++) {
            LordRoute route = new LordRoute(A);
            route.advance(1, List.of(A, B, C), random);
            route.advance(LordRoute.MAX_REST_SECONDS + 1, List.of(A, B, C), random);
            assertNotEquals("a", route.destination().id());
        }
    }

    @Test
    void realMovementCanOverridePosition() {
        LordRoute route = new LordRoute(A);
        route.moveTo(12, 34);
        assertEquals(12, route.x());
        assertEquals(34, route.z());
        assertEquals(Math.hypot(12, 34), route.remaining(), 1e-9);
    }
}
