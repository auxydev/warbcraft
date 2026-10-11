package dev.auxy.warbcraft.core.bandit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanditSpawnerTest {

    private static final BanditKind DESERT = new BanditKind("col", "Çöl Akıncısı", Set.of("desert", "BADLANDS"),
            Map.of("raider", new BanditKind.Range(2, 4)));
    private static final BanditKind LOOTER = new BanditKind("yagmaci", "Yağmacı", Set.of(BanditKind.ANY_BIOME),
            Map.of("looter", new BanditKind.Range(3, 5)));
    private static final BanditSpawner.Settings SETTINGS = new BanditSpawner.Settings(30, 0.5, 2, 32, 56);

    @Test
    void biomeChoosesKindAndFallsBack() {
        List<BanditKind> kinds = List.of(LOOTER, DESERT);
        assertEquals("col", BanditSpawner.kindFor(kinds, "minecraft:desert").orElseThrow().id());
        assertEquals("col", BanditSpawner.kindFor(kinds, "badlands").orElseThrow().id());
        assertEquals("yagmaci", BanditSpawner.kindFor(kinds, "minecraft:swamp").orElseThrow().id());
        assertTrue(BanditSpawner.kindFor(List.of(DESERT), "swamp").isEmpty());
    }

    @Test
    void gangStaysWithinRanges() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            int size = BanditSpawner.rollGang(DESERT, random).get("raider");
            assertTrue(size >= 2 && size <= 4, "boyut " + size);
        }
    }

    @Test
    void gangNeverEmpty() {
        BanditKind optional = new BanditKind("x", "X", Set.of("plains"),
                Map.of("a", new BanditKind.Range(0, 1), "b", new BanditKind.Range(0, 2)));
        Random random = new Random(1);
        for (int i = 0; i < 200; i++) {
            int total = BanditSpawner.rollGang(optional, random).values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(total >= 1);
        }
    }

    @Test
    void spawnRespectsCapAndChance() {
        assertTrue(BanditSpawner.shouldSpawn(SETTINGS, 0, 0.1));
        assertFalse(BanditSpawner.shouldSpawn(SETTINGS, 0, 0.9));
        assertFalse(BanditSpawner.shouldSpawn(SETTINGS, 2, 0.0));
    }

    @Test
    void invalidDataRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BanditKind("e", "E", Set.of(), Map.of("a", new BanditKind.Range(1, 1))));
        assertThrows(IllegalArgumentException.class, () -> new BanditKind("e", "E", Set.of("x"), Map.of("a", new BanditKind.Range(0, 0))));
        assertThrows(IllegalArgumentException.class, () -> new BanditKind.Range(3, 2));
        assertThrows(IllegalArgumentException.class, () -> new BanditSpawner.Settings(30, 1.5, 2, 32, 56));
        assertThrows(IllegalArgumentException.class, () -> new BanditSpawner.Settings(30, 0.5, 2, 40, 20));
        assertEquals(6, new BanditKind("e", "E", Set.of("x"), Map.of("a", new BanditKind.Range(1, 2),
                "b", new BanditKind.Range(0, 4))).maxSize());
    }
}
