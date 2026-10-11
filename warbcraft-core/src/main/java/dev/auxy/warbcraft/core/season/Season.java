package dev.auxy.warbcraft.core.season;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bir sezonun zaman cizelgesi. Sezon sonunda dunya sifirlanir; siralama ve odul sezon puanina
 * gore verilir.
 */
public final class Season {

    private final int number;
    private final Instant start;
    private final ZoneId zone;
    private final Duration preparation;
    private final Duration campaign;
    private final Duration finalPhase;
    private final Duration siegeNotice;
    private final List<WarWindow> warWindows;

    public Season(int number, Instant start, ZoneId zone, int preparationDays, int campaignDays, int finalDays,
                  int siegeNoticeHours, List<WarWindow> warWindows) {
        if (number <= 0) {
            throw new IllegalArgumentException("Sezon numarasi pozitif olmali");
        }
        if (preparationDays < 0 || campaignDays <= 0 || finalDays < 0 || siegeNoticeHours < 0) {
            throw new IllegalArgumentException("Sezon sureleri gecersiz");
        }
        if (warWindows.isEmpty()) {
            throw new IllegalArgumentException("En az bir savas penceresi tanimlanmali");
        }
        this.number = number;
        this.start = Objects.requireNonNull(start, "start");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.preparation = Duration.ofDays(preparationDays);
        this.campaign = Duration.ofDays(campaignDays);
        this.finalPhase = Duration.ofDays(finalDays);
        this.siegeNotice = Duration.ofHours(siegeNoticeHours);
        this.warWindows = List.copyOf(warWindows);
    }

    public int number() {
        return number;
    }

    public Instant start() {
        return start;
    }

    public Instant end() {
        return start.plus(preparation).plus(campaign).plus(finalPhase);
    }

    public ZoneId zone() {
        return zone;
    }

    public Duration siegeNotice() {
        return siegeNotice;
    }

    public List<WarWindow> warWindows() {
        return warWindows;
    }

    public SeasonPhase phaseAt(Instant now) {
        if (now.isBefore(start)) {
            return SeasonPhase.NOT_STARTED;
        }
        Instant campaignStart = start.plus(preparation);
        if (now.isBefore(campaignStart)) {
            return SeasonPhase.PREPARATION;
        }
        Instant finalStart = campaignStart.plus(campaign);
        if (now.isBefore(finalStart)) {
            return SeasonPhase.CAMPAIGN;
        }
        if (now.isBefore(end())) {
            return SeasonPhase.FINAL;
        }
        return SeasonPhase.ENDED;
    }

    /** Sefer doneminde ve bir savas penceresinin icinde miyiz? */
    public boolean isWarWindowOpen(Instant now) {
        if (!phaseAt(now).warsAllowed()) {
            return false;
        }
        ZonedDateTime local = now.atZone(zone);
        for (WarWindow window : warWindows) {
            if (window.contains(local)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code from} anindan itibaren acik olan ilk savas penceresi ani. Sezon bitmeden pencere
     * yoksa bos doner.
     */
    public Optional<Instant> nextWarWindow(Instant from) {
        Instant end = end();
        Instant cursor = from;
        if (phaseAt(cursor) == SeasonPhase.NOT_STARTED || phaseAt(cursor) == SeasonPhase.PREPARATION) {
            cursor = start.plus(preparation);
        }
        if (!cursor.isBefore(end)) {
            return Optional.empty();
        }
        if (isWarWindowOpen(cursor)) {
            return Optional.of(cursor);
        }
        Instant best = null;
        LocalDate day = cursor.atZone(zone).toLocalDate();
        LocalDate lastDay = end.atZone(zone).toLocalDate();
        while (best == null && !day.isAfter(lastDay)) {
            for (WarWindow window : warWindows) {
                if (!window.days().contains(day.getDayOfWeek())) {
                    continue;
                }
                Instant opens = day.atTime(window.start()).atZone(zone).toInstant();
                if (!opens.isBefore(cursor) && opens.isBefore(end) && (best == null || opens.isBefore(best))) {
                    best = opens;
                }
            }
            day = day.plusDays(1);
        }
        return Optional.ofNullable(best);
    }

    /**
     * Kusatma ilanindan sonra kusatmanin baslayabilecegi en erken an: ihbar suresi doldugunda
     * acik olan (veya ilk acilan) savas penceresi. Savunanlar boylece hazirlanabilir.
     */
    public Optional<Instant> earliestSiegeStart(Instant declaredAt) {
        if (!phaseAt(declaredAt).warsAllowed()) {
            return Optional.empty();
        }
        return nextWarWindow(declaredAt.plus(siegeNotice));
    }
}
