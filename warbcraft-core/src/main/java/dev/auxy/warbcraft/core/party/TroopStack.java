package dev.auxy.warbcraft.core.party;

/**
 * Bir partideki ayni turden birliklerin yigini. Deneyim yigina aittir (Bannerlord'daki gibi);
 * terfi ettirilen her asker yigindan {@code upgradeXp} kadar deneyim harcar.
 */
public final class TroopStack {

    private int healthy;
    private int wounded;
    private int xp;

    TroopStack(int healthy, int wounded, int xp) {
        this.healthy = healthy;
        this.wounded = wounded;
        this.xp = xp;
    }

    public int healthy() {
        return healthy;
    }

    public int wounded() {
        return wounded;
    }

    public int total() {
        return healthy + wounded;
    }

    public int xp() {
        return xp;
    }

    void addHealthy(int amount) {
        healthy += amount;
    }

    void addWounded(int amount) {
        wounded += amount;
    }

    void removeHealthy(int amount) {
        healthy -= amount;
    }

    void removeWounded(int amount) {
        wounded -= amount;
    }

    void addXp(int amount) {
        xp += amount;
    }

    void spendXp(int amount) {
        xp -= amount;
    }

    TroopStack copy() {
        return new TroopStack(healthy, wounded, xp);
    }
}
