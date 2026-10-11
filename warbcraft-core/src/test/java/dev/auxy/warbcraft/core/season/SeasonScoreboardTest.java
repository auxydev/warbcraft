package dev.auxy.warbcraft.core.season;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Realm;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.faction.SettlementType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeasonScoreboardTest {

    @Test
    void ranksOnlyPlayerKingdomsBySettlementsAndRenown() {
        Realm realm = new Realm();
        realm.addKingdom(new Kingdom("vlandia", "Vlandia", "vlandia", "k", true));
        realm.addKingdom(new Kingdom("p1", "P1", "vlandia", "u1", false));
        realm.addKingdom(new Kingdom("p2", "P2", "vlandia", "u2", false));
        Clan npc = new Clan("npc", "NPC", "lord", true);
        npc.setKingdomId("vlandia");
        realm.addClan(npc);
        Clan c1 = new Clan("c1", "C1", "u1", false);
        c1.setKingdomId("p1");
        c1.addRenown(120);
        realm.addClan(c1);
        Clan c2 = new Clan("c2", "C2", "u2", false);
        c2.setKingdomId("p2");
        c2.addRenown(500);
        realm.addClan(c2);
        realm.addSettlement(new Settlement("t", "T", SettlementType.TOWN, "vlandia", "c1", null, 0));
        realm.addSettlement(new Settlement("v", "V", SettlementType.VILLAGE, "vlandia", "c1", "t", 0));
        realm.addSettlement(new Settlement("c", "C", SettlementType.CASTLE, "vlandia", "c2", null, 0));
        realm.addSettlement(new Settlement("n", "N", SettlementType.TOWN, "vlandia", "npc", null, 0));

        List<SeasonScoreboard.Entry> ranking = SeasonScoreboard.rank(realm);
        assertEquals(List.of("p1", "p2"), ranking.stream().map(e -> e.kingdom().id()).toList());
        assertEquals(30 + 5 + 2, ranking.get(0).points());
        assertEquals(20 + 10, ranking.get(1).points());
    }
}
