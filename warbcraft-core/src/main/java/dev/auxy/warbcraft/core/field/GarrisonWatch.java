package dev.auxy.warbcraft.core.field;

/**
 * Bir sehir ya da kalenin dunyada gorunen nobetcileri. Garnizonun tamami degil, buyuklugune gore
 * bir kismi gorunur (performans); nobetci olunce garnizondan duser.
 */
public final class GarrisonWatch {

    public static final int MIN_GUARDS = 2;
    public static final int MAX_GUARDS = 8;
    /** Her bu kadar garnizon askeri icin bir nobetci gorunur. */
    public static final int SOLDIERS_PER_GUARD = 15;

    private GarrisonWatch() {
    }

    /** Gorunen nobetci sayisi; garnizon bossa 0, degilse [MIN_GUARDS, MAX_GUARDS] ve garnizondan fazla olamaz. */
    public static int visibleGuards(int garrisonSize) {
        if (garrisonSize <= 0) {
            return 0;
        }
        int guards = Math.clamp(garrisonSize / SOLDIERS_PER_GUARD, MIN_GUARDS, MAX_GUARDS);
        return Math.min(guards, garrisonSize);
    }
}
