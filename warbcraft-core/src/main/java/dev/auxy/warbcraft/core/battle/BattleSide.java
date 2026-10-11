package dev.auxy.warbcraft.core.battle;

public enum BattleSide {
    ATTACKER,
    DEFENDER;

    public BattleSide opposite() {
        return this == ATTACKER ? DEFENDER : ATTACKER;
    }
}
