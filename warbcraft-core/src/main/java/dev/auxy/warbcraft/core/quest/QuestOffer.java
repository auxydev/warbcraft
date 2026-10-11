package dev.auxy.warbcraft.core.quest;

import java.time.Duration;
import java.util.Objects;

/** Bir NPC kralin gorev panosundaki teklif. */
public record QuestOffer(
        String id,
        QuestType type,
        String kingId,
        String kingdomId,
        String target,
        int required,
        int rewardDenars,
        int rewardRelation,
        int rewardRenown,
        Duration timeLimit) {

    public QuestOffer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kingId, "kingId");
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(timeLimit, "timeLimit");
        if (required <= 0 || rewardDenars < 0 || rewardRelation < 0 || rewardRenown < 0) {
            throw new IllegalArgumentException(id + ": gorev degerleri gecersiz");
        }
    }
}
