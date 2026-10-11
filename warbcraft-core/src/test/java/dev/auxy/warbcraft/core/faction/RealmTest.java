package dev.auxy.warbcraft.core.faction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RealmTest {

    private Realm realm;

    @BeforeEach
    void setUp() {
        realm = new Realm();
        realm.addKingdom(new Kingdom("vlandia", "Vlandia", "vlandia", "king_derthert", true));
        realm.addKingdom(new Kingdom("p1", "Oyuncu Kralligi", "vlandia", "uuid-1", false));
        Clan npcClan = new Clan("dey_meroc", "dey Meroc", "lord_meroc", true);
        npcClan.setKingdomId("vlandia");
        realm.addClan(npcClan);
        Clan playerClan = new Clan("wolves", "Kurtlar", "uuid-1", false);
        playerClan.setKingdomId("p1");
        realm.addClan(playerClan);
        realm.addSettlement(new Settlement("pravend", "Pravend", SettlementType.TOWN, "vlandia", "dey_meroc", null, 5000));
        realm.addSettlement(new Settlement("jaculan", "Jaculan", SettlementType.CASTLE, "vlandia", "dey_meroc", null, 2000));
        realm.addSettlement(new Settlement("ryibelet", "Ryibelet", SettlementType.VILLAGE, "vlandia", "dey_meroc", "pravend", 800));
        realm.addSettlement(new Settlement("ilvan", "Ilvan", SettlementType.VILLAGE, "vlandia", "dey_meroc", "jaculan", 800));
    }

    @Test
    void capturingTownTransfersItsVillagesOnly() {
        realm.transferSettlement("pravend", "wolves");
        assertEquals("wolves", realm.settlement("pravend").ownerClanId());
        assertEquals("wolves", realm.settlement("ryibelet").ownerClanId());
        assertEquals("dey_meroc", realm.settlement("jaculan").ownerClanId());
        assertEquals("dey_meroc", realm.settlement("ilvan").ownerClanId());
        assertEquals(List.of("pravend", "ryibelet"),
                realm.settlementsOfKingdom("p1").stream().map(Settlement::id).toList());
        assertEquals(Optional.of(realm.kingdom("p1")), realm.kingdomOf(realm.settlement("ryibelet")));
    }

    @Test
    void membershipLookup() {
        assertEquals("wolves", realm.clanOfMember("uuid-1").orElseThrow().id());
        assertEquals(Optional.empty(), realm.clanOfMember("nobody"));
    }

    @Test
    void validatesReferences() {
        assertThrows(IllegalArgumentException.class, () -> realm.addSettlement(
                new Settlement("x", "X", SettlementType.VILLAGE, "vlandia", "dey_meroc", "ryibelet", 0)));
        assertThrows(IllegalArgumentException.class, () -> realm.addSettlement(
                new Settlement("y", "Y", SettlementType.TOWN, "vlandia", "ghost_clan", null, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Settlement("z", "Z", SettlementType.VILLAGE, "vlandia", "dey_meroc", null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Settlement("z", "Z", SettlementType.TOWN, "vlandia", "dey_meroc", "pravend", 0));
        assertThrows(IllegalArgumentException.class, () -> realm.addKingdom(
                new Kingdom("vlandia", "Kopya", "vlandia", "x", true)));
    }

    @Test
    void prosperityIsClamped() {
        Settlement town = realm.settlement("pravend");
        town.changeProsperity(1_000_000);
        assertEquals(Settlement.MAX_PROSPERITY, town.prosperity());
        town.changeProsperity(-1_000_000);
        assertEquals(0, town.prosperity());
    }

    @Test
    void clanTierFollowsRenown() {
        Clan clan = new Clan("c", "C", "leader", false);
        assertEquals(0, clan.tier());
        clan.addRenown(50);
        assertEquals(1, clan.tier());
        clan.addRenown(849);
        assertEquals(3, clan.tier());
        clan.addRenown(100_000);
        assertEquals(Clan.MAX_TIER, clan.tier());
        assertThrows(IllegalArgumentException.class, () -> clan.removeMember("leader"));
        assertThrows(IllegalArgumentException.class, () -> clan.setLeader("stranger"));
    }
}
