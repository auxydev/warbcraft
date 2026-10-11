package dev.auxy.warbcraft.core.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bir sehrin pazari. Fiyat arz-talebe gore degisir: stok hedefin altindaysa mal pahalilasir,
 * ustundeyse ucuzlar. Boylece sehirler arasi fiyat farki ticaret firsati yaratir.
 *
 * <p>Fiyat = taban * (hedef / stok)^ESNEKLIK, [MIN_FACTOR, MAX_FACTOR] araliginda sinirli.
 * Satis fiyati alis fiyatindan {@link #SELL_RATIO} kadar dusuktur, boylece ayni sehirde
 * al-sat ile para basilamaz.
 */
public final class Market {

    public static final double ELASTICITY = 0.6;
    public static final double MIN_FACTOR = 0.35;
    public static final double MAX_FACTOR = 3.0;
    public static final double SELL_RATIO = 0.85;

    private final Map<String, Good> goods = new LinkedHashMap<>();
    private final Map<String, Integer> stock = new LinkedHashMap<>();
    private final Map<String, Integer> target = new LinkedHashMap<>();

    public void list(Good good, int targetStock, int initialStock) {
        Objects.requireNonNull(good, "good");
        if (targetStock <= 0 || initialStock < 0) {
            throw new IllegalArgumentException(good.id() + ": hedef stok pozitif, stok negatif olmayan olmali");
        }
        goods.put(good.id(), good);
        target.put(good.id(), targetStock);
        stock.put(good.id(), initialStock);
    }

    public Map<String, Good> goods() {
        return Collections.unmodifiableMap(goods);
    }

    public int stock(String goodId) {
        return stock.getOrDefault(goodId, 0);
    }

    /** Pazardan bir birim almanin fiyati. */
    public int buyPrice(String goodId) {
        Good good = require(goodId);
        int current = Math.max(1, stock.get(goodId));
        double factor = Math.pow((double) target.get(goodId) / current, ELASTICITY);
        factor = Math.clamp(factor, MIN_FACTOR, MAX_FACTOR);
        return Math.max(1, (int) Math.round(good.basePrice() * factor));
    }

    /** Pazara bir birim satmanin getirisi. */
    public int sellPrice(String goodId) {
        require(goodId);
        stock.merge(goodId, 1, Integer::sum);
        try {
            return Math.max(1, (int) Math.floor(buyPrice(goodId) * SELL_RATIO));
        } finally {
            stock.merge(goodId, -1, Integer::sum);
        }
    }

    /**
     * Birim birim alir; her birim fiyati yukseltir.
     *
     * @return odenen toplam; yeterli stok veya para yoksa hicbir sey alinmaz ve -1 doner
     */
    public long buy(String goodId, int amount, Purse purse) {
        requirePositive(amount);
        require(goodId);
        if (stock.get(goodId) < amount) {
            return -1;
        }
        long cost = 0;
        int original = stock.get(goodId);
        for (int i = 0; i < amount; i++) {
            cost += buyPrice(goodId);
            stock.merge(goodId, -1, Integer::sum);
        }
        if (!purse.tryWithdraw(cost)) {
            stock.put(goodId, original);
            return -1;
        }
        return cost;
    }

    /** Birim birim satar; her birim fiyati dusurur. Kazanci cuzdana yatirir. */
    public long sell(String goodId, int amount, Purse purse) {
        requirePositive(amount);
        require(goodId);
        long income = 0;
        for (int i = 0; i < amount; i++) {
            income += sellPrice(goodId);
            stock.merge(goodId, 1, Integer::sum);
        }
        purse.deposit(income);
        return income;
    }

    /** Gunluk uretim/tuketim: koylerden gelen mal stoga eklenir, sehir tuketimi dusulur. */
    public void adjustStock(String goodId, int delta) {
        require(goodId);
        stock.put(goodId, Math.max(0, stock.get(goodId) + delta));
    }

    private Good require(String goodId) {
        Good good = goods.get(goodId);
        if (good == null) {
            throw new IllegalArgumentException("Bu pazarda satilmayan mal: " + goodId);
        }
        return good;
    }

    private static void requirePositive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Miktar pozitif olmali: " + amount);
        }
    }
}
