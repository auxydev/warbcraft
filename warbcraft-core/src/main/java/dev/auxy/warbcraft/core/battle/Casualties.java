package dev.auxy.warbcraft.core.battle;

/** Bir birlik turunun bir savastaki kayiplari. */
public record Casualties(int killed, int wounded) {

    public static final Casualties NONE = new Casualties(0, 0);

    public Casualties plus(Casualties other) {
        return new Casualties(killed + other.killed, wounded + other.wounded);
    }

    public int total() {
        return killed + wounded;
    }
}
