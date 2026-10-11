package dev.auxy.warbcraft.core.faction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Dunyadaki tum kralliklar, klanlar, yerlesimler ve aralarindaki savaslar. */
public final class Realm {

    private final Map<String, Kingdom> kingdoms = new LinkedHashMap<>();
    private final Map<String, Clan> clans = new LinkedHashMap<>();
    private final Map<String, Settlement> settlements = new LinkedHashMap<>();
    private final Set<Set<String>> wars = new HashSet<>();

    public void addKingdom(Kingdom kingdom) {
        putUnique(kingdoms, kingdom.id(), kingdom, "krallik");
    }

    public void addClan(Clan clan) {
        if (clan.kingdomId() != null) {
            kingdom(clan.kingdomId());
        }
        putUnique(clans, clan.id(), clan, "klan");
    }

    public void addSettlement(Settlement settlement) {
        clan(settlement.ownerClanId());
        if (settlement.boundTo() != null) {
            Settlement parent = settlement(settlement.boundTo());
            if (!parent.type().isFortified()) {
                throw new IllegalArgumentException(settlement.id() + ": koy yalnizca sehre/kaleye baglanabilir");
            }
        }
        putUnique(settlements, settlement.id(), settlement, "yerlesim");
    }

    public Kingdom kingdom(String id) {
        return require(kingdoms, id, "krallik");
    }

    public Clan clan(String id) {
        return require(clans, id, "klan");
    }

    public Settlement settlement(String id) {
        return require(settlements, id, "yerlesim");
    }

    public Optional<Kingdom> findKingdom(String id) {
        return Optional.ofNullable(kingdoms.get(id));
    }

    public Optional<Clan> findClan(String id) {
        return Optional.ofNullable(clans.get(id));
    }

    public Optional<Settlement> findSettlement(String id) {
        return Optional.ofNullable(settlements.get(id));
    }

    public Collection<Kingdom> kingdoms() {
        return Collections.unmodifiableCollection(kingdoms.values());
    }

    public Collection<Clan> clans() {
        return Collections.unmodifiableCollection(clans.values());
    }

    public Collection<Settlement> settlements() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    public Optional<Clan> clanOfMember(String memberId) {
        for (Clan clan : clans.values()) {
            if (clan.members().contains(memberId)) {
                return Optional.of(clan);
            }
        }
        return Optional.empty();
    }

    public Optional<Kingdom> kingdomOf(Settlement settlement) {
        String kingdomId = clan(settlement.ownerClanId()).kingdomId();
        return kingdomId == null ? Optional.empty() : Optional.of(kingdom(kingdomId));
    }

    public List<Settlement> settlementsOfKingdom(String kingdomId) {
        List<Settlement> result = new ArrayList<>();
        for (Settlement settlement : settlements.values()) {
            if (kingdomId.equals(clan(settlement.ownerClanId()).kingdomId())) {
                result.add(settlement);
            }
        }
        return result;
    }

    public List<Clan> clansOfKingdom(String kingdomId) {
        List<Clan> result = new ArrayList<>();
        for (Clan clan : clans.values()) {
            if (kingdomId.equals(clan.kingdomId())) {
                result.add(clan);
            }
        }
        return result;
    }

    public boolean atWar(String kingdomA, String kingdomB) {
        return wars.contains(Set.of(kingdomA, kingdomB));
    }

    public Set<String> enemiesOf(String kingdomId) {
        Set<String> result = new HashSet<>();
        for (Set<String> war : wars) {
            if (war.contains(kingdomId)) {
                for (String other : war) {
                    if (!other.equals(kingdomId)) {
                        result.add(other);
                    }
                }
            }
        }
        return result;
    }

    /** Savas durumunu dogrudan ayarlar; kural kontrolleri {@link Diplomacy} icindedir. */
    void setWar(String kingdomA, String kingdomB, boolean war) {
        if (kingdomA.equals(kingdomB)) {
            throw new IllegalArgumentException("Bir krallik kendisiyle savasamaz");
        }
        kingdom(kingdomA);
        kingdom(kingdomB);
        if (war) {
            wars.add(Set.of(kingdomA, kingdomB));
        } else {
            wars.remove(Set.of(kingdomA, kingdomB));
        }
    }

    /**
     * Yerlesimi yeni klana devreder. Sehir/kale el degistirdiginde bagli koyler de birlikte gecer.
     */
    public void transferSettlement(String settlementId, String newClanId) {
        Settlement settlement = settlement(settlementId);
        clan(newClanId);
        settlement.setOwnerClanId(newClanId);
        if (settlement.type().isFortified()) {
            for (Settlement village : settlements.values()) {
                if (settlementId.equals(village.boundTo())) {
                    village.setOwnerClanId(newClanId);
                }
            }
        }
    }

    private static <T> void putUnique(Map<String, T> map, String id, T value, String what) {
        if (map.putIfAbsent(id, value) != null) {
            throw new IllegalArgumentException("Ayni " + what + " iki kez tanimlanmis: " + id);
        }
    }

    private static <T> T require(Map<String, T> map, String id, String what) {
        T value = map.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Bilinmeyen " + what + ": " + id);
        }
        return value;
    }
}
