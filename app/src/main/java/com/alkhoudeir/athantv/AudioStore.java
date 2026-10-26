package com.alkhoudeir.athantv;

import android.content.Context;
import android.media.MediaPlayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Stores the athan recordings inside the app's private storage. Every import is size-limited,
 * written to a temp file first, and only kept if Android can actually decode it as audio.
 */
public final class AudioStore {

    public static final String SLOT_REGULAR = "regular";
    public static final String SLOT_FAJR = "fajr";

    /** 30 MB is far more than any athan needs (a 5-minute 320 kbps MP3 is about 12 MB). */
    public static final long MAX_BYTES = 30L * 1024 * 1024;

    private AudioStore() {}

    public static File file(Context ctx, String slot) {
        File dir = new File(ctx.getFilesDir(), "athan");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, slot + ".audio");
    }

    public static boolean has(Context ctx, String slot) {
        File f = file(ctx, slot);
        return f.isFile() && f.length() > 0;
    }

    /** The file to play for a prayer: Fajr uses its own slot when set, else the regular one. */
    public static File forPrayer(Context ctx, boolean isFajr) {
        if (isFajr && has(ctx, SLOT_FAJR)) return file(ctx, SLOT_FAJR);
        if (has(ctx, SLOT_REGULAR)) return file(ctx, SLOT_REGULAR);
        return null;
    }

    public static void remove(Context ctx, String slot) {
        //noinspection ResultOfMethodCallIgnored
        file(ctx, slot).delete();
        new AppSettings(ctx).setAudioLabel(slot, null);
    }

    /**
     * Copies a stream into the slot.
     *
     * @return duration in milliseconds of the accepted audio
     * @throws IOException with a user-readable message if the data is too big or not audio
     */
    public static int save(Context ctx, String slot, InputStream in, String label) throws IOException {
        File tmp = new File(ctx.getCacheDir(), "incoming_" + slot + ".tmp");
        long total = 0;
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES) throw new IOException("File is larger than 30 MB");
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        if (total == 0) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("The file was empty");
        }
        int duration = probeDurationMs(tmp);
        if (duration <= 0) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("That file isn't playable audio (try an MP3, M4A or OGG)");
        }
        File dest = file(ctx, slot);
        //noinspection ResultOfMethodCallIgnored
        dest.delete();
        if (!tmp.renameTo(dest)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Couldn't save the file");
        }
        new AppSettings(ctx).setAudioLabel(slot, label + " (" + formatDuration(duration) + ")");
        return duration;
    }

    /** Downloads over HTTPS (redirects followed) and saves into the slot. Run off the UI thread. */
    public static int download(Context ctx, String slot, String url) throws IOException {
        URL u = new URL(url.trim());
        if (!"https".equalsIgnoreCase(u.getProtocol())) {
            throw new IOException("Only https:// links are allowed");
        }
        HttpURLConnection c = null;
        for (int hop = 0; hop < 5; hop++) {
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(15_000);
            c.setReadTimeout(30_000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "AthanTV/1.0 (Android TV prayer-times app)");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirect without a location");
                u = new URL(u, loc);
                if (!"https".equalsIgnoreCase(u.getProtocol())) {
                    throw new IOException("Link redirected to a non-https address");
                }
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("Server answered HTTP " + code);
            }
            break;
        }
        if (c == null) throw new IOException("Too many redirects");
        long len = c.getContentLengthLong();
        if (len > MAX_BYTES) {
            c.disconnect();
            throw new IOException("File is larger than 30 MB");
        }
        String name = u.getPath();
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.isEmpty()) name = u.getHost();
        try (InputStream in = c.getInputStream()) {
            return save(ctx, slot, in, java.net.URLDecoder.decode(name, "UTF-8"));
        } finally {
            c.disconnect();
        }
    }

    public static int probeDurationMs(File f) {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(f.getAbsolutePath());
            mp.prepare();
            return mp.getDuration();
        } catch (Exception e) {
            return -1;
        } finally {
            mp.release();
        }
    }

    public static String formatDuration(int ms) {
        int sec = Math.max(0, ms / 1000);
        return (sec / 60) + ":" + String.format(java.util.Locale.US, "%02d", sec % 60);
    }
}
