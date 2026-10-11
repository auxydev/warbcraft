package dev.auxy.warbcraft.core.npc;

import java.util.Objects;

/**
 * NPC kral. Baskentinde oturur ve oyunculara gorev verir. Savasa bizzat katilmaz; gucu
 * lordlarindan gelir ve lordlar {@link NpcBalance} ile sinirlidir.
 *
 * @param seatId kralin oturdugu sehir (gorevler burada alinir/teslim edilir)
 */
public record NpcKing(String id, String name, String kingdomId, String seatId) {

    public NpcKing {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(seatId, "seatId");
    }
}
