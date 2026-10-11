package dev.auxy.warbcraft.core.field;

/** Sahadaki askerlere verilen emir. */
public enum FieldOrder {
    /** Liderin arkasinda formasyonda yurur; lidere ya da kendilerine saldirani vurur. */
    FOLLOW("Takip et", 16),
    /** Emrin verildigi yerde bekler; yalnizca yakina gelen dusmani vurur. */
    HOLD("Burada bekle", 8),
    /** Cevredeki tum dusmanlara saldirir. */
    CHARGE("Hücum", 32);

    private final String displayName;
    private final int engageRange;

    FieldOrder(String displayName, int engageRange) {
        this.displayName = displayName;
        this.engageRange = engageRange;
    }

    public String displayName() {
        return displayName;
    }

    /** Askerin kendiliginden dusman aradigi yaricap (blok). */
    public int engageRange() {
        return engageRange;
    }
}
