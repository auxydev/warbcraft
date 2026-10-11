package dev.auxy.warbcraft.core.party;

import dev.auxy.warbcraft.core.troop.TroopTree;
import dev.auxy.warbcraft.core.troop.TroopType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bir lider ve ona bagli birlikler. Sunucu ana is parcaciginda kullanilmak uzere tasarlanmistir
 * (es zamanli erisim icin guvenli degildir).
 */
public final class Party {

    public static final int MIN_MORALE = 0;
    public static final int MAX_MORALE = 100;
    public static final int DEFAULT_MORALE = 50;

    private final String leaderId;
    private final PartyKind kind;
    private final Map<String, TroopStack> roster = new LinkedHashMap<>();
    private int sizeLimit;
    private int morale = DEFAULT_MORALE;

    public Party(String leaderId, PartyKind kind, int sizeLimit) {
        this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
        this.kind = Objects.requireNonNull(kind, "kind");
        setSizeLimit(sizeLimit);
    }

    public String leaderId() {
        return leaderId;
    }

    public PartyKind kind() {
        return kind;
    }

    public int sizeLimit() {
        return sizeLimit;
    }

    public void setSizeLimit(int sizeLimit) {
        if (sizeLimit < 0) {
            throw new IllegalArgumentException("Parti siniri negatif olamaz");
        }
        this.sizeLimit = sizeLimit;
    }

    public int morale() {
        return morale;
    }

    public void setMorale(int morale) {
        this.morale = Math.clamp(morale, MIN_MORALE, MAX_MORALE);
    }

    public void changeMorale(int delta) {
        setMorale(morale + delta);
    }

    public Map<String, TroopStack> roster() {
        return Collections.unmodifiableMap(roster);
    }

    public int count(String troopId) {
        TroopStack stack = roster.get(troopId);
        return stack == null ? 0 : stack.total();
    }

    public int totalCount() {
        int total = 0;
        for (TroopStack stack : roster.values()) {
            total += stack.total();
        }
        return total;
    }

    public int healthyCount() {
        int total = 0;
        for (TroopStack stack : roster.values()) {
            total += stack.healthy();
        }
        return total;
    }

    public int freeSlots() {
        return Math.max(0, sizeLimit - totalCount());
    }

    public boolean isEmpty() {
        return totalCount() == 0;
    }

    /**
     * Saglikli birlik ekler; parti siniri asilmaz.
     *
     * @return gercekte eklenen asker sayisi
     */
    public int addTroops(String troopId, int amount) {
        requirePositive(amount);
        int added = Math.min(amount, freeSlots());
        if (added > 0) {
            stack(troopId).addHealthy(added);
        }
        return added;
    }

    /** Partiyi sinira bakmadan doldurur; savas sonu esir/bolunme gibi sistem islemleri icindir. */
    public void forceAddTroops(String troopId, int healthy, int wounded) {
        if (healthy < 0 || wounded < 0) {
            throw new IllegalArgumentException("Miktar negatif olamaz");
        }
        if (healthy + wounded == 0) {
            return;
        }
        TroopStack stack = stack(troopId);
        stack.addHealthy(healthy);
        stack.addWounded(wounded);
    }

    /**
     * Saglikli askerleri cikarir (once saglikli, sonra yarali).
     *
     * @return gercekte cikarilan asker sayisi
     */
    public int removeTroops(String troopId, int amount) {
        requirePositive(amount);
        TroopStack stack = roster.get(troopId);
        if (stack == null) {
            return 0;
        }
        int fromHealthy = Math.min(amount, stack.healthy());
        stack.removeHealthy(fromHealthy);
        int fromWounded = Math.min(amount - fromHealthy, stack.wounded());
        stack.removeWounded(fromWounded);
        cleanup(troopId, stack);
        return fromHealthy + fromWounded;
    }

    /** Savas kayiplari: saglikli askerlerin bir kismi olur, bir kismi yaralanir. */
    public void applyCasualties(String troopId, int killed, int wounded) {
        if (killed < 0 || wounded < 0) {
            throw new IllegalArgumentException("Kayip negatif olamaz");
        }
        TroopStack stack = roster.get(troopId);
        if (stack == null) {
            return;
        }
        int dead = Math.min(killed, stack.healthy());
        stack.removeHealthy(dead);
        int hurt = Math.min(wounded, stack.healthy());
        stack.removeHealthy(hurt);
        stack.addWounded(hurt);
        cleanup(troopId, stack);
    }

    /** Yarali askerleri iyilestirir; en once eklenen yiginlardan baslar. */
    public int healWounded(int amount) {
        requirePositive(amount);
        int remaining = amount;
        for (TroopStack stack : roster.values()) {
            if (remaining == 0) {
                break;
            }
            int healed = Math.min(remaining, stack.wounded());
            stack.removeWounded(healed);
            stack.addHealthy(healed);
            remaining -= healed;
        }
        return amount - remaining;
    }

    public void addXp(String troopId, int xp) {
        requirePositive(xp);
        TroopStack stack = roster.get(troopId);
        if (stack != null) {
            stack.addXp(xp);
        }
    }

    /** Yigindaki deneyimle en fazla kac asker terfi ettirilebilir. */
    public int upgradeableCount(TroopTree tree, String troopId) {
        TroopStack stack = roster.get(troopId);
        TroopType type = tree.get(troopId);
        if (stack == null || !type.canUpgrade()) {
            return 0;
        }
        return Math.min(stack.healthy(), stack.xp() / type.upgradeXp());
    }

    /**
     * Askerleri bir ust kademeye terfi ettirir. Parti boyutu degismez.
     *
     * @return terfi eden asker sayisi
     */
    public int upgrade(TroopTree tree, String fromId, String toId, int amount) {
        requirePositive(amount);
        TroopType from = tree.get(fromId);
        if (!from.upgradesTo().contains(toId)) {
            throw new IllegalArgumentException(fromId + " birligi " + toId + " birligine terfi edemez");
        }
        int upgraded = Math.min(amount, upgradeableCount(tree, fromId));
        if (upgraded == 0) {
            return 0;
        }
        TroopStack source = roster.get(fromId);
        source.spendXp(upgraded * from.upgradeXp());
        source.removeHealthy(upgraded);
        cleanup(fromId, source);
        stack(toId).addHealthy(upgraded);
        return upgraded;
    }

    public int dailyWage(TroopTree tree) {
        int total = 0;
        for (Map.Entry<String, TroopStack> entry : roster.entrySet()) {
            total += tree.get(entry.getKey()).wage() * entry.getValue().total();
        }
        return total;
    }

    /** Toplam savas gucu; yalnizca saglikli askerler sayilir. */
    public int strength(TroopTree tree) {
        int total = 0;
        for (Map.Entry<String, TroopStack> entry : roster.entrySet()) {
            total += tree.get(entry.getKey()).power() * entry.getValue().healthy();
        }
        return total;
    }

    public Party copy() {
        Party copy = new Party(leaderId, kind, sizeLimit);
        copy.morale = morale;
        roster.forEach((id, stack) -> copy.roster.put(id, stack.copy()));
        return copy;
    }

    private TroopStack stack(String troopId) {
        return roster.computeIfAbsent(Objects.requireNonNull(troopId, "troopId"), id -> new TroopStack(0, 0, 0));
    }

    private void cleanup(String troopId, TroopStack stack) {
        if (stack.total() == 0) {
            roster.remove(troopId);
        }
    }

    private static void requirePositive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Miktar pozitif olmali: " + amount);
        }
    }
}
