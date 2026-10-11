package dev.auxy.warbcraft.core.party;

import dev.auxy.warbcraft.core.Fixtures;
import dev.auxy.warbcraft.core.troop.TroopTree;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyTest {

    private final TroopTree tree = Fixtures.tree();

    @Test
    void addTroopsRespectsSizeLimit() {
        Party party = new Party("p", PartyKind.PLAYER, 10);
        assertEquals(8, party.addTroops("v_recruit", 8));
        assertEquals(2, party.addTroops("v_footman", 5));
        assertEquals(0, party.freeSlots());
        assertEquals(0, party.addTroops("v_recruit", 1));
        assertEquals(10, party.totalCount());
    }

    @Test
    void removeTakesHealthyBeforeWoundedAndCleansUp() {
        Party party = new Party("p", PartyKind.PLAYER, 20);
        party.addTroops("v_recruit", 5);
        party.applyCasualties("v_recruit", 0, 2);
        assertEquals(3, party.roster().get("v_recruit").healthy());
        assertEquals(4, party.removeTroops("v_recruit", 4));
        assertEquals(1, party.roster().get("v_recruit").wounded());
        assertEquals(1, party.removeTroops("v_recruit", 10));
        assertTrue(party.isEmpty());
        assertFalse(party.roster().containsKey("v_recruit"));
        assertEquals(0, party.removeTroops("v_recruit", 1));
    }

    @Test
    void casualtiesKillAndWound() {
        Party party = new Party("p", PartyKind.PLAYER, 20);
        party.addTroops("v_footman", 10);
        party.applyCasualties("v_footman", 3, 4);
        assertEquals(7, party.totalCount());
        assertEquals(3, party.healthyCount());
        assertEquals(4, party.healWounded(10));
        assertEquals(7, party.healthyCount());
    }

    @Test
    void upgradeSpendsStackXp() {
        Party party = new Party("p", PartyKind.PLAYER, 20);
        party.addTroops("v_recruit", 10);
        party.addXp("v_recruit", 60);
        assertEquals(2, party.upgradeableCount(tree, "v_recruit"));
        assertEquals(2, party.upgrade(tree, "v_recruit", "v_footman", 5));
        assertEquals(8, party.count("v_recruit"));
        assertEquals(2, party.count("v_footman"));
        assertEquals(10, party.roster().get("v_recruit").xp());
        assertEquals(10, party.totalCount());
    }

    @Test
    void upgradeRejectsInvalidPath() {
        Party party = new Party("p", PartyKind.PLAYER, 20);
        party.addTroops("v_recruit", 1);
        assertThrows(IllegalArgumentException.class, () -> party.upgrade(tree, "v_recruit", "v_knight", 1));
        assertEquals(0, party.upgradeableCount(tree, "v_crossbow"));
    }

    @Test
    void wageAndStrength() {
        Party party = new Party("p", PartyKind.PLAYER, 20);
        party.addTroops("v_recruit", 4);
        party.addTroops("v_knight", 1);
        party.applyCasualties("v_recruit", 0, 2);
        assertEquals(4 * 1 + 14, party.dailyWage(tree));
        assertEquals(2 * 10 + 60, party.strength(tree));
    }

    @Test
    void moraleIsClamped() {
        Party party = new Party("p", PartyKind.PLAYER, 1);
        party.changeMorale(500);
        assertEquals(Party.MAX_MORALE, party.morale());
        party.changeMorale(-500);
        assertEquals(Party.MIN_MORALE, party.morale());
    }

    @Test
    void copyIsIndependent() {
        Party party = new Party("p", PartyKind.PLAYER, 20);
        party.addTroops("v_recruit", 5);
        Party copy = party.copy();
        copy.removeTroops("v_recruit", 5);
        assertEquals(5, party.count("v_recruit"));
        assertTrue(copy.isEmpty());
    }

    @Test
    void rejectsNonPositiveAmounts() {
        Party party = new Party("p", PartyKind.PLAYER, 20);
        assertThrows(IllegalArgumentException.class, () -> party.addTroops("v_recruit", 0));
        assertThrows(IllegalArgumentException.class, () -> new Party("p", PartyKind.PLAYER, -1));
    }
}
