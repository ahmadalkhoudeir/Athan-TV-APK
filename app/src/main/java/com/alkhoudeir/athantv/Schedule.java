package com.alkhoudeir.athantv;

import com.alkhoudeir.athantv.core.PrayerCalc;
import com.alkhoudeir.athantv.core.PrayerCalc.DayTimes;
import com.alkhoudeir.athantv.core.PrayerCalc.Event;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Turns settings into concrete, adjusted times for a day and finds the next prayer. */
public final class Schedule {

    /** A single upcoming prayer. */
    public static final class Upcoming {
        public final Event event;
        public final ZonedDateTime time;

        Upcoming(Event event, ZonedDateTime time) {
            this.event = event;
            this.time = time;
        }
    }

    private final AppSettings s;
    private final ZoneId zone;

    public Schedule(AppSettings settings) {
        this.s = settings;
        this.zone = ZoneId.systemDefault();
    }

    public ZoneId zone() {
        return zone;
    }

    /** Times for a local date, with per-prayer minute adjustments applied. */
    public ZonedDateTime[] day(LocalDate date) {
        boolean ramadan = Hijri.of(date, s.hijriAdjust()).isRamadan();
        DayTimes t = PrayerCalc.compute(date, s.lat(), s.lng(), zone, s.method(), s.madhab(), s.highLat(), ramadan);
        ZonedDateTime[] out = new ZonedDateTime[Event.values().length];
        for (Event e : Event.values()) {
            ZonedDateTime v = t.get(e);
            out[e.ordinal()] = v == null ? null : v.plusMinutes(s.offset(e));
        }
        return out;
    }

    /**
     * Next prayer strictly after {@code now}. When {@code onlyEnabled} is true, prayers whose
     * mode is OFF are skipped. Looks up to three days ahead (covers any edge case).
     */
    public Upcoming next(ZonedDateTime now, boolean onlyEnabled) {
        LocalDate d = now.withZoneSameInstant(zone).toLocalDate();
        for (int i = 0; i < 3; i++) {
            ZonedDateTime[] t = day(d.plusDays(i));
            for (Event e : Event.values()) {
                if (!e.isPrayer()) continue;
                if (onlyEnabled && s.mode(e) == AppSettings.Mode.OFF) continue;
                ZonedDateTime v = t[e.ordinal()];
                if (v != null && v.isAfter(now)) return new Upcoming(e, v);
            }
        }
        return null;
    }

    /** Display name, with Dhuhr shown as Jumu'ah on Fridays. */
    public static String englishName(Event e, ZonedDateTime when) {
        if (e == Event.DHUHR && when != null && when.getDayOfWeek() == DayOfWeek.FRIDAY) return "Jumu'ah";
        return e.english;
    }

    public static String arabicName(Event e, ZonedDateTime when) {
        if (e == Event.DHUHR && when != null && when.getDayOfWeek() == DayOfWeek.FRIDAY) return "الجمعة";
        return e.arabic;
    }

    public static String formatTime(ZonedDateTime t, boolean use24h) {
        if (t == null) return "--:--";
        return t.format(DateTimeFormatter.ofPattern(use24h ? "HH:mm" : "h:mm a", Locale.US));
    }
}
