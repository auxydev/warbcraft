package dev.auxy.warbcraft.core.faction;

public enum SettlementType {
    /** Pazar, taverna, arena; kusatilabilir. */
    TOWN,
    /** Askeri kale; kusatilabilir. */
    CASTLE,
    /** Uretim yapar, bir sehre/kaleye baglidir; yagmalanabilir ama kusatilamaz. */
    VILLAGE;

    public boolean isFortified() {
        return this != VILLAGE;
    }
}
