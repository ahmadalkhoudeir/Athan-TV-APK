package com.alkhoudeir.athantv.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Astronomical prayer-time calculator. Pure Java with no Android imports, so it can be
 * unit-tested on a desktop JVM.
 *
 * The math follows the widely published solar-position approach used by PrayTimes.org and
 * the Aladhan API: sun declination and equation of time from the date, then the hour angle
 * at which the sun reaches each prayer's depression angle. Results are rounded to the
 * nearest minute.
 */
public final class PrayerCalc {

    private PrayerCalc() {}

    /** Calculation methods. Angles in degrees; ishaMinutes > 0 means "Isha = Maghrib + N min". */
    public enum Method {
        ISNA("Islamic Society of North America (ISNA)", 15.0, 15.0, 0),
        MWL("Muslim World League", 18.0, 17.0, 0),
        EGYPT("Egyptian General Authority of Survey", 19.5, 17.5, 0),
        MAKKAH("Umm al-Qura University, Makkah", 18.5, 0, 90),
        KARACHI("University of Islamic Sciences, Karachi", 18.0, 18.0, 0),
        GULF("Gulf Region", 19.5, 0, 90),
        KUWAIT("Kuwait", 18.0, 17.5, 0),
        QATAR("Qatar", 18.0, 0, 90),
        SINGAPORE("Singapore (MUIS)", 20.0, 18.0, 0),
        TURKEY("Diyanet, Turkey", 18.0, 17.0, 0),
        DUBAI("Dubai", 18.2, 18.2, 0),
        FRANCE("UOIF, France", 12.0, 12.0, 0),
        RUSSIA("Spiritual Administration of Muslims of Russia", 16.0, 15.0, 0);

        public final String label;
        public final double fajrAngle;
        public final double ishaAngle;
        public final int ishaMinutes;

        Method(String label, double fajrAngle, double ishaAngle, int ishaMinutes) {
            this.label = label;
            this.fajrAngle = fajrAngle;
            this.ishaAngle = ishaAngle;
            this.ishaMinutes = ishaMinutes;
        }
    }

    /** Asr shadow rule. */
    public enum Madhab {
        STANDARD("Standard (Shafi'i, Maliki, Hanbali)", 1),
        HANAFI("Hanafi", 2);

        public final String label;
        public final int shadowFactor;

        Madhab(String label, int shadowFactor) {
            this.label = label;
            this.shadowFactor = shadowFactor;
        }
    }

    /** Fallback used when Fajr/Isha angles are not reached (far north in summer). */
    public enum HighLatRule {
        ANGLE_BASED("Angle-based (default)"),
        MIDDLE_OF_NIGHT("Middle of the night"),
        ONE_SEVENTH("One-seventh of the night"),
        NONE("None");

        public final String label;

        HighLatRule(String label) {
            this.label = label;
        }
    }

    /** The six daily events, in order. Sunrise is not a prayer but is shown on the dashboard. */
    public enum Event {
        FAJR("Fajr", "الفجر"),
        SUNRISE("Sunrise", "الشروق"),
        DHUHR("Dhuhr", "الظهر"),
        ASR("Asr", "العصر"),
        MAGHRIB("Maghrib", "المغرب"),
        ISHA("Isha", "العشاء");

        public final String english;
        public final String arabic;

        Event(String english, String arabic) {
            this.english = english;
            this.arabic = arabic;
        }

        public boolean isPrayer() {
            return this != SUNRISE;
        }
    }

    /** One day's results. Index with {@link Event#ordinal()}. */
    public static final class DayTimes {
        public final LocalDate date;
        private final ZonedDateTime[] times;

        DayTimes(LocalDate date, ZonedDateTime[] times) {
            this.date = date;
            this.times = times;
        }

        public ZonedDateTime get(Event e) {
            return times[e.ordinal()];
        }
    }

    // Position of the Kaaba (degrees).
    public static final double KAABA_LAT = 21.4225;
    public static final double KAABA_LNG = 39.8262;

    private static final double SUNRISE_ANGLE = 0.833; // refraction + solar semi-diameter

    /**
     * Extra refinement passes. 0 reproduces the single-pass PrayTimes/Aladhan convention
     * (validated to match Aladhan exactly for most days); tests may raise it.
     */
    static int refinePasses = 0;

    /**
     * Calculates the times for one local calendar date.
     *
     * @param isRamadan only affects Umm al-Qura, whose Isha becomes 120 minutes after Maghrib.
     */
    public static DayTimes compute(LocalDate date, double lat, double lng, ZoneId zone,
                                   Method method, Madhab madhab, HighLatRule rule,
                                   boolean isRamadan) {
        // Julian day at 0h UT, shifted by longitude so "day portions" refer to local solar time.
        double jDate = julianDay(date) - lng / (15.0 * 24.0);

        // First pass uses typical day positions; second pass refines with the first results.
        double[] t = {5, 6, 12, 13, 18, 18, 18}; // fajr, sunrise, dhuhr, asr, sunset, maghrib, isha
        for (int pass = 0; pass < refinePasses; pass++) {
            double[] p = new double[t.length];
            for (int i = 0; i < t.length; i++) p[i] = t[i] / 24.0;
            double fajr = sunAngleTime(jDate, lat, method.fajrAngle, p[0], true);
            double sunrise = sunAngleTime(jDate, lat, SUNRISE_ANGLE, p[1], true);
            double dhuhr = midDay(jDate, p[2]);
            double asr = asrTime(jDate, lat, madhab.shadowFactor, p[3]);
            double sunset = sunAngleTime(jDate, lat, SUNRISE_ANGLE, p[4], false);
            double maghrib = sunset;
            double isha = method.ishaMinutes > 0 ? Double.NaN
                    : sunAngleTime(jDate, lat, method.ishaAngle, p[6], false);
            t = new double[]{fajr, sunrise, dhuhr, asr, sunset, maghrib, isha};
            // Keep NaN-free positions for the next pass.
            if (Double.isNaN(t[0])) t[0] = 5;
            if (Double.isNaN(t[6]) && method.ishaMinutes == 0) t[6] = 18;
        }

        // Recompute final values from the refined positions (NaN preserved for high-lat handling).
        double[] p = new double[t.length];
        for (int i = 0; i < t.length; i++) p[i] = t[i] / 24.0;
        double fajr = sunAngleTime(jDate, lat, method.fajrAngle, p[0], true);
        double sunrise = sunAngleTime(jDate, lat, SUNRISE_ANGLE, p[1], true);
        double dhuhr = midDay(jDate, p[2]);
        double asr = asrTime(jDate, lat, madhab.shadowFactor, p[3]);
        double sunset = sunAngleTime(jDate, lat, SUNRISE_ANGLE, p[4], false);
        double maghrib = sunset;
        double isha;
        if (method.ishaMinutes > 0) {
            int minutes = (method == Method.MAKKAH && isRamadan) ? 120 : method.ishaMinutes;
            isha = maghrib + minutes / 60.0;
        } else {
            isha = sunAngleTime(jDate, lat, method.ishaAngle, p[6], false);
        }

        // High-latitude safety: cap Fajr/Isha to a portion of the night.
        if (rule != HighLatRule.NONE && !Double.isNaN(sunrise) && !Double.isNaN(sunset)) {
            double night = fixHour(sunrise - sunset + 24); // sunset -> next sunrise
            fajr = adjustHighLat(fajr, sunrise, method.fajrAngle, night, true, rule);
            if (method.ishaMinutes == 0) {
                isha = adjustHighLat(isha, sunset, method.ishaAngle, night, false, rule);
            }
        }

        double[] local = {fajr, sunrise, dhuhr, asr, maghrib, isha};
        ZonedDateTime[] out = new ZonedDateTime[local.length];
        ZonedDateTime utcMidnight = date.atStartOfDay(ZoneOffset.UTC);
        for (int i = 0; i < local.length; i++) {
            if (Double.isNaN(local[i])) {
                out[i] = null;
                continue;
            }
            double utHours = local[i] - lng / 15.0; // local solar hours -> UT hours
            long seconds = Math.round(utHours * 3600.0);
            ZonedDateTime exact = utcMidnight.plusSeconds(seconds).withZoneSameInstant(zone);
            out[i] = roundToMinute(exact);
        }
        return new DayTimes(date, out);
    }

    /** Qibla bearing in degrees clockwise from true north. */
    public static double qiblaBearing(double lat, double lng) {
        double phi = Math.toRadians(lat);
        double phiK = Math.toRadians(KAABA_LAT);
        double dLambda = Math.toRadians(KAABA_LNG - lng);
        double y = Math.sin(dLambda);
        double x = Math.cos(phi) * Math.tan(phiK) - Math.sin(phi) * Math.cos(dLambda);
        double deg = Math.toDegrees(Math.atan2(y, x));
        return (deg + 360.0) % 360.0;
    }

    /** Great-circle distance to the Kaaba in kilometres. */
    public static double distanceToKaabaKm(double lat, double lng) {
        double r = 6371.0088;
        double p1 = Math.toRadians(lat), p2 = Math.toRadians(KAABA_LAT);
        double dp = p2 - p1, dl = Math.toRadians(KAABA_LNG - lng);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    // ---------------------------------------------------------------- astronomy

    static double julianDay(LocalDate d) {
        int year = d.getYear(), month = d.getMonthValue(), day = d.getDayOfMonth();
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** Returns {declination (deg), equation of time (hours)}. */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0;
        double eqt = q / 15.0 - fixHour(ra);
        double decl = darcsin(dsin(e) * dsin(l));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jDate, double portion) {
        double eqt = sunPosition(jDate + portion)[1];
        return fixHour(12 - eqt);
    }

    private static double sunAngleTime(double jDate, double lat, double angle, double portion,
                                       boolean beforeNoon) {
        double decl = sunPosition(jDate + portion)[0];
        double noon = midDay(jDate, portion);
        double cosH = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat));
        if (cosH < -1 || cosH > 1) return Double.NaN; // sun never reaches this angle today
        double h = darccos(cosH) / 15.0;
        return noon + (beforeNoon ? -h : h);
    }

    private static double asrTime(double jDate, double lat, int factor, double portion) {
        double decl = sunPosition(jDate + portion)[0];
        double angle = -darccot(factor + dtan(Math.abs(lat - decl)));
        return sunAngleTime(jDate, lat, angle, portion, false);
    }

    private static double adjustHighLat(double time, double base, double angle, double night,
                                        boolean beforeBase, HighLatRule rule) {
        double portion;
        switch (rule) {
            case MIDDLE_OF_NIGHT: portion = 0.5; break;
            case ONE_SEVENTH: portion = 1.0 / 7.0; break;
            case ANGLE_BASED:
            default: portion = angle / 60.0; break;
        }
        portion *= night;
        double diff = beforeBase ? fixHour(base - time) : fixHour(time - base);
        if (Double.isNaN(time) || diff > portion) {
            return base + (beforeBase ? -portion : portion);
        }
        return time;
    }

    private static ZonedDateTime roundToMinute(ZonedDateTime t) {
        ZonedDateTime floor = t.truncatedTo(ChronoUnit.MINUTES);
        return t.getSecond() >= 30 ? floor.plusMinutes(1) : floor;
    }

    // ---------------------------------------------------------------- degree helpers

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double darccot(double x) { return Math.toDegrees(Math.atan(1.0 / x)); }

    private static double fixAngle(double a) { return fix(a, 360); }
    private static double fixHour(double a) { return fix(a, 24); }

    private static double fix(double a, double b) {
        a = a - b * Math.floor(a / b);
        return a < 0 ? a + b : a;
    }

    /** Convenience for callers that need "now" as an Instant (kept here for testability). */
    public static Instant now() {
        return Instant.now();
    }
}
