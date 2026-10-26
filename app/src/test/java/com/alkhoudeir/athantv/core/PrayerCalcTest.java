package com.alkhoudeir.athantv.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.alkhoudeir.athantv.core.PrayerCalc.DayTimes;
import com.alkhoudeir.athantv.core.PrayerCalc.Event;
import com.alkhoudeir.athantv.core.PrayerCalc.HighLatRule;
import com.alkhoudeir.athantv.core.PrayerCalc.Madhab;
import com.alkhoudeir.athantv.core.PrayerCalc.Method;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Compares the calculator with reference timetables downloaded from the Aladhan API
 * (api.aladhan.com/v1/calendar) on 21 Oct 2026. CSV columns: date, Fajr, Sunrise, Dhuhr, Asr,
 * Maghrib, Isha, Hijri month. Every value must be within 1 minute, and most must match exactly.
 */
public class PrayerCalcTest {

    private static final String NB = "America/New_York";

    @Test
    public void isnaStandardNewBritainOctober2026() throws Exception {
        check("isna_newbritain_2026-10.csv", 41.6612, -72.7795, NB, Method.ISNA, Madhab.STANDARD);
    }

    @Test
    public void mwlHanafiNewBritainJune2026() throws Exception {
        check("mwl_hanafi_newbritain_2026-06.csv", 41.6612, -72.7795, NB, Method.MWL, Madhab.HANAFI);
    }

    @Test
    public void ummAlQuraMakkahMarch2026IncludingRamadanIsha() throws Exception {
        check("makkah_ummalqura_2026-03.csv", 21.4225, 39.8262, "Asia/Riyadh", Method.MAKKAH, Madhab.STANDARD);
    }

    @Test
    public void daylightSavingFallBack() {
        ZoneId z = ZoneId.of(NB);
        DayTimes sat = PrayerCalc.compute(LocalDate.of(2026, 10, 31), 41.6612, -72.7795, z,
                Method.ISNA, Madhab.STANDARD, HighLatRule.ANGLE_BASED, false);
        DayTimes sun = PrayerCalc.compute(LocalDate.of(2026, 11, 1), 41.6612, -72.7795, z,
                Method.ISNA, Madhab.STANDARD, HighLatRule.ANGLE_BASED, false);
        // Clocks go back one hour on 1 Nov 2026, so Dhuhr moves from 12:35 EDT to 11:35 EST.
        assertEquals(LocalTime.of(12, 35), sat.get(Event.DHUHR).toLocalTime());
        assertEquals(LocalTime.of(11, 35), sun.get(Event.DHUHR).toLocalTime());
    }

    @Test
    public void qiblaBearings() {
        // New York City is commonly published as about 58.5 degrees from true north.
        assertEquals(58.5, PrayerCalc.qiblaBearing(40.7128, -74.0060), 0.1);
        assertEquals(119.0, PrayerCalc.qiblaBearing(51.5074, -0.1278), 0.1); // London
    }

    private void check(String file, double lat, double lng, String tz, Method m, Madhab mad) throws Exception {
        int total = 0, exact = 0;
        try (InputStream in = getClass().getResourceAsStream("/aladhan/" + file);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                String[] dmy = p[0].split("-");
                LocalDate date = LocalDate.of(Integer.parseInt(dmy[2]), Integer.parseInt(dmy[1]), Integer.parseInt(dmy[0]));
                boolean ramadan = Integer.parseInt(p[7].trim()) == 9;
                DayTimes t = PrayerCalc.compute(date, lat, lng, ZoneId.of(tz), m, mad, HighLatRule.ANGLE_BASED, ramadan);
                for (int i = 0; i < 6; i++) {
                    LocalTime ref = LocalTime.parse(p[i + 1].trim());
                    LocalTime got = t.get(Event.values()[i]).toLocalTime();
                    int diff = Math.abs(got.toSecondOfDay() - ref.toSecondOfDay()) / 60;
                    assertTrue(file + " " + p[0] + " " + Event.values()[i] + ": ours " + got + " vs " + ref,
                            diff <= 1);
                    total++;
                    if (diff == 0) exact++;
                }
            }
        }
        assertTrue("too few rows", total >= 150);
        assertTrue(file + ": only " + exact + "/" + total + " exact", exact * 100 / total >= 85);
    }
}
