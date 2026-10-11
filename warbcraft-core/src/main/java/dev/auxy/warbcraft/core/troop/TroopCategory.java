package dev.auxy.warbcraft.core.troop;

/** Formasyon komutlari ve savas hesaplari icin birlik sinifi. */
public enum TroopCategory {
    INFANTRY,
    ARCHER,
    CAVALRY,
    HORSE_ARCHER;

    public boolean isMounted() {
        return this == CAVALRY || this == HORSE_ARCHER;
    }

    public boolean isRanged() {
        return this == ARCHER || this == HORSE_ARCHER;
    }
}
