package dev.auxy.warbcraft.core.economy;

/** Dinar cuzdani. Bakiye hicbir zaman negatif olmaz. */
public final class Purse {

    private long balance;

    public Purse(long balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("Bakiye negatif olamaz");
        }
        this.balance = balance;
    }

    public long balance() {
        return balance;
    }

    public void deposit(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Yatirilan miktar negatif olamaz");
        }
        balance = Math.addExact(balance, amount);
    }

    public boolean tryWithdraw(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cekilen miktar negatif olamaz");
        }
        if (amount > balance) {
            return false;
        }
        balance -= amount;
        return true;
    }
}
