package dev.auxy.warbcraft.core.battle;

import dev.auxy.warbcraft.core.Fixtures;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.party.PartyKind;
import dev.auxy.warbcraft.core.troop.TroopCategory;
import dev.auxy.warbcraft.core.troop.TroopTree;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoResolveTest {

    private final TroopTree tree = Fixtures.tree();
    private final AutoResolve resolver = new AutoResolve(tree);

    private static Party party(String leader, PartyKind kind, String troop, int count) {
        Party party = new Party(leader, kind, 1000);
        party.addTroops(troop, count);
        return party;
    }

    @Test
    void strongPartyCrushesBandits() {
        Party knights = party("lord", PartyKind.NPC_LORD, "v_knight", 30);
        Party looters = party("bandits", PartyKind.BANDIT, "looter", 40);
        BattleReport report = resolver.resolve(List.of(knights), List.of(looters), false, new Random(7));
        assertEquals(BattleSide.ATTACKER, report.winner());
        assertTrue(looters.healthyCount() <= 10);
        assertTrue(report.totalCasualties("lord") < report.totalCasualties("bandits"));
    }

    @Test
    void reportMatchesPartyState() {
        Party a = party("a", PartyKind.PLAYER, "v_footman", 50);
        Party b = party("b", PartyKind.NPC_LORD, "v_recruit", 60);
        BattleReport report = resolver.resolve(List.of(a), List.of(b), false, new Random(3));
        for (Party p : List.of(a, b)) {
            String troop = p == a ? "v_footman" : "v_recruit";
            int start = p == a ? 50 : 60;
            Casualties c = report.casualties().getOrDefault(p.leaderId(), Map.of()).getOrDefault(troop, Casualties.NONE);
            assertEquals(start - c.killed(), p.totalCount());
            assertEquals(c.wounded(), p.totalCount() - p.healthyCount());
        }
    }

    @Test
    void sameSeedSameOutcome() {
        BattleReport first = resolver.resolve(List.of(party("a", PartyKind.PLAYER, "v_spearman", 40)),
                List.of(party("b", PartyKind.NPC_LORD, "v_footman", 55)), false, new Random(42));
        BattleReport second = resolver.resolve(List.of(party("a", PartyKind.PLAYER, "v_spearman", 40)),
                List.of(party("b", PartyKind.NPC_LORD, "v_footman", 55)), false, new Random(42));
        assertEquals(first, second);
    }

    @Test
    void wallsFavourDefenders() {
        int siegeDefenderWins = 0;
        int fieldDefenderWins = 0;
        for (int seed = 0; seed < 20; seed++) {
            BattleReport siege = resolver.resolve(List.of(party("a", PartyKind.PLAYER, "v_sergeant", 40)),
                    List.of(party("d", PartyKind.GARRISON, "v_sergeant", 40)), true, new Random(seed));
            BattleReport field = resolver.resolve(List.of(party("a", PartyKind.PLAYER, "v_sergeant", 40)),
                    List.of(party("d", PartyKind.GARRISON, "v_sergeant", 40)), false, new Random(seed));
            siegeDefenderWins += siege.winner() == BattleSide.DEFENDER ? 1 : 0;
            fieldDefenderWins += field.winner() == BattleSide.DEFENDER ? 1 : 0;
        }
        assertTrue(siegeDefenderWins >= 18, "kusatmada savunan " + siegeDefenderWins + "/20");
        assertTrue(siegeDefenderWins > fieldDefenderWins);
    }

    @Test
    void winnersGainExperience() {
        Party winner = party("w", PartyKind.PLAYER, "v_recruit", 60);
        resolver.resolve(List.of(winner), List.of(party("l", PartyKind.BANDIT, "looter", 15)), false, new Random(1));
        assertTrue(winner.roster().get("v_recruit").xp() > 0);
    }

    @Test
    void cavalryIsWeakAgainstWalls() {
        assertTrue(AutoResolve.categoryModifier(TroopCategory.CAVALRY, BattleSide.ATTACKER, true)
                < AutoResolve.categoryModifier(TroopCategory.CAVALRY, BattleSide.ATTACKER, false));
    }

    @Test
    void needsBothSides() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(List.of(), List.of(party("d", PartyKind.BANDIT, "looter", 1)), false, new Random()));
    }
}
