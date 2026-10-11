package dev.auxy.warbcraft.core.npc;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Bir NPC lord ordusunun haritadaki yolculugu: kendi kralliginin yerlesimleri arasinda gider, varinca
 * bir sure konaklar, sonra baska bir yerlesime yola cikar. Yakinda oyuncu yokken bu soyut hareketle
 * ilerler; oyuncu yaklasinca eklenti ayni konumda gercek NPC'leri dogurur.
 */
public final class LordRoute {

    /** Yuruyen bir ordunun hizi (blok/sn); oyuncunun yurume hizinin biraz altinda. */
    public static final double SPEED = 3.0;
    public static final int MIN_REST_SECONDS = 40;
    public static final int MAX_REST_SECONDS = 120;

    /** Haritada bir durak (yerlesim merkezi). */
    public record Stop(String id, double x, double z) {
        public Stop {
            Objects.requireNonNull(id, "id");
        }
    }

    private double x;
    private double z;
    private Stop destination;
    private double restSeconds;

    public LordRoute(Stop start) {
        this.x = start.x();
        this.z = start.z();
        this.destination = start;
    }

    public double x() {
        return x;
    }

    public double z() {
        return z;
    }

    public Stop destination() {
        return destination;
    }

    public boolean resting() {
        return restSeconds > 0;
    }

    /** Gercek NPC'ler yururken konum onlardan alinir. */
    public void moveTo(double newX, double newZ) {
        this.x = newX;
        this.z = newZ;
    }

    /**
     * Zaman ilerler: konaklama suruyorsa sayac azalir, bitince yeni duraga yola cikilir; yoldaysa
     * hedefe dogru {@link #SPEED} ile ilerlenir, varinca konaklama baslar.
     *
     * @param stops kralligin yerlesimleri; bossa ordu yerinde bekler
     */
    public void advance(double seconds, List<Stop> stops, RandomGenerator random) {
        if (seconds <= 0) {
            return;
        }
        if (restSeconds > 0) {
            restSeconds -= seconds;
            if (restSeconds <= 0) {
                destination = nextStop(stops, random);
            }
            return;
        }
        double dx = destination.x() - x;
        double dz = destination.z() - z;
        double distance = Math.hypot(dx, dz);
        double step = SPEED * seconds;
        if (distance <= step) {
            x = destination.x();
            z = destination.z();
            restSeconds = MIN_REST_SECONDS + random.nextInt(MAX_REST_SECONDS - MIN_REST_SECONDS + 1);
            return;
        }
        x += dx / distance * step;
        z += dz / distance * step;
    }

    /** Hedefe kalan mesafe. */
    public double remaining() {
        return Math.hypot(destination.x() - x, destination.z() - z);
    }

    /** Ordu hedefe vardi sayilir (gercek NPC'ler yururken eklenti cagirir). */
    public void arrive(RandomGenerator random) {
        if (restSeconds <= 0) {
            restSeconds = MIN_REST_SECONDS + random.nextInt(MAX_REST_SECONDS - MIN_REST_SECONDS + 1);
        }
    }

    /** Bulunulan durak disinda rastgele bir durak; tek durak varsa o. */
    private Stop nextStop(List<Stop> stops, RandomGenerator random) {
        if (stops.isEmpty()) {
            return destination;
        }
        if (stops.size() == 1) {
            return stops.getFirst();
        }
        Stop next;
        do {
            next = stops.get(random.nextInt(stops.size()));
        } while (next.id().equals(destination.id()));
        return next;
    }
}
