package dev.auxy.warbcraft.core.faction;

import dev.auxy.warbcraft.core.npc.NpcBalance;
import dev.auxy.warbcraft.core.season.Season;
import dev.auxy.warbcraft.core.season.WarWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static dev.auxy.warbcraft.core.faction.Diplomacy.Outcome;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiplomacyTest {

    private static final Instant START = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant PREPARATION = START.plus(Duration.ofDays(1));
    private static final Instant CAMPAIGN = START.plus(Duration.ofDays(8));

    private Realm realm;
    private Diplomacy diplomacy;

    @BeforeEach
    void setUp() {
        realm = new Realm();
        realm.addKingdom(new Kingdom("vlandia", "Vlandia", "vlandia", "k1", true));
        realm.addKingdom(new Kingdom("sturgia", "Sturgia", "sturgia", "k2", true));
        realm.addKingdom(new Kingdom("p1", "P1", "vlandia", "u1", false));
        realm.addKingdom(new Kingdom("p2", "P2", "vlandia", "u2", false));
        Season season = new Season(1, START, ZoneOffset.UTC, 7, 21, 7, 24,
                List.of(new WarWindow(Set.of(DayOfWeek.SATURDAY), LocalTime.of(20, 0), LocalTime.of(22, 0))));
        diplomacy = new Diplomacy(realm, season, NpcBalance.DEFAULT);
    }

    @Test
    void restoreSkipsRules() {
        diplomacy.restoreWar("p1", "p2");
        assertTrue(realm.atWar("p1", "p2"));
    }

    @Test
    void noWarsDuringPreparation() {
        assertEquals(Outcome.SEASON_FORBIDS, diplomacy.declareWar("p1", "p2", PREPARATION));
        assertFalse(realm.atWar("p1", "p2"));
    }

    @Test
    void warAndPeaceBetweenPlayers() {
        assertEquals(Outcome.OK, diplomacy.declareWar("p1", "p2", CAMPAIGN));
        assertTrue(realm.atWar("p2", "p1"));
        assertEquals(Outcome.ALREADY_AT_WAR, diplomacy.declareWar("p2", "p1", CAMPAIGN));
        assertEquals(Outcome.OK, diplomacy.makePeace("p1", "p2"));
        assertEquals(Outcome.NOT_AT_WAR, diplomacy.makePeace("p1", "p2"));
        assertEquals(Outcome.SAME_KINGDOM, diplomacy.declareWar("p1", "p1", CAMPAIGN));
    }

    @Test
    void npcKingdomsHaveLimitedAggressionAgainstPlayers() {
        assertEquals(Outcome.OK, diplomacy.declareWar("vlandia", "p1", CAMPAIGN));
        assertEquals(Outcome.NPC_TOO_MANY_WARS, diplomacy.declareWar("vlandia", "p2", CAMPAIGN));
        diplomacy.makePeace("vlandia", "p1");
        assertEquals(Outcome.NPC_COOLDOWN, diplomacy.declareWar("vlandia", "p2", CAMPAIGN.plus(Duration.ofDays(6))));
        assertEquals(Outcome.OK, diplomacy.declareWar("vlandia", "p2", CAMPAIGN.plus(Duration.ofDays(7))));
    }

    @Test
    void npcLimitsDoNotApplyToNpcWarsOrPlayerAttacks() {
        assertEquals(Outcome.OK, diplomacy.declareWar("vlandia", "p1", CAMPAIGN));
        assertEquals(Outcome.OK, diplomacy.declareWar("vlandia", "sturgia", CAMPAIGN));
        assertEquals(Outcome.OK, diplomacy.declareWar("p2", "vlandia", CAMPAIGN));
        assertEquals(Set.of("p1", "p2", "sturgia"), realm.enemiesOf("vlandia"));
    }
}
