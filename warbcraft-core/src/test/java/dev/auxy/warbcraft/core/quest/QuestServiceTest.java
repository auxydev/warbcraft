package dev.auxy.warbcraft.core.quest;

import dev.auxy.warbcraft.core.economy.Good;
import dev.auxy.warbcraft.core.economy.Purse;
import dev.auxy.warbcraft.core.npc.NpcKing;
import dev.auxy.warbcraft.core.npc.RelationBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static dev.auxy.warbcraft.core.quest.QuestService.AcceptOutcome;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    private static final NpcKing KING = new NpcKing("derthert", "Kral Derthert", "vlandia", "pravend");
    private static final List<Good> GOODS = List.of(new Good("grain", "Tahil", 10));

    private RelationBook relations;
    private Map<String, Purse> purses;
    private QuestService quests;

    @BeforeEach
    void setUp() {
        relations = new RelationBook();
        purses = new HashMap<>();
        quests = new QuestService(relations, id -> purses.computeIfAbsent(id, k -> new Purse(0)));
    }

    /** Panoda istenen turde teklif cikana kadar yeniler ve "u1" icin kabul eder. */
    private QuestOffer accepted(QuestType type, String target) {
        for (int seed = 0; seed < 500; seed++) {
            for (QuestOffer offer : quests.refreshBoard(KING, GOODS, Set.of("sturgia"), new Random(seed))) {
                if (offer.type() == type && (target == null || offer.target().equals(target))) {
                    assertEquals(AcceptOutcome.OK, quests.accept("u1", KING.id(), offer.id(), NOW));
                    return offer;
                }
            }
        }
        throw new AssertionError("teklif bulunamadi: " + type);
    }

    @Test
    void boardHasOffersAndWarServiceOnlyWhenAtWar() {
        assertEquals(QuestService.OFFERS_PER_KING, quests.refreshBoard(KING, GOODS, Set.of(), new Random(1)).size());
        for (int seed = 0; seed < 200; seed++) {
            Collection<QuestOffer> board = quests.refreshBoard(KING, GOODS, Set.of(), new Random(seed));
            assertTrue(board.stream().noneMatch(o -> o.type() == QuestType.WAR_SERVICE));
        }
        assertEquals("sturgia", accepted(QuestType.WAR_SERVICE, null).target());
    }

    @Test
    void completingQuestPaysRewardAndRaisesRelation() {
        QuestOffer offer = accepted(QuestType.HUNT_BANDITS, null);
        assertTrue(quests.board(KING.id()).stream().noneMatch(o -> o.id().equals(offer.id())));
        assertTrue(quests.progress("u1", QuestType.HUNT_BANDITS, "", offer.required() - 1, NOW).isEmpty());
        List<Quest> done = quests.progress("u1", QuestType.HUNT_BANDITS, "", 5, NOW);
        assertEquals(1, done.size());
        assertEquals(QuestStatus.COMPLETED, done.getFirst().status());
        assertEquals(offer.rewardDenars(), purses.get("u1").balance());
        assertEquals(offer.rewardRelation(), relations.get("u1", KING.id()));
        assertTrue(quests.activeFor("u1").isEmpty());
    }

    @Test
    void deliveryOnlyCountsRequestedGood() {
        QuestOffer offer = accepted(QuestType.DELIVER_GOODS, "grain");
        assertTrue(quests.progress("u1", QuestType.DELIVER_GOODS, "iron", 100, NOW).isEmpty());
        assertEquals(offer.required(), quests.find("u1", offer.id()).orElseThrow().remaining());
        assertEquals(1, quests.progress("u1", QuestType.DELIVER_GOODS, "grain", offer.required(), NOW).size());
    }

    @Test
    void raiseTroopsAcceptsHigherTiers() {
        QuestOffer offer = accepted(QuestType.RAISE_TROOPS, null);
        int tier = Integer.parseInt(offer.target());
        assertTrue(quests.progress("u1", QuestType.RAISE_TROOPS, Integer.toString(tier - 1), 50, NOW).isEmpty());
        assertEquals(1, quests.progress("u1", QuestType.RAISE_TROOPS, Integer.toString(tier + 1), offer.required(), NOW).size());
    }

    @Test
    void acceptLimits() {
        accepted(QuestType.HUNT_BANDITS, null);
        accepted(QuestType.HUNT_BANDITS, null);
        accepted(QuestType.HUNT_BANDITS, null);
        QuestOffer fourth = quests.refreshBoard(KING, GOODS, Set.of(), new Random(9)).iterator().next();
        assertEquals(AcceptOutcome.TOO_MANY_ACTIVE, quests.accept("u1", KING.id(), fourth.id(), NOW));
        assertEquals(AcceptOutcome.NOT_FOUND, quests.accept("u2", KING.id(), "nope", NOW));
        relations.set("u2", KING.id(), -50);
        assertEquals(AcceptOutcome.RELATION_TOO_LOW, quests.accept("u2", KING.id(), fourth.id(), NOW));
        relations.set("u2", KING.id(), 0);
        assertEquals(AcceptOutcome.OK, quests.accept("u2", KING.id(), fourth.id(), NOW));
        assertEquals(4, quests.allActive().size());
        assertEquals(AcceptOutcome.NOT_FOUND, quests.accept("u3", KING.id(), fourth.id(), NOW));
    }

    @Test
    void expiredQuestsFailWithPenaltyAndStopProgressing() {
        QuestOffer offer = accepted(QuestType.HUNT_BANDITS, null);
        Instant late = NOW.plus(offer.timeLimit()).plusSeconds(1);
        assertTrue(quests.progress("u1", QuestType.HUNT_BANDITS, "", 100, late).isEmpty());
        List<Quest> failed = quests.expire(late);
        assertEquals(1, failed.size());
        assertEquals(QuestStatus.FAILED, failed.getFirst().status());
        assertEquals(-QuestService.FAILURE_RELATION_PENALTY, relations.get("u1", KING.id()));
        assertTrue(quests.activeFor("u1").isEmpty());
    }

    @Test
    void abandoningCostsRelation() {
        QuestOffer offer = accepted(QuestType.HUNT_BANDITS, null);
        assertTrue(quests.abandon("u1", offer.id()));
        assertFalse(quests.abandon("u1", offer.id()));
        assertEquals(-QuestService.FAILURE_RELATION_PENALTY, relations.get("u1", KING.id()));
    }
}
