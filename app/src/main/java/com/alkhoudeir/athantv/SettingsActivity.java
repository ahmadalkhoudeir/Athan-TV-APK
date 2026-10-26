package com.alkhoudeir.athantv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.alkhoudeir.athantv.core.PrayerCalc.Event;
import com.alkhoudeir.athantv.core.PrayerCalc.HighLatRule;
import com.alkhoudeir.athantv.core.PrayerCalc.Madhab;
import com.alkhoudeir.athantv.core.PrayerCalc.Method;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Remote-friendly settings: one list, every row opens a simple choice dialog. */
public final class SettingsActivity extends Activity {

    private interface Action {
        void run();
    }

    private static final class Row {
        final Supplier<String> label;
        final Action action;

        Row(Supplier<String> label, Action action) {
            this.label = label;
            this.action = action;
        }
    }

    private AppSettings s;
    private final List<Row> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = new AppSettings(this);
        buildRows();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(Ui.background());
        int pad = Ui.dp(this, 40);
        col.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 20));
        col.addView(Ui.text(this, "Settings  ·  الإعدادات", 28, Ui.GOLD, true));

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<>()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
                t.setTextColor(0xFFFFFFFF);
                return t;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> rows.get(pos).action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = Ui.dp(this, 12);
        col.addView(list, lp);
        setContentView(col);
        list.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        adapter.clear();
        for (Row r : rows) adapter.add(r.label.get());
        adapter.notifyDataSetChanged();
    }

    /** Called after every change: redraw the list and rebuild the alarm schedule. */
    private void changed() {
        AlarmScheduler.scheduleAll(this);
        refresh();
    }

    // ------------------------------------------------------------------ rows

    private void buildRows() {
        rows.add(new Row(() -> "Location: " + s.cityName()
                + String.format(Locale.US, "  (%.4f, %.4f)", s.lat(), s.lng()), this::pickLocation));

        rows.add(new Row(() -> "Calculation method: " + s.method().label, () -> {
            Method[] v = Method.values();
            choose("Calculation method", labels(v), s.method().ordinal(), i -> s.setMethod(v[i]));
        }));
        rows.add(new Row(() -> "Asr time: " + s.madhab().label, () -> {
            Madhab[] v = Madhab.values();
            choose("Asr time", labels(v), s.madhab().ordinal(), i -> s.setMadhab(v[i]));
        }));
        rows.add(new Row(() -> "High-latitude rule: " + s.highLat().label, () -> {
            HighLatRule[] v = HighLatRule.values();
            choose("High-latitude rule", labels(v), s.highLat().ordinal(), i -> s.setHighLat(v[i]));
        }));

        for (Event e : Event.values()) {
            if (!e.isPrayer()) continue;
            rows.add(new Row(() -> e.english + ": " + s.mode(e).label, () -> {
                AppSettings.Mode[] v = AppSettings.Mode.values();
                choose(e.english + " alert", labels(v), s.mode(e).ordinal(), i -> s.setMode(e, v[i]));
            }));
        }
        for (Event e : Event.values()) {
            rows.add(new Row(() -> e.english + " time adjustment: " + signed(s.offset(e)) + " min", () -> {
                String[] opts = new String[61];
                for (int i = 0; i < 61; i++) opts[i] = signed(i - 30) + " min";
                choose(e.english + " adjustment", opts, s.offset(e) + 30, i -> s.setOffset(e, i - 30));
            }));
        }

        int[] reminders = {0, 5, 10, 15, 20, 30};
        rows.add(new Row(() -> "Reminder before each prayer: "
                + (s.reminderMinutes() == 0 ? "Off" : s.reminderMinutes() + " min"), () -> {
            String[] opts = new String[reminders.length];
            int sel = 0;
            for (int i = 0; i < reminders.length; i++) {
                opts[i] = reminders[i] == 0 ? "Off" : reminders[i] + " minutes before";
                if (reminders[i] == s.reminderMinutes()) sel = i;
            }
            choose("Reminder", opts, sel, i -> s.setReminderMinutes(reminders[i]));
        }));
        rows.add(new Row(() -> "Reminder chime: " + onOff(s.reminderChime()),
                () -> chooseBool("Reminder chime", s.reminderChime(), s::setReminderChime)));

        rows.add(new Row(() -> "Alert style: " + s.overlayStyle().label, () -> {
            AppSettings.OverlayStyle[] v = AppSettings.OverlayStyle.values();
            choose("Alert style", labels(v), s.overlayStyle().ordinal(), i -> s.setOverlayStyle(v[i]));
        }));
        int[] duas = {0, 30, 45, 60, 120};
        rows.add(new Row(() -> "Supplication after athan: "
                + (s.duaSeconds() == 0 ? "Off" : s.duaSeconds() + " s on screen"), () -> {
            String[] opts = new String[duas.length];
            int sel = 0;
            for (int i = 0; i < duas.length; i++) {
                opts[i] = duas[i] == 0 ? "Off" : duas[i] + " seconds";
                if (duas[i] == s.duaSeconds()) sel = i;
            }
            choose("Supplication after athan", opts, sel, i -> s.setDuaSeconds(duas[i]));
        }));
        rows.add(new Row(() -> "Athan volume: " + s.volumePercent() + "% of TV volume", () -> {
            String[] opts = new String[10];
            for (int i = 0; i < 10; i++) opts[i] = ((i + 1) * 10) + "%";
            choose("Athan volume", opts, s.volumePercent() / 10 - 1, i -> s.setVolumePercent((i + 1) * 10));
        }));
        rows.add(new Row(() -> "Other apps during athan: "
                + (s.pauseOthers() ? "Pause, then resume" : "Stop"), () ->
                choose("Other apps during athan",
                        new String[]{"Pause, then resume after the athan", "Stop them"},
                        s.pauseOthers() ? 0 : 1, i -> s.setPauseOthers(i == 0))));
        rows.add(new Row(() -> "Wake the TV for athan (best effort): " + onOff(s.wakeScreen()),
                () -> chooseBool("Wake the TV for athan", s.wakeScreen(), s::setWakeScreen)));

        rows.add(new Row(() -> "Hijri date adjustment: " + signed(s.hijriAdjust()) + " day(s)", () -> {
            String[] opts = {"-2", "-1", "0", "+1", "+2"};
            choose("Hijri date adjustment", opts, s.hijriAdjust() + 2, i -> s.setHijriAdjust(i - 2));
        }));
        rows.add(new Row(() -> "Clock: " + (s.use24h() ? "24-hour" : "12-hour"), () ->
                choose("Clock", new String[]{"12-hour", "24-hour"}, s.use24h() ? 1 : 0,
                        i -> s.setUse24h(i == 1))));

        rows.add(new Row(() -> "Athan sound…", () -> startActivity(new Intent(this, SoundActivity.class))));
        rows.add(new Row(() -> "Setup check and permissions…", () -> MainActivity.showSetupCheck(this)));
        rows.add(new Row(() -> "Test the reminder banner", () -> {
            AthanService.startReminder(this, Event.ASR, 10);
            Toast.makeText(this, "Showing a test reminder", Toast.LENGTH_SHORT).show();
        }));
        rows.add(new Row(() -> "Test the athan now", () ->
                AthanService.startAlert(this, Event.DHUHR, System.currentTimeMillis(), true)));
        rows.add(new Row(() -> "Test on top of another app (starts in 30 seconds)", () ->
                MainActivity.startDelayedTest(this)));
        rows.add(new Row(() -> "About: Athan TV " + versionName(), () -> Ui.dialog(this)
                .setTitle("About Athan TV")
                .setMessage("Prayer times are calculated on the TV itself (no internet needed) using standard "
                        + "solar-position formulas, and were checked against the Aladhan API.\n\n"
                        + "Supplication text: Sahih al-Bukhari 614.\n\nYour settings never leave this device.")
                .setPositiveButton("OK", null).show()));
    }

    // ------------------------------------------------------------------ dialogs

    private void pickLocation() {
        String[] opts = new String[AppSettings.CITIES.length + 1];
        for (int i = 0; i < AppSettings.CITIES.length; i++) opts[i] = AppSettings.CITIES[i].name;
        opts[opts.length - 1] = "Enter coordinates manually…";
        Ui.dialog(this).setTitle("Location").setItems(opts, (d, i) -> {
            if (i < AppSettings.CITIES.length) {
                AppSettings.City c = AppSettings.CITIES[i];
                s.setLocation(c.name, c.lat, c.lng);
                changed();
            } else {
                manualLocation();
            }
        }).show();
    }

    private void manualLocation() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        box.setPadding(p, p / 2, p, 0);
        EditText name = new EditText(this);
        name.setHint("Place name (e.g. My home)");
        name.setText(s.cityName());
        EditText coords = new EditText(this);
        coords.setHint("Latitude, longitude  e.g. 41.6612, -72.7795");
        coords.setText(String.format(Locale.US, "%.4f, %.4f", s.lat(), s.lng()));
        coords.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(name);
        box.addView(coords);
        Ui.dialog(this).setTitle("Coordinates (decimal degrees; west and south are negative)")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        String[] parts = coords.getText().toString().split(",");
                        double lat = Double.parseDouble(parts[0].trim());
                        double lng = Double.parseDouble(parts[1].trim());
                        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) throw new IllegalArgumentException();
                        String n = name.getText().toString().trim();
                        s.setLocation(n.isEmpty() ? "Custom location" : n, lat, lng);
                        changed();
                    } catch (RuntimeException ex) {
                        Toast.makeText(this, "Please enter two numbers like 41.6612, -72.7795",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void choose(String title, String[] options, int selected, IntConsumer onPick) {
        Ui.dialog(this).setTitle(title)
                .setSingleChoiceItems(options, selected, (d, i) -> {
                    onPick.accept(i);
                    d.dismiss();
                    changed();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void chooseBool(String title, boolean current, java.util.function.Consumer<Boolean> set) {
        choose(title, new String[]{"On", "Off"}, current ? 0 : 1, i -> set.accept(i == 0));
    }

    // ------------------------------------------------------------------ helpers

    private static String[] labels(Enum<?>[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            if (v instanceof Method) out[i] = ((Method) v).label;
            else if (v instanceof Madhab) out[i] = ((Madhab) v).label;
            else if (v instanceof HighLatRule) out[i] = ((HighLatRule) v).label;
            else if (v instanceof AppSettings.Mode) out[i] = ((AppSettings.Mode) v).label;
            else if (v instanceof AppSettings.OverlayStyle) out[i] = ((AppSettings.OverlayStyle) v).label;
            else out[i] = values[i].name();
        }
        return out;
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }

    private static String onOff(boolean b) {
        return b ? "On" : "Off";
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
