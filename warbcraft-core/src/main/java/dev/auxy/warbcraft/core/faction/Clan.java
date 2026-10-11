package dev.auxy.warbcraft.core.faction;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Klan: lordlarin ve oyuncularin bagli oldugu aile/birlik. Toprak klana aittir.
 * Kademe (0-6) unle artar ve parti sinirini buyutur.
 */
public final class Clan {

    public static final int MAX_TIER = 6;
    /** Kademe {@code n} icin gereken toplam un: n'inci eleman. */
    private static final int[] RENOWN_FOR_TIER = {0, 50, 150, 350, 900, 1600, 3000};

    private final String id;
    private final String name;
    private final boolean npc;
    private final Set<String> members = new LinkedHashSet<>();
    private String leaderId;
    private String kingdomId;
    private int renown;

    public Clan(String id, String name, String leaderId, boolean npc) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
        this.npc = npc;
        members.add(leaderId);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean npc() {
        return npc;
    }

    public String leaderId() {
        return leaderId;
    }

    public void setLeader(String memberId) {
        if (!members.contains(memberId)) {
            throw new IllegalArgumentException("Lider klan uyesi olmali: " + memberId);
        }
        leaderId = memberId;
    }

    /** Bagli olunan krallik; bagimsiz klanlar icin {@code null}. */
    public String kingdomId() {
        return kingdomId;
    }

    public void setKingdomId(String kingdomId) {
        this.kingdomId = kingdomId;
    }

    public Set<String> members() {
        return Collections.unmodifiableSet(members);
    }

    public void addMember(String memberId) {
        members.add(Objects.requireNonNull(memberId, "memberId"));
    }

    public void removeMember(String memberId) {
        if (memberId.equals(leaderId)) {
            throw new IllegalArgumentException("Lider klandan cikarilamaz; once liderligi devredin");
        }
        members.remove(memberId);
    }

    public int renown() {
        return renown;
    }

    public void addRenown(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Un negatif eklenemez");
        }
        renown = Math.addExact(renown, amount);
    }

    public int tier() {
        int tier = 0;
        while (tier < MAX_TIER && renown >= RENOWN_FOR_TIER[tier + 1]) {
            tier++;
        }
        return tier;
    }

    public static int renownForTier(int tier) {
        return RENOWN_FOR_TIER[Math.clamp(tier, 0, MAX_TIER)];
    }
}
