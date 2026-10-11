package dev.auxy.warbcraft.plugin.data;

import dev.auxy.warbcraft.core.economy.Purse;
import dev.auxy.warbcraft.core.party.Party;

import java.util.UUID;

/** Bir oyuncunun kalici verisi: parasi ve partisi. */
public final class PlayerProfile {

    private final UUID id;
    private final Purse purse;
    private final Party party;
    private String name;

    public PlayerProfile(UUID id, String name, Purse purse, Party party) {
        this.id = id;
        this.name = name;
        this.purse = purse;
        this.party = party;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Purse purse() {
        return purse;
    }

    public Party party() {
        return party;
    }
}
