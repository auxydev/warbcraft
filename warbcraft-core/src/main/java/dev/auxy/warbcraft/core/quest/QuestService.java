package dev.auxy.warbcraft.core.quest;

import dev.auxy.warbcraft.core.economy.Good;
import dev.auxy.warbcraft.core.economy.Purse;
import dev.auxy.warbcraft.core.npc.NpcKing;
import dev.auxy.warbcraft.core.npc.RelationBook;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * NPC krallarin gorev panolari ve oyuncularin aktif gorevleri. Odul (dinar + iliski) gorev
 * tamamlandigi anda verilir; un ise sonuc listesiyle doner, klana eklemek cagirana kalir.
 */
public final class QuestService {

    public static final int MAX_ACTIVE_PER_PLAYER = 3;
    public static final int OFFERS_PER_KING = 3;
    /** Bu iliskinin altindaki oyunculara kral gorev vermez. */
    public static final int MIN_RELATION_FOR_QUEST = -20;
    public static final int FAILURE_RELATION_PENALTY = 5;

    public enum AcceptOutcome {
        OK("Gorev kabul edildi"),
        NOT_FOUND("Boyle bir gorev teklifi yok"),
        TOO_MANY_ACTIVE("Ayni anda en fazla " + MAX_ACTIVE_PER_PLAYER + " gorev alabilirsin"),
        RELATION_TOO_LOW("Kral sana guvenmiyor; once iliskini duzelt");

        private final String message;

        AcceptOutcome(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final RelationBook relations;
    private final Function<String, Purse> purses;
    private final Map<String, Map<String, QuestOffer>> boards = new LinkedHashMap<>();
    private final Map<String, List<Quest>> active = new LinkedHashMap<>();
    private long nextOfferId = 1;

    public QuestService(RelationBook relations, Function<String, Purse> purses) {
        this.relations = Objects.requireNonNull(relations, "relations");
        this.purses = Objects.requireNonNull(purses, "purses");
    }

    /**
     * Kralin panosunu yeni tekliflerle doldurur (her gun yenilenir).
     *
     * @param goods   teslimat gorevlerinde istenebilecek mallar
     * @param enemies kralligin savastigi kralliklar; bossa sefer gorevi cikmaz
     */
    public Collection<QuestOffer> refreshBoard(NpcKing king, List<Good> goods, Set<String> enemies, Random random) {
        Map<String, QuestOffer> board = new LinkedHashMap<>();
        List<QuestType> types = new ArrayList<>(List.of(QuestType.HUNT_BANDITS, QuestType.RAISE_TROOPS));
        if (!goods.isEmpty()) {
            types.add(QuestType.DELIVER_GOODS);
        }
        if (!enemies.isEmpty()) {
            types.add(QuestType.WAR_SERVICE);
        }
        for (int i = 0; i < OFFERS_PER_KING; i++) {
            QuestType type = types.get(random.nextInt(types.size()));
            QuestOffer offer = generate(king, type, goods, List.copyOf(enemies), random);
            board.put(offer.id(), offer);
        }
        boards.put(king.id(), board);
        return board.values();
    }

    private QuestOffer generate(NpcKing king, QuestType type, List<Good> goods, List<String> enemies, Random random) {
        String id = "q" + nextOfferId++;
        return switch (type) {
            case HUNT_BANDITS -> {
                int amount = 5 + random.nextInt(11);
                yield new QuestOffer(id, type, king.id(), king.kingdomId(), "", amount,
                        amount * 40, 3, 5, Duration.ofDays(3));
            }
            case DELIVER_GOODS -> {
                Good good = goods.get(random.nextInt(goods.size()));
                int amount = 10 + random.nextInt(21);
                yield new QuestOffer(id, type, king.id(), king.kingdomId(), good.id(), amount,
                        (int) Math.round(amount * good.basePrice() * 1.4), 2, 3, Duration.ofDays(2));
            }
            case RAISE_TROOPS -> {
                int tier = 2 + random.nextInt(2);
                int amount = 5 + random.nextInt(6);
                yield new QuestOffer(id, type, king.id(), king.kingdomId(), Integer.toString(tier), amount,
                        amount * tier * 60, 4, 6, Duration.ofDays(4));
            }
            case WAR_SERVICE -> {
                String enemy = enemies.get(random.nextInt(enemies.size()));
                int amount = 1 + random.nextInt(3);
                yield new QuestOffer(id, type, king.id(), king.kingdomId(), enemy, amount,
                        amount * 500, 6, 15, Duration.ofDays(5));
            }
        };
    }

    public Collection<QuestOffer> board(String kingId) {
        return List.copyOf(boards.getOrDefault(kingId, Map.of()).values());
    }

    public AcceptOutcome accept(String playerId, String kingId, String offerId, Instant now) {
        Map<String, QuestOffer> board = boards.get(kingId);
        QuestOffer offer = board == null ? null : board.get(offerId);
        if (offer == null) {
            return AcceptOutcome.NOT_FOUND;
        }
        if (activeFor(playerId).size() >= MAX_ACTIVE_PER_PLAYER) {
            return AcceptOutcome.TOO_MANY_ACTIVE;
        }
        if (relations.get(playerId, kingId) < MIN_RELATION_FOR_QUEST) {
            return AcceptOutcome.RELATION_TOO_LOW;
        }
        board.remove(offerId);
        active.computeIfAbsent(playerId, k -> new ArrayList<>()).add(new Quest(offer, playerId, now));
        return AcceptOutcome.OK;
    }

    public List<Quest> activeFor(String playerId) {
        return List.copyOf(active.getOrDefault(playerId, List.of()));
    }

    /** Tum oyuncularin aktif gorevleri (kayit icin). */
    public List<Quest> allActive() {
        List<Quest> result = new ArrayList<>();
        active.values().forEach(result::addAll);
        return result;
    }

    public Optional<Quest> find(String playerId, String offerId) {
        for (Quest quest : active.getOrDefault(playerId, List.of())) {
            if (quest.offer().id().equals(offerId)) {
                return Optional.of(quest);
            }
        }
        return Optional.empty();
    }

    /** Kayittan yukleme. */
    public void restore(Quest quest) {
        if (quest.status() == QuestStatus.ACTIVE) {
            active.computeIfAbsent(quest.assigneeId(), k -> new ArrayList<>()).add(quest);
        }
    }

    /**
     * Bir olay gorevleri ilerletir (haydut olduruldu, mal teslim edildi, savas kazanildi...).
     * Turu ve hedefi eslesen gorevler ilerler; tamamlananlarin odulu verilir.
     *
     * @return bu olayla tamamlanan gorevler
     */
    public List<Quest> progress(String playerId, QuestType type, String target, int amount, Instant now) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Ilerleme pozitif olmali");
        }
        List<Quest> completed = new ArrayList<>();
        List<Quest> quests = active.getOrDefault(playerId, List.of());
        int left = amount;
        for (Quest quest : List.copyOf(quests)) {
            if (left == 0) {
                break;
            }
            if (quest.offer().type() != type || !matchesTarget(quest.offer(), target) || now.isAfter(quest.deadline())) {
                continue;
            }
            left -= quest.advance(left);
            if (quest.status() == QuestStatus.COMPLETED) {
                quests.remove(quest);
                purses.apply(playerId).deposit(quest.offer().rewardDenars());
                relations.change(playerId, quest.offer().kingId(), quest.offer().rewardRelation());
                completed.add(quest);
            }
        }
        return completed;
    }

    private static boolean matchesTarget(QuestOffer offer, String target) {
        if (offer.type() == QuestType.RAISE_TROOPS) {
            // Hedef en dusuk kademe; daha yuksek kademeli asker de kabul edilir.
            return Integer.parseInt(target) >= Integer.parseInt(offer.target());
        }
        return offer.target().isEmpty() || offer.target().equals(target);
    }

    /** Suresi dolan gorevleri basarisiz sayar ve iliski cezasi uygular. */
    public List<Quest> expire(Instant now) {
        List<Quest> failed = new ArrayList<>();
        for (List<Quest> quests : active.values()) {
            for (Quest quest : List.copyOf(quests)) {
                if (now.isAfter(quest.deadline())) {
                    quest.fail();
                    quests.remove(quest);
                    relations.change(quest.assigneeId(), quest.offer().kingId(), -FAILURE_RELATION_PENALTY);
                    failed.add(quest);
                }
            }
        }
        return failed;
    }

    /** Oyuncu gorevden vazgecer; basarisizlik gibi sayilir. */
    public boolean abandon(String playerId, String offerId) {
        Optional<Quest> quest = find(playerId, offerId);
        if (quest.isEmpty()) {
            return false;
        }
        quest.get().fail();
        active.get(playerId).remove(quest.get());
        relations.change(playerId, quest.get().offer().kingId(), -FAILURE_RELATION_PENALTY);
        return true;
    }

    /** Kayittan yuklemede teklif kimliklerinin cakismamasi icin. */
    public void reserveOfferIds(long next) {
        nextOfferId = Math.max(nextOfferId, next);
    }

    public long nextOfferId() {
        return nextOfferId;
    }
}
