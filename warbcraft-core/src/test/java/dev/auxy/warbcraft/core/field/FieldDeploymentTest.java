package dev.auxy.warbcraft.core.field;

import dev.auxy.warbcraft.core.Fixtures;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.party.PartyKind;
import dev.auxy.warbcraft.core.troop.TroopTree;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldDeploymentTest {

    private final TroopTree tree = Fixtures.tree();

    @Test
    void highestTierGoesFirstUntilCap() {
        Party party = new Party("p", PartyKind.PLAYER, 100);
        party.addTroops("v_recruit", 20);
        party.addTroops("v_knight", 3);
        party.addTroops("v_spearman", 4);
        Map<String, Integer> field = FieldDeployment.select(party, tree, 10);
        assertEquals(List.of("v_knight", "v_spearman", "v_recruit"), List.copyOf(field.keySet()));
        assertEquals(3, field.get("v_knight"));
        assertEquals(4, field.get("v_spearman"));
        assertEquals(3, field.get("v_recruit"));
    }

    @Test
    void woundedStayInReserve() {
        Party party = new Party("p", PartyKind.PLAYER, 100);
        party.addTroops("v_footman", 5);
        party.applyCasualties("v_footman", 0, 5);
        party.addTroops("v_recruit", 2);
        Map<String, Integer> field = FieldDeployment.select(party, tree, 10);
        assertFalse(field.containsKey("v_footman"));
        assertEquals(2, field.get("v_recruit"));
    }

    @Test
    void sameTierPrefersLargerStack() {
        Party party = new Party("p", PartyKind.PLAYER, 100);
        party.addTroops("v_spearman", 2);
        party.addTroops("v_crossbow", 6);
        assertEquals(List.of("v_crossbow", "v_spearman"), List.copyOf(FieldDeployment.select(party, tree, 20).keySet()));
    }

    @Test
    void zeroCapDeploysNobody() {
        Party party = new Party("p", PartyKind.PLAYER, 100);
        party.addTroops("v_recruit", 5);
        assertTrue(FieldDeployment.select(party, tree, 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> FieldDeployment.select(party, tree, -1));
    }

    @Test
    void statsGrowWithTier() {
        FieldDeployment.Stats recruit = FieldDeployment.stats(tree.get("v_recruit"));
        FieldDeployment.Stats knight = FieldDeployment.stats(tree.get("v_knight"));
        assertTrue(knight.maxHealth() > recruit.maxHealth());
        assertTrue(knight.attackDamage() > recruit.attackDamage());
        assertEquals(0, recruit.shootCooldownTicks());
        assertEquals(0, recruit.arrowDamage());
        FieldDeployment.Stats crossbow = FieldDeployment.stats(tree.get("v_crossbow"));
        assertTrue(crossbow.shootCooldownTicks() > 0);
        assertTrue(crossbow.arrowDamage() > 0 && crossbow.arrowDamage() < crossbow.attackDamage());
    }

    @Test
    void fallsAreMostlyWounds() {
        assertTrue(FieldDeployment.fallIsFatal(0.0));
        assertFalse(FieldDeployment.fallIsFatal(FieldDeployment.DEATH_CHANCE));
        assertFalse(FieldDeployment.fallIsFatal(0.99));
    }

    @Test
    void killXpScalesWithVictim() {
        assertEquals(1, FieldDeployment.killXp(1));
        assertEquals(10, FieldDeployment.killXp(20));
    }
}
