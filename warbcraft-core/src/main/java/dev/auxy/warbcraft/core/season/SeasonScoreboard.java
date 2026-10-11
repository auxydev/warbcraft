package dev.auxy.warbcraft.core.season;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Realm;
import dev.auxy.warbcraft.core.faction.Settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sezon sonu siralamasi. Puan = elde tutulan yerlesimler + klanlarin unu. Yalnizca oyuncu
 * kralliklari siralanir; NPC kralliklar dunyanin parcasidir, yarismaci degildir.
 */
public final class SeasonScoreboard {

    public static final int TOWN_POINTS = 30;
    public static final int CASTLE_POINTS = 20;
    public static final int VILLAGE_POINTS = 5;
    /** Her bu kadar un 1 puan eder. */
    public static final int RENOWN_PER_POINT = 50;

    public record Entry(Kingdom kingdom, int points) {
    }

    private SeasonScoreboard() {
    }

    public static List<Entry> rank(Realm realm) {
        List<Entry> entries = new ArrayList<>();
        for (Kingdom kingdom : realm.kingdoms()) {
            if (!kingdom.npc()) {
                entries.add(new Entry(kingdom, score(realm, kingdom.id())));
            }
        }
        entries.sort(Comparator.comparingInt(Entry::points).reversed()
                .thenComparing(entry -> entry.kingdom().id()));
        return entries;
    }

    public static int score(Realm realm, String kingdomId) {
        int points = 0;
        for (Settlement settlement : realm.settlementsOfKingdom(kingdomId)) {
            points += switch (settlement.type()) {
                case TOWN -> TOWN_POINTS;
                case CASTLE -> CASTLE_POINTS;
                case VILLAGE -> VILLAGE_POINTS;
            };
        }
        for (Clan clan : realm.clansOfKingdom(kingdomId)) {
            points += clan.renown() / RENOWN_PER_POINT;
        }
        return points;
    }
}
