package dev.auxy.warbcraft.core.troop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tum kulturlerin birlik agaclari. Yuklenirken dogrulanir: her terfi hedefi tanimli olmali,
 * ayni kulturden olmali ve kademe tam olarak bir artmali (boylece dongu olusamaz).
 */
public final class TroopTree {

    private final Map<String, TroopType> byId;

    private TroopTree(Map<String, TroopType> byId) {
        this.byId = Collections.unmodifiableMap(byId);
    }

    public static TroopTree of(Collection<TroopType> troops) {
        Map<String, TroopType> map = new LinkedHashMap<>();
        for (TroopType troop : troops) {
            if (map.putIfAbsent(troop.id(), troop) != null) {
                throw new IllegalArgumentException("Ayni birlik iki kez tanimlanmis: " + troop.id());
            }
        }
        for (TroopType troop : map.values()) {
            for (String targetId : troop.upgradesTo()) {
                TroopType target = map.get(targetId);
                if (target == null) {
                    throw new IllegalArgumentException(troop.id() + ": bilinmeyen terfi hedefi " + targetId);
                }
                if (!target.culture().equals(troop.culture())) {
                    throw new IllegalArgumentException(troop.id() + ": baska kulture terfi edemez (" + targetId + ")");
                }
                if (target.tier() != troop.tier() + 1) {
                    throw new IllegalArgumentException(troop.id() + ": terfi hedefi bir ust kademede olmali (" + targetId + ")");
                }
            }
        }
        return new TroopTree(map);
    }

    public Optional<TroopType> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public TroopType get(String id) {
        TroopType troop = byId.get(id);
        if (troop == null) {
            throw new IllegalArgumentException("Bilinmeyen birlik: " + id);
        }
        return troop;
    }

    public Collection<TroopType> all() {
        return byId.values();
    }

    /** Bir kulturun acemi (kademe 1) birlikleri; tavernada/koyde toplanabilenler. */
    public List<TroopType> recruits(String culture) {
        List<TroopType> result = new ArrayList<>();
        for (TroopType troop : byId.values()) {
            if (troop.culture().equals(culture) && troop.tier() == TroopType.MIN_TIER) {
                result.add(troop);
            }
        }
        return result;
    }

    public int size() {
        return byId.size();
    }
}
