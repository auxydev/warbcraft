package dev.auxy.warbcraft.core.season;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonTest {

    static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    /** 5 Ekim 2026 Pazartesi baslar: 7 gun hazirlik, 21 gun sefer, 7 gun son muharebeler. */
    static Season season() {
        WarWindow weekend = new WarWindow(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                LocalTime.of(20, 0), LocalTime.of(23, 0));
        return new Season(1, at("2026-10-05T00:00"), IST, 7, 21, 7, 24, List.of(weekend));
    }

    static Instant at(String local) {
        return LocalDateTime.parse(local).atZone(IST).toInstant();
    }

    @Test
    void phasesFollowSchedule() {
        Season season = season();
        assertEquals(SeasonPhase.NOT_STARTED, season.phaseAt(at("2026-10-04T23:59")));
        assertEquals(SeasonPhase.PREPARATION, season.phaseAt(at("2026-10-05T00:00")));
        assertEquals(SeasonPhase.CAMPAIGN, season.phaseAt(at("2026-10-12T00:00")));
        assertEquals(SeasonPhase.FINAL, season.phaseAt(at("2026-11-02T00:00")));
        assertEquals(SeasonPhase.ENDED, season.phaseAt(at("2026-11-09T00:00")));
        assertEquals(at("2026-11-09T00:00"), season.end());
    }

    @Test
    void warWindowOnlyOpensDuringCampaignEvenings() {
        Season season = season();
        assertTrue(season.isWarWindowOpen(at("2026-10-16T21:00")));
        assertFalse(season.isWarWindowOpen(at("2026-10-16T19:59")));
        assertFalse(season.isWarWindowOpen(at("2026-10-16T23:00")));
        assertFalse(season.isWarWindowOpen(at("2026-10-15T21:00")), "Persembe pencere yok");
        assertFalse(season.isWarWindowOpen(at("2026-10-09T21:00")), "hazirlik doneminde savas yok");
    }

    @Test
    void nextWarWindow() {
        Season season = season();
        assertEquals(Optional.of(at("2026-10-16T20:00")), season.nextWarWindow(at("2026-10-12T10:00")));
        assertEquals(Optional.of(at("2026-10-16T20:00")), season.nextWarWindow(at("2026-10-06T10:00")));
        assertEquals(Optional.of(at("2026-10-16T21:30")), season.nextWarWindow(at("2026-10-16T21:30")));
        assertEquals(Optional.of(at("2026-10-17T20:00")), season.nextWarWindow(at("2026-10-16T23:30")));
        assertEquals(Optional.empty(), season.nextWarWindow(at("2026-11-08T23:30")));
    }

    @Test
    void siegeNeedsNoticeAndWarWindow() {
        Season season = season();
        assertEquals(Optional.of(at("2026-10-17T21:00")), season.earliestSiegeStart(at("2026-10-16T21:00")));
        assertEquals(Optional.of(at("2026-10-16T20:00")), season.earliestSiegeStart(at("2026-10-15T10:00")));
        assertEquals(Optional.empty(), season.earliestSiegeStart(at("2026-10-07T10:00")));
    }

    @Test
    void validation() {
        assertThrows(IllegalArgumentException.class,
                () -> new WarWindow(Set.of(DayOfWeek.MONDAY), LocalTime.of(22, 0), LocalTime.of(20, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new WarWindow(Set.of(), LocalTime.of(20, 0), LocalTime.of(22, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Season(1, Instant.EPOCH, IST, 1, 1, 1, 1, List.of()));
    }
}
