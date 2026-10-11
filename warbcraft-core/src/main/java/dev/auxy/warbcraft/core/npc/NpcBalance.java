package dev.auxy.warbcraft.core.npc;

import dev.auxy.warbcraft.core.troop.TroopType;

/**
 * NPC kralliklarin gucunu sinirlayan ayarlar. Tasarim hedefi: NPC krallar dunyayi canli tutar ve
 * gorev verir, ama orta seviye bir oyuncu klani bir NPC lordunu yenebilir; oyunu NPC'ler degil
 * oyuncular domine eder.
 *
 * @param lordPartyFactor        NPC lord parti siniri = oyuncu siniri * bu carpan (0-1]
 * @param maxLordTroopTier       NPC lord partilerindeki en yuksek birlik kademesi
 * @param garrisonFactor         NPC garnizonlarinin boyut carpani (0-1]
 * @param npcWarCooldownDays     bir NPC kralligin oyuncu kralligina iki savas ilani arasi en az gun
 * @param maxNpcWarsVsPlayers    bir NPC kralligin ayni anda oyuncu kralliklariyla en fazla savas sayisi
 */
public record NpcBalance(
        double lordPartyFactor,
        int maxLordTroopTier,
        double garrisonFactor,
        int npcWarCooldownDays,
        int maxNpcWarsVsPlayers) {

    public static final NpcBalance DEFAULT = new NpcBalance(0.6, 4, 0.7, 7, 1);

    public NpcBalance {
        if (lordPartyFactor <= 0 || lordPartyFactor > 1) {
            throw new IllegalArgumentException("lordPartyFactor (0,1] araliginda olmali");
        }
        if (maxLordTroopTier < TroopType.MIN_TIER || maxLordTroopTier > TroopType.MAX_TIER) {
            throw new IllegalArgumentException("maxLordTroopTier gecerli bir kademe olmali");
        }
        if (garrisonFactor <= 0 || garrisonFactor > 1) {
            throw new IllegalArgumentException("garrisonFactor (0,1] araliginda olmali");
        }
        if (npcWarCooldownDays < 0 || maxNpcWarsVsPlayers < 0) {
            throw new IllegalArgumentException("NPC savas sinirlari negatif olamaz");
        }
    }
}
