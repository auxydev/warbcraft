package dev.auxy.warbcraft.core.bandit;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Dolasan haydut cetelerinin kurallari: hangi biyomda hangi tur, cete kac kisi, ne zaman yeni cete.
 * Dogma yeri ve varliklar eklentidedir; burada yalnizca kararlar verilir.
 */
public final class BanditSpawner {

    private BanditSpawner() {
    }

    /**
     * @param intervalSeconds  bir oyuncunun cevresinde iki cete denemesi arasi saniye
     * @param chance           her denemede cete cikma olasiligi [0,1]
     * @param maxGangsNearby   bir oyuncunun cevresinde ayni anda en fazla cete
     * @param minDistance      oyuncuya en yakin dogma uzakligi (blok)
     * @param maxDistance      oyuncuya en uzak dogma uzakligi (blok)
     */
    public record Settings(int intervalSeconds, double chance, int maxGangsNearby, int minDistance, int maxDistance) {
        public Settings {
            if (intervalSeconds <= 0) {
                throw new IllegalArgumentException("Deneme araligi pozitif olmali");
            }
            if (chance < 0 || chance > 1) {
                throw new IllegalArgumentException("Olasilik 0 ile 1 arasinda olmali");
            }
            if (maxGangsNearby < 0) {
                throw new IllegalArgumentException("Cete siniri negatif olamaz");
            }
            if (minDistance <= 0 || maxDistance < minDistance) {
                throw new IllegalArgumentException("Dogma uzakligi araligi gecersiz");
            }
        }
    }

    /** Biyoma uyan tur; hicbiri uymazsa {@code "*"} turu. Biyom "desert" ya da "minecraft:desert" olabilir. */
    public static Optional<BanditKind> kindFor(Collection<BanditKind> kinds, String biome) {
        String key = biome.toLowerCase(Locale.ROOT);
        String plain = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        BanditKind fallback = null;
        for (BanditKind kind : kinds) {
            if (kind.biomes().contains(plain) || kind.biomes().contains(key)) {
                return Optional.of(kind);
            }
            if (fallback == null && kind.isFallback()) {
                fallback = kind;
            }
        }
        return Optional.ofNullable(fallback);
    }

    /** Cetenin bilesimi; her birlik kendi araliginda rastgele, toplam en az bir kisi. */
    public static Map<String, Integer> rollGang(BanditKind kind, RandomGenerator random) {
        Map<String, Integer> gang = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, BanditKind.Range> entry : kind.roster().entrySet()) {
            BanditKind.Range range = entry.getValue();
            int count = range.min() + random.nextInt(range.max() - range.min() + 1);
            if (count > 0) {
                gang.put(entry.getKey(), count);
                total += count;
            }
        }
        if (total == 0) {
            // Hepsi 0 geldiyse en az bir kisi: en genis araliga sahip birlikten.
            String widest = kind.roster().entrySet().stream()
                    .max(Map.Entry.comparingByValue((a, b) -> Integer.compare(a.max(), b.max())))
                    .orElseThrow().getKey();
            gang.put(widest, 1);
        }
        return gang;
    }

    /**
     * Bir oyuncunun cevresinde yeni cete cikmali mi?
     *
     * @param gangsNearby oyuncunun cevresindeki mevcut cete sayisi
     * @param roll        [0,1) rastgele sayi
     */
    public static boolean shouldSpawn(Settings settings, int gangsNearby, double roll) {
        return gangsNearby < settings.maxGangsNearby() && roll < settings.chance();
    }
}
