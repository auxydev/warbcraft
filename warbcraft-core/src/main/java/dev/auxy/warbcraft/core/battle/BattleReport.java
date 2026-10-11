package dev.auxy.warbcraft.core.battle;

import java.util.Map;

/**
 * Savas sonucu.
 *
 * @param casualties lider kimligi -> (birlik kimligi -> kayip)
 */
public record BattleReport(BattleSide winner, int rounds, Map<String, Map<String, Casualties>> casualties) {

    public BattleReport {
        casualties = Map.copyOf(casualties);
    }

    public int totalCasualties(String leaderId) {
        int total = 0;
        for (Casualties c : casualties.getOrDefault(leaderId, Map.of()).values()) {
            total += c.total();
        }
        return total;
    }
}
