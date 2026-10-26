package com.alkhoudeir.athantv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.alkhoudeir.athantv.core.PrayerCalc;
import com.alkhoudeir.athantv.core.PrayerCalc.Event;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** TV dashboard: clock, Hijri date, today's times, countdown, Qibla and status. */
public final class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppSettings s;
    private Schedule sched;

    private TextView place, gregorian, hijri, nextTitle, countdown, ramadan, status, qibla;
    private TextClock clock;
    private final LinearLayout[] cards = new LinearLayout[Event.values().length];
    private final TextView[] cardTime = new TextView[Event.values().length];
    private final TextView[] cardMode = new TextView[Event.values().length];
    private final TextView[] cardName = new TextView[Event.values().length];
    private LocalDate shownDate;
    private ZonedDateTime[] today;
    private ZonedDateTime lastNextTime;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = new AppSettings(this);
        setContentView(buildLayout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        sched = new Schedule(s); // picks up time-zone changes
        AlarmScheduler.scheduleAll(this);
        refreshAll();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    // ------------------------------------------------------------------ layout

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 40);
        col.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 24));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // Header: title + place on the left, clock + dates on the right.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Ui.text(this, "Athan TV  ·  مواقيت الصلاة", 26, Ui.GOLD, true));
        place = Ui.text(this, "", 16, Ui.MUTED, false);
        left.addView(place);
        header.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        clock = new TextClock(this);
        clock.setTextSize(44);
        clock.setTextColor(0xFFFFFFFF);
        clock.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        right.addView(clock);
        gregorian = Ui.text(this, "", 16, Ui.MUTED, false);
        gregorian.setGravity(Gravity.END);
        right.addView(gregorian);
        hijri = Ui.text(this, "", 16, Ui.GOLD, false);
        hijri.setGravity(Gravity.END);
        right.addView(hijri);
        header.addView(right);
        col.addView(header);

        // Next prayer + countdown.
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        nextTitle = Ui.text(this, "", 24, Ui.MUTED, false);
        nextTitle.setGravity(Gravity.CENTER);
        countdown = Ui.text(this, "", 60, 0xFFFFFFFF, true);
        countdown.setGravity(Gravity.CENTER);
        ramadan = Ui.text(this, "", 16, Ui.GOLD, false);
        ramadan.setGravity(Gravity.CENTER);
        center.addView(nextTitle);
        center.addView(countdown);
        center.addView(ramadan);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, 0, 1f);
        center.setGravity(Gravity.CENTER);
        col.addView(center, clp);

        // Six cards. Click a prayer card to cycle Athan / Silent / Off.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Event e : Event.values()) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            int p = Ui.dp(this, 12);
            card.setPadding(p, p, p, p);
            card.setFocusable(e.isPrayer());
            card.setClickable(e.isPrayer());
            TextView ar = Ui.text(this, e.arabic, 26, Ui.GOLD, true);
            ar.setGravity(Gravity.CENTER);
            cardName[e.ordinal()] = Ui.text(this, e.english, 16, 0xFFFFFFFF, false);
            cardName[e.ordinal()].setGravity(Gravity.CENTER);
            cardTime[e.ordinal()] = Ui.text(this, "--:--", 28, 0xFFFFFFFF, true);
            cardTime[e.ordinal()].setGravity(Gravity.CENTER);
            cardMode[e.ordinal()] = Ui.text(this, "", 13, Ui.MUTED, false);
            cardMode[e.ordinal()].setGravity(Gravity.CENTER);
            card.addView(ar);
            card.addView(cardName[e.ordinal()]);
            card.addView(cardTime[e.ordinal()]);
            card.addView(cardMode[e.ordinal()]);
            if (e.isPrayer()) {
                card.setOnClickListener(v -> cycleMode(e));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            int m = Ui.dp(this, 6);
            lp.setMargins(m, m, m, m);
            row.addView(card, lp);
            cards[e.ordinal()] = card;
        }
        col.addView(row);

        // Buttons + status. The row scrolls sideways if a TV reports a narrow screen.
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        addButton(buttons, "Settings", v -> startActivity(new Intent(this, SettingsActivity.class)));
        addButton(buttons, "Athan sound", v -> startActivity(new Intent(this, SoundActivity.class)));
        addButton(buttons, "Test now", v -> testAthan());
        addButton(buttons, "Test in 30 s", v -> startDelayedTest(this));
        addButton(buttons, "Stop", v -> AthanService.stop(this));
        addButton(buttons, "Setup check", v -> showSetupCheck(this));
        HorizontalScrollView buttonScroll = new HorizontalScrollView(this);
        buttonScroll.setHorizontalScrollBarEnabled(false);
        buttonScroll.addView(buttons);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.dp(this, 14);
        col.addView(buttonScroll, blp);

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        status = Ui.text(this, "", 15, Ui.MUTED, false);
        qibla = Ui.text(this, "", 15, Ui.MUTED, false);
        qibla.setGravity(Gravity.END);
        foot.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        foot.addView(qibla);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = Ui.dp(this, 10);
        col.addView(foot, flp);

        // Start with focus on the first prayer card so the remote works immediately.
        cards[Event.FAJR.ordinal()].requestFocus();
        return root;
    }

    private void addButton(LinearLayout parent, String label, View.OnClickListener l) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(this, 10);
        parent.addView(Ui.button(this, label, l), lp);
    }

    /**
     * Schedules a real athan alert 30 seconds from now, so you can press Home, start YouTube or
     * Netflix, and watch the alert appear on top of it. Shared with SettingsActivity.
     */
    static void startDelayedTest(Activity a) {
        AlarmScheduler.scheduleTest(a, 30);
        Ui.dialog(a).setTitle("Test in 30 seconds")
                .setMessage("Press OK, then press Home on your remote and start a video in YouTube, "
                        + "Netflix or any app.\n\nIn 30 seconds the athan alert should appear on top of it "
                        + "and the video should pause. Press BACK to stop the test early.")
                .setPositiveButton("OK", null)
                .show();
    }

    // ------------------------------------------------------------------ data

    private void refreshAll() {
        boolean h24 = s.use24h();
        String pattern = h24 ? "HH:mm" : "h:mm a"; // same pattern for both so the app setting wins
        clock.setFormat12Hour(pattern);
        clock.setFormat24Hour(pattern);

        ZonedDateTime now = ZonedDateTime.now(sched.zone());
        shownDate = now.toLocalDate();
        today = sched.day(shownDate);
        Hijri hj = Hijri.of(shownDate, s.hijriAdjust());

        place.setText(s.cityName() + "  ·  " + s.method().label + "  ·  Asr: " + s.madhab().label);
        gregorian.setText(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)));
        hijri.setText(hj.arabic() + "   ·   " + hj.english());

        Schedule.Upcoming next = sched.next(now, false);
        lastNextTime = next == null ? null : next.time;
        for (Event e : Event.values()) {
            int i = e.ordinal();
            ZonedDateTime t = today[i];
            cardTime[i].setText(Schedule.formatTime(t, h24));
            cardName[i].setText(Schedule.englishName(e, t));
            boolean isNext = next != null && next.event == e && t != null
                    && next.time.toLocalDate().equals(shownDate);
            cards[i].setBackground(Ui.card(this, isNext));
            boolean past = t != null && t.isBefore(now) && !isNext;
            cards[i].setAlpha(past ? 0.55f : 1f);
            cardMode[i].setText(e.isPrayer() ? s.mode(e).label : "—");
        }

        if (hj.isRamadan()) {
            ramadan.setText("Ramadan Mubarak  ·  رمضان مبارك  ·  Suhoor ends "
                    + Schedule.formatTime(today[Event.FAJR.ordinal()], h24)
                    + "  ·  Iftar " + Schedule.formatTime(today[Event.MAGHRIB.ordinal()], h24));
            ramadan.setVisibility(View.VISIBLE);
        } else {
            ramadan.setVisibility(View.GONE);
        }

        double q = PrayerCalc.qiblaBearing(s.lat(), s.lng());
        double km = PrayerCalc.distanceToKaabaKm(s.lat(), s.lng());
        qibla.setText(String.format(Locale.US, "Qibla %.1f° from true north (%s)  ·  %,.0f km to Makkah",
                q, compass(q), km));

        status.setText(setupSummary());
        status.setTextColor(setupOk() ? Ui.GREEN_OK : Ui.AMBER);
        updateCountdown();
    }

    private void updateCountdown() {
        if (sched == null) return;
        ZonedDateTime now = ZonedDateTime.now(sched.zone());
        if (!now.toLocalDate().equals(shownDate)) {
            refreshAll(); // midnight rollover
            return;
        }
        Schedule.Upcoming next = sched.next(now, false);
        if (next == null) {
            nextTitle.setText("");
            countdown.setText("");
            return;
        }
        // Repaint the cards when a prayer time passes and "next" moves on.
        if (lastNextTime != null && !next.time.equals(lastNextTime)) {
            refreshAll();
            return;
        }
        Duration d = Duration.between(now, next.time);
        long secs = Math.max(0, d.getSeconds());
        nextTitle.setText("Next: " + Schedule.englishName(next.event, next.time) + "  "
                + Schedule.arabicName(next.event, next.time) + "  at "
                + Schedule.formatTime(next.time, s.use24h()));
        countdown.setText(String.format(Locale.US, "%d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60));
    }

    private void cycleMode(Event e) {
        AppSettings.Mode[] modes = AppSettings.Mode.values();
        AppSettings.Mode next = modes[(s.mode(e).ordinal() + 1) % modes.length];
        s.setMode(e, next);
        AlarmScheduler.scheduleAll(this);
        refreshAll();
    }

    private void testAthan() {
        Schedule.Upcoming n = sched.next(ZonedDateTime.now(sched.zone()), false);
        Event e = n == null ? Event.DHUHR : n.event;
        AthanService.startAlert(this, e, System.currentTimeMillis(), true);
    }

    // ------------------------------------------------------------------ setup checks

    private boolean setupOk() {
        return OverlayController.canShow(this) && AlarmScheduler.canExact(this)
                && AudioStore.has(this, AudioStore.SLOT_REGULAR);
    }

    private String setupSummary() {
        if (!OverlayController.canShow(this)) return "⚠ Overlay permission missing: open Setup check";
        if (!AlarmScheduler.canExact(this)) return "⚠ Exact alarms not allowed: open Setup check";
        if (!AudioStore.has(this, AudioStore.SLOT_REGULAR)) return "⚠ No athan recording yet: open Athan sound";
        return "✓ Ready. Next athan: " + AlarmScheduler.describeNext(this);
    }

    /** Shared by MainActivity and SettingsActivity. */
    static void showSetupCheck(Activity a) {
        boolean overlay = OverlayController.canShow(a);
        boolean exact = AlarmScheduler.canExact(a);
        boolean audio = AudioStore.has(a, AudioStore.SLOT_REGULAR);
        String ip = NetUtil.localIpv4();
        String pkg = a.getPackageName();
        StringBuilder m = new StringBuilder();
        m.append(overlay ? "✓" : "✗").append("  Display over other apps\n");
        m.append(exact ? "✓" : "✗").append("  Exact alarms\n");
        m.append(audio ? "✓" : "✗").append("  Athan recording\n\n");
        m.append("Next: ").append(AlarmScheduler.describeNext(a)).append("\n\n");
        if (!overlay) {
            m.append("Google TV and Fire TV often hide the overlay setting. Grant it once from a computer "
                    + "on the same Wi-Fi with ADB (Developer options > USB/ADB debugging ON):\n\n");
            m.append("adb connect ").append(ip == null ? "<TV-IP>" : ip).append("\n");
            m.append("adb shell appops set ").append(pkg).append(" SYSTEM_ALERT_WINDOW allow\n\n");
        }
        if (!exact) {
            m.append("Allow \"Alarms & reminders\" for Athan TV in Settings > Apps > Special app access.\n\n");
        }
        m.append("TV address: ").append(ip == null ? "unknown" : ip);
        Ui.dialog(a).setTitle("Setup check")
                .setMessage(m.toString())
                .setPositiveButton(overlay ? "Close" : "Try overlay settings", (d, w) -> {
                    if (!overlay) openOverlaySettings(a);
                })
                .setNegativeButton(exact ? null : "Alarm settings", exact ? null : (d, w) -> openExactAlarmSettings(a))
                .show();
    }

    static void openOverlaySettings(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (ActivityNotFoundException ex) {
            Ui.dialog(a).setTitle("Not available on this TV")
                    .setMessage("This TV has no overlay settings screen. Use the ADB command shown in Setup check.")
                    .setPositiveButton("OK", null).show();
        }
    }

    static void openExactAlarmSettings(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (ActivityNotFoundException ex) {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.getPackageName())));
        }
    }

    static String compass(double deg) {
        String[] dirs = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        return dirs[(int) Math.round(deg / 22.5) % 16];
    }
}
