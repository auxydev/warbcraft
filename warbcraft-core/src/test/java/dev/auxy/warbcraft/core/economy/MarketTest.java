package dev.auxy.warbcraft.core.economy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketTest {

    private Market market;

    @BeforeEach
    void setUp() {
        market = new Market();
        market.list(new Good("grain", "Tahil", 10), 100, 100);
    }

    @Test
    void priceAtTargetIsBasePrice() {
        assertEquals(10, market.buyPrice("grain"));
    }

    @Test
    void scarcityRaisesAndSurplusLowersPrice() {
        market.adjustStock("grain", -75);
        assertTrue(market.buyPrice("grain") > 10);
        market.adjustStock("grain", 375);
        assertTrue(market.buyPrice("grain") < 10);
    }

    @Test
    void priceIsClamped() {
        market.adjustStock("grain", -1000);
        assertEquals(0, market.stock("grain"));
        assertEquals((int) Math.round(10 * Market.MAX_FACTOR), market.buyPrice("grain"));
    }

    @Test
    void buyingRaisesPriceAndChargesPurse() {
        Purse purse = new Purse(10_000);
        long cost = market.buy("grain", 50, purse);
        assertTrue(cost > 500, "her birim fiyati artirmali");
        assertEquals(10_000 - cost, purse.balance());
        assertEquals(50, market.stock("grain"));
    }

    @Test
    void failedPurchaseChangesNothing() {
        Purse purse = new Purse(20);
        assertEquals(-1, market.buy("grain", 10, purse));
        assertEquals(100, market.stock("grain"));
        assertEquals(20, purse.balance());
        assertEquals(-1, market.buy("grain", 101, new Purse(1_000_000)));
    }

    @Test
    void roundTripIsNotProfitable() {
        Purse purse = new Purse(10_000);
        long cost = market.buy("grain", 30, purse);
        long income = market.sell("grain", 30, purse);
        assertTrue(income < cost);
        assertEquals(100, market.stock("grain"));
    }

    @Test
    void unknownGoodIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> market.buyPrice("iron"));
    }

    @Test
    void purseNeverGoesNegative() {
        Purse purse = new Purse(5);
        assertFalse(purse.tryWithdraw(6));
        assertTrue(purse.tryWithdraw(5));
        assertEquals(0, purse.balance());
        assertThrows(IllegalArgumentException.class, () -> purse.deposit(-1));
    }
}
