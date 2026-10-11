package dev.auxy.warbcraft.core.npc;

import dev.auxy.warbcraft.core.Fixtures;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.party.PartyKind;
import dev.auxy.warbcraft.core.party.PartyUpkeep;
import dev.auxy.warbcraft.core.troop.TroopTree;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcLordFactoryTest {

    private final TroopTree tree = Fixtures.tree();
    private final NpcLordFactory factory = new NpcLordFactory(tree, NpcBalance.DEFAULT);

    @Test
    void lordPartiesAreSmallerThanPlayerParties() {
        for (int tier = 0; tier <= 6; tier++) {
            assertTrue(factory.lordPartyLimit(tier) < PartyUpkeep.sizeLimit(tier, 0));
        }
        assertEquals(39, factory.lordPartyLimit(3));
    }

    @Test
    void lordPartyIsFilledWithCappedTiers() {
        for (int seed = 0; seed < 20; seed++) {
            Party party = factory.createLordParty("lord", "vlandia", 3, new Random(seed));
            assertEquals(PartyKind.NPC_LORD, party.kind());
            assertEquals(39, party.totalCount());
            for (String troopId : party.roster().keySet()) {
                assertTrue(tree.get(troopId).tier() <= NpcBalance.DEFAULT.maxLordTroopTier(), troopId);
                assertEquals("vlandia", tree.get(troopId).culture());
            }
        }
    }

    @Test
    void garrisonIsScaledDown() {
        Party garrison = factory.createGarrison("pravend", "vlandia", 100, new Random(1));
        assertEquals(PartyKind.GARRISON, garrison.kind());
        assertEquals(70, garrison.totalCount());
    }

    @Test
    void rejectsUnknownCultureAndBadBalance() {
        assertThrows(IllegalArgumentException.class, () -> factory.createLordParty("l", "khuzait", 1, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new NpcBalance(1.5, 4, 0.7, 7, 1));
        assertThrows(IllegalArgumentException.class, () -> new NpcBalance(0.5, 9, 0.7, 7, 1));
    }
}
