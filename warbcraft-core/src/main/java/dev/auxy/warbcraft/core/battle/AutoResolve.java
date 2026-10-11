package dev.auxy.warbcraft.core.battle;

import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.party.TroopStack;
import dev.auxy.warbcraft.core.troop.TroopCategory;
import dev.auxy.warbcraft.core.troop.TroopTree;
import dev.auxy.warbcraft.core.troop.TroopType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Oyuncu katilmayan savaslari (NPC'ler arasi, haydut baskinlari, oyuncu "otomatik coz" secerse)
 * tur tur simule eder. Partiler yerinde degistirilir: olenler cikarilir, yaralananlar yarali olur,
 * kazanan tarafa deneyim verilir.
 */
public final class AutoResolve {

    static final int MAX_ROUNDS = 40;
    /** Her turda, ortalama gucte bir asker basina dusman kaybi. */
    static final double LETHALITY = 0.08;
    /** Kayiplarin olme olasiligi (gerisi yaralanir). */
    static final double KILL_CHANCE = 0.35;
    /** Baslangictaki saglikli askerlerin bu oranina dusen taraf bozguna ugrar. */
    static final double ROUT_RATIO = 0.25;
    static final int XP_PER_ENEMY_CASUALTY = 6;

    private final TroopTree tree;

    public AutoResolve(TroopTree tree) {
        this.tree = tree;
    }

    public BattleReport resolve(List<Party> attackers, List<Party> defenders, boolean siege, Random random) {
        if (attackers.isEmpty() || defenders.isEmpty()) {
            throw new IllegalArgumentException("Her iki tarafta da en az bir parti olmali");
        }
        Map<String, Map<String, Casualties>> casualties = new LinkedHashMap<>();
        int attackerStart = healthy(attackers);
        int defenderStart = healthy(defenders);
        int[] inflicted = new int[2];
        int round = 0;
        BattleSide winner = null;
        while (winner == null && round < MAX_ROUNDS) {
            round++;
            double attackerPower = effectivePower(attackers, BattleSide.ATTACKER, siege);
            double defenderPower = effectivePower(defenders, BattleSide.DEFENDER, siege);
            int hitsOnDefenders = hits(attackerPower, defenders, random);
            int hitsOnAttackers = hits(defenderPower, attackers, random);
            inflicted[BattleSide.ATTACKER.ordinal()] += strike(defenders, hitsOnDefenders, casualties, random);
            inflicted[BattleSide.DEFENDER.ordinal()] += strike(attackers, hitsOnAttackers, casualties, random);
            boolean attackersBroken = healthy(attackers) <= attackerStart * ROUT_RATIO;
            boolean defendersBroken = healthy(defenders) <= defenderStart * ROUT_RATIO;
            if (attackersBroken && defendersBroken) {
                winner = ratio(defenders, defenderStart) > ratio(attackers, attackerStart)
                        ? BattleSide.DEFENDER : BattleSide.ATTACKER;
            } else if (attackersBroken) {
                winner = BattleSide.DEFENDER;
            } else if (defendersBroken) {
                winner = BattleSide.ATTACKER;
            }
        }
        if (winner == null) {
            // Tur siniri doldu: kusatmada savunan dayanmis sayilir, meydan savasinda oran belirler.
            winner = siege || ratio(defenders, defenderStart) >= ratio(attackers, attackerStart)
                    ? BattleSide.DEFENDER : BattleSide.ATTACKER;
        }
        List<Party> winners = winner == BattleSide.ATTACKER ? attackers : defenders;
        grantXp(winners, inflicted[winner.ordinal()] * XP_PER_ENEMY_CASUALTY);
        return new BattleReport(winner, round, casualties);
    }

    private double effectivePower(List<Party> parties, BattleSide side, boolean siege) {
        double total = 0;
        for (Party party : parties) {
            double morale = 0.75 + party.morale() / 200.0;
            for (Map.Entry<String, TroopStack> entry : party.roster().entrySet()) {
                TroopType type = tree.get(entry.getKey());
                total += type.power() * entry.getValue().healthy() * categoryModifier(type.category(), side, siege) * morale;
            }
        }
        if (siege && side == BattleSide.DEFENDER) {
            total *= 1.5;
        }
        return total;
    }

    static double categoryModifier(TroopCategory category, BattleSide side, boolean siege) {
        if (siege) {
            if (category.isMounted()) {
                return 0.6;
            }
            if (category.isRanged() && side == BattleSide.DEFENDER) {
                return 1.25;
            }
            return 1.0;
        }
        return switch (category) {
            case CAVALRY -> 1.2;
            case HORSE_ARCHER -> 1.15;
            default -> 1.0;
        };
    }

    private int hits(double power, List<Party> targets, Random random) {
        int targetHealthy = healthy(targets);
        if (power <= 0 || targetHealthy == 0) {
            return 0;
        }
        double avgTargetPower = averagePower(targets);
        double expected = power / avgTargetPower * LETHALITY * (0.85 + random.nextDouble() * 0.3);
        return Math.min(targetHealthy, Math.max(1, (int) Math.round(expected)));
    }

    private int strike(List<Party> targets, int hits, Map<String, Map<String, Casualties>> casualties, Random random) {
        int dealt = 0;
        for (int i = 0; i < hits; i++) {
            int remaining = healthy(targets);
            if (remaining == 0) {
                break;
            }
            int pick = random.nextInt(remaining);
            outer:
            for (Party party : targets) {
                for (Map.Entry<String, TroopStack> entry : new ArrayList<>(party.roster().entrySet())) {
                    int healthy = entry.getValue().healthy();
                    if (pick < healthy) {
                        boolean killed = random.nextDouble() < KILL_CHANCE;
                        party.applyCasualties(entry.getKey(), killed ? 1 : 0, killed ? 0 : 1);
                        casualties.computeIfAbsent(party.leaderId(), k -> new HashMap<>())
                                .merge(entry.getKey(), new Casualties(killed ? 1 : 0, killed ? 0 : 1), Casualties::plus);
                        dealt++;
                        break outer;
                    }
                    pick -= healthy;
                }
            }
        }
        return dealt;
    }

    private void grantXp(List<Party> winners, int totalXp) {
        int survivors = healthy(winners);
        if (survivors == 0 || totalXp == 0) {
            return;
        }
        int perSoldier = Math.max(1, totalXp / survivors);
        for (Party party : winners) {
            for (Map.Entry<String, TroopStack> entry : new ArrayList<>(party.roster().entrySet())) {
                int healthy = entry.getValue().healthy();
                if (healthy > 0) {
                    party.addXp(entry.getKey(), perSoldier * healthy);
                }
            }
        }
    }

    private double averagePower(List<Party> parties) {
        long power = 0;
        int count = 0;
        for (Party party : parties) {
            for (Map.Entry<String, TroopStack> entry : party.roster().entrySet()) {
                int healthy = entry.getValue().healthy();
                power += (long) tree.get(entry.getKey()).power() * healthy;
                count += healthy;
            }
        }
        return count == 0 ? 1 : (double) power / count;
    }

    private static int healthy(List<Party> parties) {
        int total = 0;
        for (Party party : parties) {
            total += party.healthyCount();
        }
        return total;
    }

    private static double ratio(List<Party> parties, int start) {
        return start == 0 ? 0 : (double) healthy(parties) / start;
    }
}
