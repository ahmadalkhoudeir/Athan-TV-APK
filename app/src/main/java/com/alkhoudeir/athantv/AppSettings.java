package com.alkhoudeir.athantv;

import android.content.Context;
import android.content.SharedPreferences;

import com.alkhoudeir.athantv.core.PrayerCalc.Event;
import com.alkhoudeir.athantv.core.PrayerCalc.HighLatRule;
import com.alkhoudeir.athantv.core.PrayerCalc.Madhab;
import com.alkhoudeir.athantv.core.PrayerCalc.Method;

/** Typed wrapper around SharedPreferences. Every setting has one key and one default. */
public final class AppSettings {

    /** What happens at a prayer time. */
    public enum Mode {
        ATHAN("Athan + screen alert"),
        BANNER("Silent screen alert"),
        OFF("Off");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    public enum OverlayStyle {
        FULL("Full screen"),
        BANNER("Lower-third banner");

        public final String label;

        OverlayStyle(String label) {
            this.label = label;
        }
    }

    /** A preset location. Coordinates are decimal degrees. */
    public static final class City {
        public final String name;
        public final double lat;
        public final double lng;

        City(String name, double lat, double lng) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }
    }

    public static final City[] CITIES = {
            new City("New Britain, CT", 41.6612, -72.7795),
            new City("West Hartford, CT", 41.7621, -72.7420),
            new City("Hartford, CT", 41.7658, -72.6734),
            new City("New Haven, CT", 41.3083, -72.9279),
            new City("New York, NY", 40.7128, -74.0060),
            new City("Boston, MA", 42.3601, -71.0589),
            new City("Providence, RI", 41.8240, -71.4128),
            new City("Makkah", 21.4225, 39.8262),
            new City("Madinah", 24.4672, 39.6111),
    };

    private static final String FILE = "athan_settings";
    private final SharedPreferences p;

    public AppSettings(Context ctx) {
        p = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- location

    public String cityName() { return p.getString("city", CITIES[0].name); }
    public double lat() { return Double.longBitsToDouble(p.getLong("lat", Double.doubleToLongBits(CITIES[0].lat))); }
    public double lng() { return Double.longBitsToDouble(p.getLong("lng", Double.doubleToLongBits(CITIES[0].lng))); }

    public void setLocation(String name, double lat, double lng) {
        p.edit().putString("city", name)
                .putLong("lat", Double.doubleToLongBits(lat))
                .putLong("lng", Double.doubleToLongBits(lng)).apply();
    }

    // ---------------------------------------------------------------- calculation

    public Method method() { return enumOr(Method.class, p.getString("method", null), Method.ISNA); }
    public void setMethod(Method m) { p.edit().putString("method", m.name()).apply(); }

    public Madhab madhab() { return enumOr(Madhab.class, p.getString("madhab", null), Madhab.STANDARD); }
    public void setMadhab(Madhab m) { p.edit().putString("madhab", m.name()).apply(); }

    public HighLatRule highLat() { return enumOr(HighLatRule.class, p.getString("highlat", null), HighLatRule.ANGLE_BASED); }
    public void setHighLat(HighLatRule r) { p.edit().putString("highlat", r.name()).apply(); }

    /** Minutes added to a calculated time (to match a local mosque). Range -30..30. */
    public int offset(Event e) { return p.getInt("offset_" + e.name(), 0); }
    public void setOffset(Event e, int minutes) { p.edit().putInt("offset_" + e.name(), clamp(minutes, -30, 30)).apply(); }

    // ---------------------------------------------------------------- alerts

    public Mode mode(Event e) {
        if (!e.isPrayer()) return Mode.OFF;
        return enumOr(Mode.class, p.getString("mode_" + e.name(), null), Mode.ATHAN);
    }
    public void setMode(Event e, Mode m) { p.edit().putString("mode_" + e.name(), m.name()).apply(); }

    /** Minutes before each prayer to show a small reminder banner; 0 = off. */
    public int reminderMinutes() { return p.getInt("reminder", 0); }
    public void setReminderMinutes(int m) { p.edit().putInt("reminder", clamp(m, 0, 60)).apply(); }

    public boolean reminderChime() { return p.getBoolean("reminder_chime", true); }
    public void setReminderChime(boolean b) { p.edit().putBoolean("reminder_chime", b).apply(); }

    public OverlayStyle overlayStyle() { return enumOr(OverlayStyle.class, p.getString("overlay", null), OverlayStyle.FULL); }
    public void setOverlayStyle(OverlayStyle s) { p.edit().putString("overlay", s.name()).apply(); }

    /** Seconds the after-athan supplication stays on screen; 0 = skip. */
    public int duaSeconds() { return p.getInt("dua_seconds", 45); }
    public void setDuaSeconds(int s) { p.edit().putInt("dua_seconds", clamp(s, 0, 300)).apply(); }

    /** Athan player volume as a percentage of the device's media volume. */
    public int volumePercent() { return p.getInt("volume", 100); }
    public void setVolumePercent(int v) { p.edit().putInt("volume", clamp(v, 10, 100)).apply(); }

    /** true = ask other apps to pause and resume afterwards; false = ask them to stop. */
    public boolean pauseOthers() { return p.getBoolean("pause_others", true); }
    public void setPauseOthers(boolean b) { p.edit().putBoolean("pause_others", b).apply(); }

    /** Best-effort: wake the streaming device (and the TV over HDMI-CEC) for the athan. */
    public boolean wakeScreen() { return p.getBoolean("wake_screen", false); }
    public void setWakeScreen(boolean b) { p.edit().putBoolean("wake_screen", b).apply(); }

    /** Skip an athan that fires more than this many minutes late (device was off). */
    public int lateLimitMinutes() { return 10; }

    // ---------------------------------------------------------------- display

    public int hijriAdjust() { return p.getInt("hijri_adjust", 0); }
    public void setHijriAdjust(int d) { p.edit().putInt("hijri_adjust", clamp(d, -2, 2)).apply(); }

    public boolean use24h() { return p.getBoolean("use24h", false); }
    public void setUse24h(boolean b) { p.edit().putBoolean("use24h", b).apply(); }

    // ---------------------------------------------------------------- audio metadata

    public String audioLabel(String slot) { return p.getString("audio_label_" + slot, null); }
    public void setAudioLabel(String slot, String label) {
        SharedPreferences.Editor e = p.edit();
        if (label == null) e.remove("audio_label_" + slot); else e.putString("audio_label_" + slot, label);
        e.apply();
    }

    // ---------------------------------------------------------------- helpers

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static <T extends Enum<T>> T enumOr(Class<T> type, String name, T fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
