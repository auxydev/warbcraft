package dev.auxy.warbcraft.core.quest;

import java.time.Instant;
import java.util.Objects;

/** Bir oyuncunun kabul ettigi gorev. */
public final class Quest {

    private final QuestOffer offer;
    private final String assigneeId;
    private final Instant acceptedAt;
    private int progress;
    private QuestStatus status = QuestStatus.ACTIVE;

    public Quest(QuestOffer offer, String assigneeId, Instant acceptedAt) {
        this.offer = Objects.requireNonNull(offer, "offer");
        this.assigneeId = Objects.requireNonNull(assigneeId, "assigneeId");
        this.acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt");
    }

    /** Kayittan geri yukleme icin. */
    public static Quest restore(QuestOffer offer, String assigneeId, Instant acceptedAt, int progress, QuestStatus status) {
        Quest quest = new Quest(offer, assigneeId, acceptedAt);
        quest.progress = Math.clamp(progress, 0, offer.required());
        quest.status = Objects.requireNonNull(status, "status");
        return quest;
    }

    public QuestOffer offer() {
        return offer;
    }

    public String assigneeId() {
        return assigneeId;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Instant deadline() {
        return acceptedAt.plus(offer.timeLimit());
    }

    public int progress() {
        return progress;
    }

    public int remaining() {
        return offer.required() - progress;
    }

    public QuestStatus status() {
        return status;
    }

    /** @return gercekte sayilan ilerleme */
    int advance(int amount) {
        int counted = Math.min(amount, remaining());
        progress += counted;
        if (remaining() == 0) {
            status = QuestStatus.COMPLETED;
        }
        return counted;
    }

    void fail() {
        status = QuestStatus.FAILED;
    }
}
