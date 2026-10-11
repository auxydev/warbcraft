package dev.auxy.warbcraft.core.party;

import dev.auxy.warbcraft.core.economy.Purse;
import dev.auxy.warbcraft.core.troop.TroopTree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gunluk bakim: maas odeme, moral ve firar. Maas odenemezse moral duser; moral esigin altina
 * inince en dusuk kademeli askerlerden baslayarak firar baslar.
 */
public final class PartyUpkeep {

    public static final int PAID_MORALE_GAIN = 2;
    public static final int UNPAID_MORALE_LOSS = 15;
    public static final int DESERTION_MORALE = 20;
    /** Moral dusukken her gun firar eden asker orani (yuzde). */
    public static final int DESERTION_PERCENT = 10;

    private PartyUpkeep() {
    }

    public record Result(int wagePaid, boolean paidInFull, int deserters) {
    }

    public static Result dailyTick(Party party, TroopTree tree, Purse purse) {
        int wage = party.dailyWage(tree);
        boolean paid = purse.tryWithdraw(wage);
        if (paid) {
            if (party.morale() < Party.DEFAULT_MORALE) {
                party.changeMorale(PAID_MORALE_GAIN);
            }
        } else {
            party.changeMorale(-UNPAID_MORALE_LOSS);
        }
        int deserters = party.morale() < DESERTION_MORALE ? desert(party, tree) : 0;
        return new Result(paid ? wage : 0, paid, deserters);
    }

    private static int desert(Party party, TroopTree tree) {
        int toLeave = (int) Math.ceil(party.totalCount() * DESERTION_PERCENT / 100.0);
        List<String> byTier = new ArrayList<>(party.roster().keySet());
        byTier.sort(Comparator.comparingInt((String id) -> tree.get(id).tier()));
        int left = 0;
        for (String troopId : byTier) {
            if (left == toLeave) {
                break;
            }
            left += party.removeTroops(troopId, Math.min(toLeave - left, party.count(troopId)));
        }
        return left;
    }

    /** Parti siniri: taban + klan kademesi + liderlik becerisi. */
    public static int sizeLimit(int clanTier, int leadershipSkill) {
        return 20 + clanTier * 15 + leadershipSkill / 5;
    }
}
