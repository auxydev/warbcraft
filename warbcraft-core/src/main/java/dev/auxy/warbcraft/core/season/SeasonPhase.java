package dev.auxy.warbcraft.core.season;

public enum SeasonPhase {
    NOT_STARTED("Baslamadi", false),
    /** Kurulus donemi: savas ilan edilemez; ticaret, asker toplama, gorevler. */
    PREPARATION("Hazirlik", false),
    /** Savaslar serbest; kusatmalar yalnizca savas pencerelerinde. */
    CAMPAIGN("Sefer", true),
    /** Son hafta: un kazanimi artar, sezon puanlari kesinlesir. */
    FINAL("Son Muharebeler", true),
    ENDED("Bitti", false);

    private final String displayName;
    private final boolean warsAllowed;

    SeasonPhase(String displayName, boolean warsAllowed) {
        this.displayName = displayName;
        this.warsAllowed = warsAllowed;
    }

    public String displayName() {
        return displayName;
    }

    public boolean warsAllowed() {
        return warsAllowed;
    }
}
