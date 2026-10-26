package com.alkhoudeir.athantv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Alarms are cleared on reboot and become stale when the clock, time zone or exact-alarm
 * permission changes. Every one of those events simply rebuilds the schedule.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Log.i("AthanTV", "Rescheduling after " + intent.getAction());
        AlarmScheduler.scheduleAll(ctx);
    }
}
