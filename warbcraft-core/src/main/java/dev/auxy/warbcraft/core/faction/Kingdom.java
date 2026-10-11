package dev.auxy.warbcraft.core.faction;

import java.util.Objects;

/**
 * Krallik. NPC krallarin hukmettigi krallik ({@code npc=true}) veya oyuncu kralligi.
 *
 * @param rulerId NPC krallar icin NPC kimligi, oyuncu kralliklari icin oyuncu UUID'si
 */
public record Kingdom(String id, String name, String culture, String rulerId, boolean npc) {

    public Kingdom {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(culture, "culture");
        Objects.requireNonNull(rulerId, "rulerId");
    }
}
