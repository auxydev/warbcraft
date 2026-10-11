package dev.auxy.warbcraft.core.npc;

import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.party.PartyKind;
import dev.auxy.warbcraft.core.party.PartyUpkeep;
import dev.auxy.warbcraft.core.troop.TroopTree;
import dev.auxy.warbcraft.core.troop.TroopType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/** {@link NpcBalance} sinirlarina uyan NPC lord ve garnizon partileri olusturur. */
public final class NpcLordFactory {

    /** Kademe dagilimi agirliklari (kademe 1..6). Ust kademeler nadirdir. */
    private static final int[] TIER_WEIGHTS = {30, 25, 20, 15, 7, 3};

    private final TroopTree tree;
    private final NpcBalance balance;

    public NpcLordFactory(TroopTree tree, NpcBalance balance) {
        this.tree = tree;
        this.balance = balance;
    }

    public int lordPartyLimit(int clanTier) {
        return Math.max(1, (int) Math.floor(PartyUpkeep.sizeLimit(clanTier, 0) * balance.lordPartyFactor()));
    }

    public Party createLordParty(String lordId, String culture, int clanTier, Random random) {
        int limit = lordPartyLimit(clanTier);
        Party party = new Party(lordId, PartyKind.NPC_LORD, limit);
        fill(party, culture, limit, random);
        return party;
    }

    public Party createGarrison(String settlementId, String culture, int baseSize, Random random) {
        int size = Math.max(1, (int) Math.floor(baseSize * balance.garrisonFactor()));
        Party party = new Party(settlementId, PartyKind.GARRISON, size);
        fill(party, culture, size, random);
        return party;
    }

    private void fill(Party party, String culture, int amount, Random random) {
        Map<Integer, List<TroopType>> byTier = new TreeMap<>();
        for (TroopType troop : tree.all()) {
            if (troop.culture().equals(culture) && troop.tier() <= balance.maxLordTroopTier()) {
                byTier.computeIfAbsent(troop.tier(), t -> new ArrayList<>()).add(troop);
            }
        }
        if (byTier.isEmpty()) {
            throw new IllegalArgumentException("Kulturde uygun birlik yok: " + culture);
        }
        int totalWeight = 0;
        for (int tier : byTier.keySet()) {
            totalWeight += TIER_WEIGHTS[tier - 1];
        }
        for (int i = 0; i < amount; i++) {
            int roll = random.nextInt(totalWeight);
            for (Map.Entry<Integer, List<TroopType>> entry : byTier.entrySet()) {
                roll -= TIER_WEIGHTS[entry.getKey() - 1];
                if (roll < 0) {
                    List<TroopType> options = entry.getValue();
                    party.addTroops(options.get(random.nextInt(options.size())).id(), 1);
                    break;
                }
            }
        }
    }
}
