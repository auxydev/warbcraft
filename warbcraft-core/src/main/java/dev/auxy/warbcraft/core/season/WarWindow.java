package dev.auxy.warbcraft.core.season;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Kusatmalarin yapilabilecegi zaman araligi (orn. Cuma-Pazar 20:00-23:00). Boylece cevrimdisi
 * oyuncunun kalesi gece yarisi dusmez. Aralik ayni gun icinde kalmalidir.
 */
public record WarWindow(Set<DayOfWeek> days, LocalTime start, LocalTime end) {

    public WarWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (days.isEmpty()) {
            throw new IllegalArgumentException("Savas penceresi en az bir gun icermeli");
        }
        days = Set.copyOf(EnumSet.copyOf(days));
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Savas penceresi baslangici bitisten once olmali");
        }
    }

    public boolean contains(ZonedDateTime time) {
        LocalTime local = time.toLocalTime();
        return days.contains(time.getDayOfWeek()) && !local.isBefore(start) && local.isBefore(end);
    }
}
