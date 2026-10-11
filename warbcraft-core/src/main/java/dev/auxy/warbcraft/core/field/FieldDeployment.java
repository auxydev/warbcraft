package dev.auxy.warbcraft.core.field;

import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.party.TroopStack;
import dev.auxy.warbcraft.core.troop.TroopTree;
import dev.auxy.warbcraft.core.troop.TroopType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Partinin dunyada gorunen kismi. Performans icin sahada en fazla {@code cap} asker bulunur;
 * once en yuksek kademeliler cikar, gerisi yedekte bekler. Sahadaki asker duserse yedekten
 * biri onun yerini alir. Yalnizca saglikli askerler sahaya cikar.
 */
public final class FieldDeployment {

    /** Sahada dusen askerin olme olasiligi; gerisi yaralanir ve iyilesince geri doner. */
    public static final double DEATH_CHANCE = 0.3;

    private FieldDeployment() {
    }

    /**
     * Sahaya cikacak askerler: birlik kimligi -> adet, sahaya cikis sirasinda (yuksek kademe once,
     * esit kademede kalabalik yigin once).
     */
    public static Map<String, Integer> select(Party party, TroopTree tree, int cap) {
        if (cap < 0) {
            throw new IllegalArgumentException("Saha siniri negatif olamaz: " + cap);
        }
        List<Map.Entry<String, TroopStack>> stacks = new ArrayList<>(party.roster().entrySet());
        stacks.sort(Comparator
                .comparingInt((Map.Entry<String, TroopStack> e) -> tree.get(e.getKey()).tier()).reversed()
                .thenComparing(e -> e.getValue().healthy(), Comparator.reverseOrder()));
        Map<String, Integer> result = new LinkedHashMap<>();
        int left = cap;
        for (Map.Entry<String, TroopStack> entry : stacks) {
            int take = Math.min(left, entry.getValue().healthy());
            if (take > 0) {
                result.put(entry.getKey(), take);
                left -= take;
            }
        }
        return result;
    }

    /** Sahadaki bir askerin canavar degerleri; kademe arttikca guclenir. */
    public static Stats stats(TroopType troop) {
        int tier = troop.tier();
        double health = 14 + tier * 4.0;
        double damage = 2 + tier + (troop.category().isMounted() ? 1 : 0);
        boolean ranged = troop.category().isRanged();
        int shootCooldown = ranged ? Math.max(20, 50 - tier * 4) : 0;
        // Ok hasari hizla carpilir (~1.6), bu yuzden taban deger yakin dovusun altinda tutulur.
        double arrowDamage = ranged ? damage * 0.6 : 0;
        return new Stats(health, damage, shootCooldown, arrowDamage);
    }

    /**
     * Sahada dusen asker oldu mu, yaralandi mi?
     *
     * @param roll [0,1) araliginda rastgele sayi
     */
    public static boolean fallIsFatal(double roll) {
        return roll < DEATH_CHANCE;
    }

    /** Askerin oldurdugu yaratigin canina gore yigina yazilan deneyim. */
    public static int killXp(double victimMaxHealth) {
        return Math.max(1, (int) Math.round(victimMaxHealth / 2));
    }

    /**
     * @param maxHealth          can
     * @param attackDamage       yakin dovus hasari
     * @param shootCooldownTicks iki atis arasi tik; menzilli olmayan birliklerde 0
     * @param arrowDamage        okun taban hasari; menzilli olmayan birliklerde 0
     */
    public record Stats(double maxHealth, double attackDamage, int shootCooldownTicks, double arrowDamage) {
    }
}
