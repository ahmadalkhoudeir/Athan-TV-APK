package com.alkhoudeir.athantv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.alkhoudeir.athantv.core.PrayerCalc.Event;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Foreground service that runs one alert at a time: shows the overlay, takes audio focus so
 * the show you were watching pauses, plays the full athan, shows the after-athan supplication,
 * then cleans up and gives audio focus back.
 */
public final class AthanService extends Service
        implements OverlayController.Listener, AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "AthanTV";
    private static final String CHANNEL = "athan";
    private static final int NOTIF_ID = 42;

    static final String ACTION_ALERT = "alert";
    static final String ACTION_REMINDER = "reminder";
    static final String ACTION_STOP = "stop";
    private static final String X_EVENT = "event";
    private static final String X_AT = "at";
    private static final String X_AUDIO = "audio";
    private static final String X_MINUTES = "minutes";

    private final Handler main = new Handler(Looper.getMainLooper());
    private OverlayController overlay;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focusRequest; // Android 8.0+
    private boolean legacyFocus;            // Android 7.1 (Fire OS 6)
    private PowerManager.WakeLock cpuLock;
    private PowerManager.WakeLock screenLock;
    private boolean alertActive;

    // ------------------------------------------------------------------ entry points

    public static void startAlert(Context ctx, Event e, long atMillis, boolean withAudio) {
        Intent i = new Intent(ctx, AthanService.class).setAction(ACTION_ALERT)
                .putExtra(X_EVENT, e.ordinal()).putExtra(X_AT, atMillis).putExtra(X_AUDIO, withAudio);
        start(ctx, i);
    }

    public static void startReminder(Context ctx, Event e, int minutes) {
        Intent i = new Intent(ctx, AthanService.class).setAction(ACTION_REMINDER)
                .putExtra(X_EVENT, e.ordinal()).putExtra(X_MINUTES, minutes);
        start(ctx, i);
    }

    public static void stop(Context ctx) {
        ctx.startService(new Intent(ctx, AthanService.class).setAction(ACTION_STOP));
    }

    private static void start(Context ctx, Intent i) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i); // Fire OS 6 / Android 7.1 has no background-start limits
            }
        } catch (RuntimeException ex) {
            // e.g. ForegroundServiceStartNotAllowedException if the alarm was not exact.
            Log.e(TAG, "Could not start alert service", ex);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        overlay = new OverlayController(this);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground("Prayer alert");
        String action = intent == null ? ACTION_STOP : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            finish();
            return START_NOT_STICKY;
        }
        int idx = intent.getIntExtra(X_EVENT, Event.DHUHR.ordinal());
        Event e = Event.values()[Math.max(0, Math.min(Event.values().length - 1, idx))];

        if (ACTION_REMINDER.equals(action)) {
            if (!alertActive) beginReminder(e, intent.getIntExtra(X_MINUTES, 0));
        } else {
            long at = intent.getLongExtra(X_AT, System.currentTimeMillis());
            beginAlert(e, at, intent.getBooleanExtra(X_AUDIO, true));
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        releaseAll();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ athan

    private void beginAlert(Event e, long atMillis, boolean withAudio) {
        releaseAll();
        alertActive = true;
        AppSettings s = new AppSettings(this);
        ZonedDateTime when = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault());
        String en = Schedule.englishName(e, when);
        String ar = Schedule.arabicName(e, when);
        goForeground("Athan: " + en + " " + Schedule.formatTime(when, s.use24h()));

        acquireLocks(s.wakeScreen());

        boolean isFajr = e == Event.FAJR;
        File file = withAudio ? AudioStore.forPrayer(this, isFajr) : null;
        String status = withAudio
                ? (file != null ? "Press BACK or OK on your remote to stop" : "No athan recording yet. Add one in Athan TV > Athan sound")
                : "Silent alert. Press BACK to close";

        if (OverlayController.canShow(this)) {
            overlay.showAlert(ar, en, Schedule.formatTime(when, s.use24h()), s.cityName(), isFajr,
                    status, s.overlayStyle() == AppSettings.OverlayStyle.BANNER, this);
        } else {
            Log.w(TAG, "Overlay permission missing; playing audio only");
        }

        if (!withAudio) {
            overlay.setProgress(-1);
            main.postDelayed(this::finish, 30_000);
            return;
        }
        if (file == null) {
            beep(3);
            main.postDelayed(this::afterAthan, 4_000);
            return;
        }
        playFile(file, s);
    }

    private void playFile(File file, AppSettings s) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        int gain = s.pauseOthers() ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT : AudioManager.AUDIOFOCUS_GAIN;
        int focus = requestFocus(attrs, gain);
        if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Audio focus not granted (" + focus + "); playing anyway");
        }

        player = new MediaPlayer();
        try {
            player.setAudioAttributes(attrs);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(file.getAbsolutePath());
            float vol = s.volumePercent() / 100f;
            player.setVolume(vol, vol);
            player.setOnCompletionListener(mp -> afterAthan());
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Playback error " + what + "/" + extra);
                afterAthan();
                return true;
            });
            player.prepare();
            player.start();
            tickProgress();
        } catch (Exception ex) {
            Log.e(TAG, "Could not play athan", ex);
            overlay.setStatus("Couldn't play the athan file. Re-add it in Athan TV > Athan sound");
            beep(3);
            main.postDelayed(this::afterAthan, 4_000);
        }
    }

    private void tickProgress() {
        if (player == null) return;
        try {
            int d = player.getDuration();
            if (d > 0) overlay.setProgress(player.getCurrentPosition() / (float) d);
        } catch (IllegalStateException ignored) {
            return;
        }
        main.postDelayed(this::tickProgress, 500);
    }

    private void afterAthan() {
        stopPlayer();
        abandonFocus();
        int dua = new AppSettings(this).duaSeconds();
        if (dua > 0 && overlay.isShowing()) {
            overlay.showDua();
            overlay.setStatus("Press BACK to close");
            main.postDelayed(this::finish, dua * 1000L);
        } else {
            finish();
        }
    }

    // ------------------------------------------------------------------ reminder

    private void beginReminder(Event e, int minutes) {
        main.removeCallbacksAndMessages(null); // a newer reminder replaces an older one
        AppSettings s = new AppSettings(this);
        Schedule sched = new Schedule(s);
        ZonedDateTime when = ZonedDateTime.now(sched.zone()).plusMinutes(minutes);
        String line1 = Schedule.englishName(e, when) + " in " + minutes + " min  ·  "
                + Schedule.arabicName(e, when);
        String line2 = "Athan at about " + Schedule.formatTime(when, s.use24h()) + " · " + s.cityName();
        goForeground("Reminder: " + line1);
        if (OverlayController.canShow(this)) overlay.showReminder(line1, line2);
        if (s.reminderChime()) beep(1);
        main.postDelayed(this::finish, 15_000);
    }

    // ------------------------------------------------------------------ callbacks

    @Override
    public void onDismissRequested() {
        finish();
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            // Another app took over for good (the user started something new): end the alert.
            finish();
        }
    }

    // ------------------------------------------------------------------ helpers

    private void finish() {
        releaseAll();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseAll() {
        main.removeCallbacksAndMessages(null);
        stopPlayer();
        abandonFocus();
        if (overlay != null) overlay.hide();
        if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
        if (screenLock != null && screenLock.isHeld()) screenLock.release();
        cpuLock = null;
        screenLock = null;
        alertActive = false;
    }

    private void stopPlayer() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // Not started.
            }
            player.release();
            player = null;
        }
    }

    /** Asks other apps to pause (transient) or stop, using the API the device supports. */
    @SuppressWarnings("deprecation")
    private int requestFocus(AudioAttributes attrs, int gain) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = new AudioFocusRequest.Builder(gain)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(this, main)
                    .build();
            return audio.requestAudioFocus(focusRequest);
        }
        legacyFocus = true;
        return audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, gain);
    }

    @SuppressWarnings("deprecation")
    private void abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest != null) {
                audio.abandonAudioFocusRequest(focusRequest);
                focusRequest = null;
            }
        } else if (legacyFocus) {
            audio.abandonAudioFocus(this);
            legacyFocus = false;
        }
    }

    @SuppressWarnings("deprecation")
    private void acquireLocks(boolean wakeScreen) {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) return;
        cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AthanTV:alert");
        cpuLock.acquire(15 * 60 * 1000L);
        // Screen already on (you're watching something, or the screensaver is up): waking it fully
        // closes the screensaver so the alert is visible. A device that is asleep is only woken
        // when "Wake the TV for athan" is on; with HDMI-CEC that usually turns the TV on too.
        // SCREEN_BRIGHT_WAKE_LOCK is deprecated but is still the only app-level way to do this.
        if (wakeScreen || pm.isInteractive()) {
            screenLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "AthanTV:wake");
            screenLock.acquire(10 * 60 * 1000L);
        }
    }

    private void beep(int times) {
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            for (int i = 0; i < times; i++) {
                main.postDelayed(() -> tg.startTone(ToneGenerator.TONE_PROP_ACK, 400), i * 900L);
            }
            main.postDelayed(tg::release, times * 900L + 600);
        } catch (RuntimeException ex) {
            Log.w(TAG, "Tone unavailable", ex);
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return; // channels start at Android 8.0
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Athan alerts",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shown while the athan or a reminder is playing");
            nm.createNotificationChannel(ch);
        }
    }

    @SuppressWarnings("deprecation")
    private Notification.Builder legacyBuilder() {
        return new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);
    }

    private void goForeground(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, AthanService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : legacyBuilder();
        Notification n = builder
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Athan TV")
                .setContentText(text)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }
}
