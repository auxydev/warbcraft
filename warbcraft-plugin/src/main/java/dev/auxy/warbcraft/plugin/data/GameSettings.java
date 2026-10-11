package dev.auxy.warbcraft.plugin.data;

/**
 * Genel oyun ayarlari (config.yml -> oyun).
 *
 * @param dayLengthMinutes  bir sefer gununun gercek dakika karsiligi (maas, uretim, gorev yenileme)
 * @param recruitCost       kademe 1 asker basina toplama ucreti
 * @param startingDenars    yeni oyuncunun baslangic parasi
 * @param banditBounty      haydut basina odul
 * @param clanCost          klan kurma ucreti
 * @param kingdomMinClanTier krallik kurmak icin gereken klan kademesi
 * @param vassalMinRelation NPC kralliga bagli olmak icin kralla gereken iliski
 */
public record GameSettings(
        int dayLengthMinutes,
        int recruitCost,
        int startingDenars,
        int banditBounty,
        int clanCost,
        int kingdomMinClanTier,
        int vassalMinRelation) {
}
