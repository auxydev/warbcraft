package dev.auxy.warbcraft.core.npc;

import java.util.HashMap;
import java.util.Map;

/** Oyuncu ile NPC'ler (krallar, lordlar) arasindaki iliski puani, [-100, 100]. */
public final class RelationBook {

    public static final int MIN = -100;
    public static final int MAX = 100;

    private final Map<String, Map<String, Integer>> relations = new HashMap<>();

    public int get(String playerId, String npcId) {
        return relations.getOrDefault(playerId, Map.of()).getOrDefault(npcId, 0);
    }

    public int change(String playerId, String npcId, int delta) {
        int value = Math.clamp((long) get(playerId, npcId) + delta, MIN, MAX);
        set(playerId, npcId, value);
        return value;
    }

    public void set(String playerId, String npcId, int value) {
        relations.computeIfAbsent(playerId, k -> new HashMap<>()).put(npcId, Math.clamp(value, MIN, MAX));
    }

    public Map<String, Integer> of(String playerId) {
        return Map.copyOf(relations.getOrDefault(playerId, Map.of()));
    }
}
