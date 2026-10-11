package dev.auxy.warbcraft.core.faction;

import java.util.Objects;

/**
 * Sehir, kale veya koy. Sahibi bir klandir; krallik klan uzerinden belirlenir.
 *
 * @see Realm#kingdomOf(Settlement)
 */
public final class Settlement {

    public static final int MAX_PROSPERITY = 10_000;

    private final String id;
    private final String name;
    private final SettlementType type;
    private final String culture;
    private final String boundTo;
    private String ownerClanId;
    private int prosperity;

    /**
     * @param boundTo koyler icin bagli oldugu sehir/kale; digerleri icin {@code null}
     */
    public Settlement(String id, String name, SettlementType type, String culture, String ownerClanId,
                      String boundTo, int prosperity) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.culture = Objects.requireNonNull(culture, "culture");
        this.ownerClanId = Objects.requireNonNull(ownerClanId, "ownerClanId");
        if ((type == SettlementType.VILLAGE) != (boundTo != null)) {
            throw new IllegalArgumentException(id + ": yalnizca koyler bir yerlesime bagli olur");
        }
        this.boundTo = boundTo;
        setProsperity(prosperity);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public SettlementType type() {
        return type;
    }

    public String culture() {
        return culture;
    }

    public String boundTo() {
        return boundTo;
    }

    public String ownerClanId() {
        return ownerClanId;
    }

    public void setOwnerClanId(String ownerClanId) {
        this.ownerClanId = Objects.requireNonNull(ownerClanId, "ownerClanId");
    }

    public int prosperity() {
        return prosperity;
    }

    public void setProsperity(int prosperity) {
        this.prosperity = Math.clamp(prosperity, 0, MAX_PROSPERITY);
    }

    public void changeProsperity(int delta) {
        setProsperity(prosperity + delta);
    }
}
