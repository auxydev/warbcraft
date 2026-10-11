package dev.auxy.warbcraft.core.bandit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bir haydut turu (dag haydudu, col akincisi...). Hangi biyomlarda ciktigi ve cetesinin hangi
 * birliklerden kac kisi oldugu veri dosyasindan gelir.
 *
 * @param biomes {@code "*"} iceren tur, baska hicbir turun tutmadigi biyomlarda cikar
 * @param roster birlik kimligi -> cetede bulunacak adet araligi
 */
public record BanditKind(String id, String name, Set<String> biomes, Map<String, Range> roster) {

    public static final String ANY_BIOME = "*";

    public BanditKind {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        biomes = biomes.stream().map(b -> b.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        roster = Collections.unmodifiableMap(new LinkedHashMap<>(roster));
        if (biomes.isEmpty()) {
            throw new IllegalArgumentException(id + ": en az bir biyom gerekli");
        }
        if (roster.isEmpty()) {
            throw new IllegalArgumentException(id + ": cetede en az bir birlik olmali");
        }
        // Kompakt kurucuda alanlar henuz atanmadi; maxSize() yerine parametreden hesaplanir.
        if (roster.values().stream().mapToInt(Range::max).sum() <= 0) {
            throw new IllegalArgumentException(id + ": cete en az bir kisilik olabilmeli");
        }
    }

    public boolean isFallback() {
        return biomes.contains(ANY_BIOME);
    }

    public int maxSize() {
        return roster.values().stream().mapToInt(Range::max).sum();
    }

    /** Kapali adet araligi. */
    public record Range(int min, int max) {
        public Range {
            if (min < 0 || max < min) {
                throw new IllegalArgumentException("Gecersiz aralik: " + min + "-" + max);
            }
        }
    }
}
