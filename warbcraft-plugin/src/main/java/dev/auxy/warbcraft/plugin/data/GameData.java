package dev.auxy.warbcraft.plugin.data;

import dev.auxy.warbcraft.core.economy.Good;
import dev.auxy.warbcraft.core.faction.Realm;
import dev.auxy.warbcraft.core.npc.NpcBalance;
import dev.auxy.warbcraft.core.npc.NpcKing;
import dev.auxy.warbcraft.core.season.Season;
import dev.auxy.warbcraft.core.troop.TroopTree;
import org.bukkit.Material;

import java.util.Map;

/**
 * Yapilandirma dosyalarindan okunan baslangic verisi.
 *
 * @param goodItems       mal kimligi -> oyundaki esya
 * @param villageProduce  koy kimligi -> urettigi mal
 * @param garrisonSizes   sehir/kale kimligi -> taban garnizon boyutu
 * @param cultureNames    kultur kimligi -> gorunen ad
 * @param cultureSkins    kultur kimligi -> askerlerin skini
 * @param bandits         haydut cetesi turleri ve dogma ayarlari
 */
public record GameData(
        TroopTree tree,
        Realm realm,
        Season season,
        NpcBalance balance,
        GameSettings settings,
        Map<String, NpcKing> kings,
        Map<String, Good> goods,
        Map<String, Material> goodItems,
        Map<String, SettlementSite> sites,
        Map<String, String> villageProduce,
        Map<String, Integer> garrisonSizes,
        Map<String, String> cultureNames,
        Map<String, Skin> cultureSkins,
        BanditData bandits) {
}
