package com.alkhoudeir.athantv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.alkhoudeir.athantv.core.PrayerCalc.Event;

import java.time.ZonedDateTime;

/**
 * Keeps exactly two alarms armed: the next enabled prayer, and (optionally) the reminder
 * before it. Called on app start, after every alarm, after settings change, on boot and
 * when the clock or time zone changes, so the schedule is always rebuilt from scratch.
 */
public final class AlarmScheduler {

    private static final String TAG = "AthanTV";
    static final String ACTION_ATHAN = "com.alkhoudeir.athantv.ATHAN";
    static final String ACTION_REMINDER = "com.alkhoudeir.athantv.REMINDER";
    static final String ACTION_TEST = "com.alkhoudeir.athantv.TEST";
    static final String EXTRA_EVENT = "event";
    static final String EXTRA_AT = "at";
    static final String EXTRA_MINUTES = "minutes";

    private static final int RC_ATHAN = 1;
    private static final int RC_REMINDER = 2;
    private static final int RC_SHOW = 3;
    private static final int RC_TEST = 4;

    private AlarmScheduler() {}

    public static void scheduleAll(Context ctx) {
        scheduleAll(ctx, 0L);
    }

    /**
     * @param notBeforeMillis treat "now" as at least one second after this instant. Used when
     *                        re-arming from inside an alarm so the alarm that just fired can never
     *                        be scheduled again (guards against a callback that runs a few ms early).
     */
    public static void scheduleAll(Context ctx, long notBeforeMillis) {
        Context app = ctx.getApplicationContext();
        AppSettings s = new AppSettings(app);
        Schedule sched = new Schedule(s);
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        cancel(app, am, ACTION_ATHAN, RC_ATHAN);
        cancel(app, am, ACTION_REMINDER, RC_REMINDER);

        ZonedDateTime now = ZonedDateTime.now(sched.zone());
        if (notBeforeMillis > 0) {
            ZonedDateTime floor = java.time.Instant.ofEpochMilli(notBeforeMillis + 1000)
                    .atZone(sched.zone());
            if (floor.isAfter(now)) now = floor;
        }
        Schedule.Upcoming next = sched.next(now, true);
        if (next == null) {
            Log.i(TAG, "No enabled prayers; nothing scheduled");
            return;
        }

        long at = next.time.toInstant().toEpochMilli();
        Intent athan = new Intent(app, AlarmReceiver.class)
                .setAction(ACTION_ATHAN)
                .putExtra(EXTRA_EVENT, next.event.ordinal())
                .putExtra(EXTRA_AT, at);
        set(app, am, at, pending(app, athan, RC_ATHAN));
        Log.i(TAG, "Scheduled " + next.event + " at " + next.time);

        int remind = s.reminderMinutes();
        if (remind > 0) {
            // The reminder belongs to the first prayer whose reminder time is still ahead.
            Schedule.Upcoming target = next;
            ZonedDateTime remindAt = target.time.minusMinutes(remind);
            if (!remindAt.isAfter(now)) {
                target = sched.next(target.time, true);
                remindAt = target == null ? null : target.time.minusMinutes(remind);
            }
            if (target != null && remindAt != null) {
                long rAt = remindAt.toInstant().toEpochMilli();
                Intent r = new Intent(app, AlarmReceiver.class)
                        .setAction(ACTION_REMINDER)
                        .putExtra(EXTRA_EVENT, target.event.ordinal())
                        .putExtra(EXTRA_AT, rAt)
                        .putExtra(EXTRA_MINUTES, remind);
                set(app, am, rAt, pending(app, r, RC_REMINDER));
                Log.i(TAG, "Scheduled reminder for " + target.event + " at " + remindAt);
            }
        }
    }

    /**
     * Arms a one-off test athan {@code seconds} from now, through the same alarm path as a real
     * prayer, so it can be watched on top of another app. Uses the next prayer's name.
     */
    public static void scheduleTest(Context ctx, int seconds) {
        Context app = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Schedule sched = new Schedule(new AppSettings(app));
        Schedule.Upcoming n = sched.next(ZonedDateTime.now(sched.zone()), false);
        Event e = n == null ? Event.DHUHR : n.event;
        long at = System.currentTimeMillis() + seconds * 1000L;
        Intent test = new Intent(app, AlarmReceiver.class)
                .setAction(ACTION_TEST)
                .putExtra(EXTRA_EVENT, e.ordinal())
                .putExtra(EXTRA_AT, at);
        set(app, am, at, pending(app, test, RC_TEST));
        Log.i(TAG, "Test athan armed for " + seconds + " s from now");
    }

    /** True when the OS lets this app set exact alarms (always true below Android 12). */
    public static boolean canExact(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    private static void set(Context ctx, AlarmManager am, long at, PendingIntent op) {
        try {
            if (canExact(ctx)) {
                // setAlarmClock is never deferred and leaves low-power modes to fire on time.
                PendingIntent show = PendingIntent.getActivity(ctx, RC_SHOW,
                        new Intent(ctx, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), op);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op);
                Log.w(TAG, "Exact alarms not permitted; using inexact alarm");
            }
        } catch (SecurityException se) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op);
            Log.w(TAG, "Exact alarm refused; using inexact alarm", se);
        }
    }

    private static PendingIntent pending(Context ctx, Intent i, int rc) {
        return PendingIntent.getBroadcast(ctx, rc, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancel(Context ctx, AlarmManager am, String action, int rc) {
        Intent i = new Intent(ctx, AlarmReceiver.class).setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, rc, i,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    /** Human-readable summary for the diagnostics screen. */
    public static String describeNext(Context ctx) {
        AppSettings s = new AppSettings(ctx);
        Schedule sched = new Schedule(s);
        Schedule.Upcoming n = sched.next(ZonedDateTime.now(sched.zone()), true);
        if (n == null) return "No prayers enabled";
        Event e = n.event;
        return Schedule.englishName(e, n.time) + " at " + Schedule.formatTime(n.time, s.use24h())
                + " (" + s.mode(e).label + ")";
    }
}
