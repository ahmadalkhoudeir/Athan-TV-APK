package com.alkhoudeir.athantv;

import android.icu.util.Calendar;
import android.icu.util.IslamicCalendar;
import android.icu.util.TimeZone;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Hijri (Umm al-Qura) date using Android's built-in ICU calendar, with a +/- day adjustment. */
public final class Hijri {

    public static final String[] MONTHS_EN = {
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula",
            "Jumada al-Akhirah", "Rajab", "Sha'ban", "Ramadan", "Shawwal",
            "Dhu al-Qi'dah", "Dhu al-Hijjah"};

    public static final String[] MONTHS_AR = {
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى",
            "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال",
            "ذو القعدة", "ذو الحجة"};

    public final int day;
    public final int monthIndex; // 0 = Muharram ... 8 = Ramadan
    public final int year;

    private Hijri(int day, int monthIndex, int year) {
        this.day = day;
        this.monthIndex = monthIndex;
        this.year = year;
    }

    public static Hijri of(LocalDate date, int adjustDays) {
        LocalDate d = date.plusDays(adjustDays);
        IslamicCalendar cal = new IslamicCalendar(TimeZone.GMT_ZONE);
        try {
            cal.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        } catch (Throwable ignored) {
            // Older ICU builds fall back to the default (civil) Islamic calendar.
        }
        // Noon UTC avoids any edge effects at midnight.
        long millis = d.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
        cal.setTimeInMillis(millis);
        return new Hijri(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH), cal.get(Calendar.YEAR));
    }

    public boolean isRamadan() {
        return monthIndex == 8;
    }

    public String english() {
        return day + " " + MONTHS_EN[monthIndex] + " " + year + " AH";
    }

    public String arabic() {
        return toArabicDigits(day) + " " + MONTHS_AR[monthIndex] + " " + toArabicDigits(year) + " هـ";
    }

    static String toArabicDigits(int n) {
        String s = Integer.toString(n);
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            b.append(c >= '0' && c <= '9' ? (char) ('٠' + (c - '0')) : c);
        }
        return b.toString();
    }
}
