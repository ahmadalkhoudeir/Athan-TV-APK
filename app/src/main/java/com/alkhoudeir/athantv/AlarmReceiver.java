package com.alkhoudeir.athantv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.alkhoudeir.athantv.core.PrayerCalc.Event;

/** Fires at prayer and reminder times, starts the alert service, then re-arms the next alarm. */
public final class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        int idx = intent.getIntExtra(AlarmScheduler.EXTRA_EVENT, -1);
        long at = intent.getLongExtra(AlarmScheduler.EXTRA_AT, 0);
        AppSettings s = new AppSettings(ctx);

        try {
            if (idx < 0 || idx >= Event.values().length) return;
            Event e = Event.values()[idx];
            long lateMs = System.currentTimeMillis() - at;

            if (AlarmScheduler.ACTION_ATHAN.equals(action)) {
                if (lateMs > s.lateLimitMinutes() * 60_000L) {
                    // The device was asleep or off; an athan long after its time would be wrong.
                    Log.i("AthanTV", "Skipping " + e + ": fired " + (lateMs / 60000) + " min late");
                    return;
                }
                AppSettings.Mode m = s.mode(e);
                if (m == AppSettings.Mode.OFF) return;
                AthanService.startAlert(ctx, e, at, m == AppSettings.Mode.ATHAN);
            } else if (AlarmScheduler.ACTION_TEST.equals(action)) {
                AthanService.startAlert(ctx, e, at, true);
            } else if (AlarmScheduler.ACTION_REMINDER.equals(action)) {
                if (lateMs > 2 * 60_000L) return;
                int minutes = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTES, 0);
                AthanService.startReminder(ctx, e, minutes);
            }
        } finally {
            AlarmScheduler.scheduleAll(ctx, at);
        }
    }
}
