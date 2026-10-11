package dev.auxy.warbcraft.core.party;

import dev.auxy.warbcraft.core.Fixtures;
import dev.auxy.warbcraft.core.economy.Purse;
import dev.auxy.warbcraft.core.troop.TroopTree;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyUpkeepTest {

    private final TroopTree tree = Fixtures.tree();

    @Test
    void paysWagesAndRecoversMorale() {
        Party party = new Party("p", PartyKind.PLAYER, 50);
        party.addTroops("v_footman", 10);
        party.setMorale(40);
        Purse purse = new Purse(100);
        PartyUpkeep.Result result = PartyUpkeep.dailyTick(party, tree, purse);
        assertTrue(result.paidInFull());
        assertEquals(20, result.wagePaid());
        assertEquals(80, purse.balance());
        assertEquals(42, party.morale());
    }

    @Test
    void unpaidWagesDropMoraleWithoutTakingMoney() {
        Party party = new Party("p", PartyKind.PLAYER, 50);
        party.addTroops("v_footman", 10);
        Purse purse = new Purse(5);
        PartyUpkeep.Result result = PartyUpkeep.dailyTick(party, tree, purse);
        assertFalse(result.paidInFull());
        assertEquals(5, purse.balance());
        assertEquals(Party.DEFAULT_MORALE - PartyUpkeep.UNPAID_MORALE_LOSS, party.morale());
        assertEquals(0, result.deserters());
    }

    @Test
    void lowMoraleCausesDesertionFromLowestTier() {
        Party party = new Party("p", PartyKind.PLAYER, 50);
        party.addTroops("v_knight", 5);
        party.addTroops("v_recruit", 15);
        party.setMorale(25);
        PartyUpkeep.Result result = PartyUpkeep.dailyTick(party, tree, new Purse(0));
        assertEquals(2, result.deserters());
        assertEquals(13, party.count("v_recruit"));
        assertEquals(5, party.count("v_knight"));
    }

    @Test
    void sizeLimitGrowsWithTierAndLeadership() {
        assertEquals(20, PartyUpkeep.sizeLimit(0, 0));
        assertEquals(20 + 45 + 10, PartyUpkeep.sizeLimit(3, 50));
    }
}
