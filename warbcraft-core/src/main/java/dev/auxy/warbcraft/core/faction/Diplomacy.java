package dev.auxy.warbcraft.core.faction;

import dev.auxy.warbcraft.core.npc.NpcBalance;
import dev.auxy.warbcraft.core.season.Season;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Savas ve baris ilanlari; sezon ve NPC dengesi kurallarini uygular. */
public final class Diplomacy {

    public enum Outcome {
        OK("Tamam"),
        SAME_KINGDOM("Bir krallik kendisine savas ilan edemez"),
        ALREADY_AT_WAR("Zaten savastasiniz"),
        NOT_AT_WAR("Savasta degilsiniz"),
        SEASON_FORBIDS("Sezonun bu evresinde savas ilan edilemez"),
        NPC_COOLDOWN("Bu NPC krallik oyunculara kisa sure once savas ilan etti"),
        NPC_TOO_MANY_WARS("Bu NPC krallik zaten yeterince oyuncu kralligiyla savasta");

        private final String message;

        Outcome(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final Realm realm;
    private final Season season;
    private final NpcBalance balance;
    private final Map<String, Instant> lastNpcDeclarationVsPlayers = new HashMap<>();

    public Diplomacy(Realm realm, Season season, NpcBalance balance) {
        this.realm = realm;
        this.season = season;
        this.balance = balance;
    }

    public Outcome declareWar(String attackerId, String defenderId, Instant now) {
        Outcome outcome = checkWar(attackerId, defenderId, now);
        if (outcome == Outcome.OK) {
            realm.setWar(attackerId, defenderId, true);
            if (realm.kingdom(attackerId).npc() && !realm.kingdom(defenderId).npc()) {
                lastNpcDeclarationVsPlayers.put(attackerId, now);
            }
        }
        return outcome;
    }

    public Outcome checkWar(String attackerId, String defenderId, Instant now) {
        if (attackerId.equals(defenderId)) {
            return Outcome.SAME_KINGDOM;
        }
        Kingdom attacker = realm.kingdom(attackerId);
        Kingdom defender = realm.kingdom(defenderId);
        if (realm.atWar(attackerId, defenderId)) {
            return Outcome.ALREADY_AT_WAR;
        }
        if (!season.phaseAt(now).warsAllowed()) {
            return Outcome.SEASON_FORBIDS;
        }
        if (attacker.npc() && !defender.npc()) {
            if (playerWarCount(attackerId) >= balance.maxNpcWarsVsPlayers()) {
                return Outcome.NPC_TOO_MANY_WARS;
            }
            Instant last = lastNpcDeclarationVsPlayers.get(attackerId);
            if (last != null && Duration.between(last, now).compareTo(Duration.ofDays(balance.npcWarCooldownDays())) < 0) {
                return Outcome.NPC_COOLDOWN;
            }
        }
        return Outcome.OK;
    }

    public Outcome makePeace(String kingdomA, String kingdomB) {
        if (!realm.atWar(kingdomA, kingdomB)) {
            return Outcome.NOT_AT_WAR;
        }
        realm.setWar(kingdomA, kingdomB, false);
        return Outcome.OK;
    }

    /** Kayittan savas durumunu kural kontrolu olmadan geri yukler. */
    public void restoreWar(String kingdomA, String kingdomB) {
        realm.setWar(kingdomA, kingdomB, true);
    }

    private int playerWarCount(String npcKingdomId) {
        int count = 0;
        for (String enemy : realm.enemiesOf(npcKingdomId)) {
            if (!realm.kingdom(enemy).npc()) {
                count++;
            }
        }
        return count;
    }
}
