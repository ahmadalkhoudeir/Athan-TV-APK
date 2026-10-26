package com.alkhoudeir.athantv;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tiny LAN-only web page for sending an athan MP3 from a phone to the TV.
 *
 * Safety measures: runs only while the Athan sound screen is open and stops after 10 idle
 * minutes; a fresh random 6-digit PIN is required for every upload (constant-time compare);
 * 10 wrong PINs shut the server down; uploads are capped at 30 MB, need a Content-Length, and
 * are only kept if Android can decode them as audio. Nothing is ever served back except the
 * static upload page.
 */
final class UploadServer {

    interface Listener {
        /** Called on a background thread after a successful upload. */
        void onUploaded(String slot, String label);
    }

    private static final String TAG = "AthanTV";
    private static final int MAX_BAD_PINS = 10;
    private static final long IDLE_LIMIT_MS = 10 * 60 * 1000L;

    private final Context ctx;
    private final Listener listener;
    private final String pin;
    private ServerSocket server;
    private Thread thread;
    private int badPins;
    private volatile long lastActivity;
    private int port;

    UploadServer(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        this.pin = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1_000_000));
    }

    String pin() {
        return pin;
    }

    int port() {
        return port;
    }

    boolean isRunning() {
        return server != null && !server.isClosed();
    }

    /** Binds the first free port in 8080..8089. */
    void start() throws IOException {
        IOException last = null;
        for (int p = 8080; p < 8090; p++) {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(p));
                server = ss;
                port = p;
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        if (server == null) throw last != null ? last : new IOException("No free port");
        lastActivity = System.currentTimeMillis();
        thread = new Thread(this::loop, "athan-upload");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
            // Closing anyway.
        }
    }

    private void loop() {
        try {
            server.setSoTimeout(30_000);
        } catch (SocketException ignored) {
            // Keep the default.
        }
        while (isRunning()) {
            if (System.currentTimeMillis() - lastActivity > IDLE_LIMIT_MS) {
                stop();
                break;
            }
            try (Socket s = server.accept()) {
                lastActivity = System.currentTimeMillis();
                s.setSoTimeout(60_000);
                handle(s);
            } catch (java.net.SocketTimeoutException timeout) {
                // Loop to check the idle limit.
            } catch (IOException | RuntimeException e) {
                // A bad request must never take the server thread down.
                if (isRunning()) Log.w(TAG, "Upload connection error", e);
            }
        }
    }

    private void handle(Socket s) throws IOException {
        InputStream in = s.getInputStream();
        OutputStream out = s.getOutputStream();
        String head = readHead(in);
        if (head == null) {
            respond(out, 400, "text/plain", "Bad request");
            return;
        }
        String[] lines = head.split("\r\n");
        String[] req = lines[0].split(" ");
        if (req.length < 2) {
            respond(out, 400, "text/plain", "Bad request");
            return;
        }
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.US), lines[i].substring(c + 1).trim());
        }
        String method = req[0];
        String path = req[1];

        if ("GET".equals(method) && ("/".equals(path) || path.startsWith("/?"))) {
            respond(out, 200, "text/html; charset=utf-8", PAGE);
            return;
        }
        if (!"POST".equals(method) || !path.startsWith("/upload")) {
            respond(out, 404, "text/plain", "Not found");
            return;
        }

        String given = headers.getOrDefault("x-pin", "");
        if (!MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), pin.getBytes(StandardCharsets.UTF_8))) {
            badPins++;
            respond(out, 403, "text/plain", "Wrong PIN. Check the PIN shown on the TV.");
            if (badPins >= MAX_BAD_PINS) stop();
            return;
        }
        String slot = "fajr".equals(headers.get("x-slot")) ? AudioStore.SLOT_FAJR : AudioStore.SLOT_REGULAR;
        long len;
        try {
            len = Long.parseLong(headers.getOrDefault("content-length", "-1"));
        } catch (NumberFormatException e) {
            len = -1;
        }
        if (len <= 0) {
            respond(out, 411, "text/plain", "Missing file size");
            return;
        }
        if (len > AudioStore.MAX_BYTES) {
            respond(out, 413, "text/plain", "File is larger than 30 MB");
            return;
        }
        String label = sanitize(headers.getOrDefault("x-name", "upload"));
        try {
            int ms = AudioStore.save(ctx, slot, new Limited(in, len), label);
            respond(out, 200, "text/plain", "Saved ✓ " + label + " (" + AudioStore.formatDuration(ms)
                    + "). You can close this page.");
            if (listener != null) listener.onUploaded(slot, label);
        } catch (IOException e) {
            respond(out, 422, "text/plain", "Not saved: " + e.getMessage());
        }
    }

    /** Reads request line + headers (max 16 KB) up to the blank line. */
    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int state = 0;
        while (buf.size() < 16 * 1024) {
            int b = in.read();
            if (b < 0) return null;
            buf.write(b);
            // Match \r\n\r\n
            if ((state == 0 || state == 2) && b == '\r') state++;
            else if ((state == 1 || state == 3) && b == '\n') state++;
            else state = b == '\r' ? 1 : 0;
            if (state == 4) return buf.toString("ISO-8859-1");
        }
        return null;
    }

    private static void respond(OutputStream out, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "Error") + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + b.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(b);
        out.flush();
    }

    private static String sanitize(String raw) {
        String s;
        try {
            s = URLDecoder.decode(raw, "UTF-8");
        } catch (Exception e) {
            s = raw;
        }
        s = s.replaceAll("[^\\p{L}\\p{N} ._()\\-]", "").trim();
        if (s.isEmpty()) s = "upload";
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    /** Stops reading after exactly {@code remaining} bytes. */
    private static final class Limited extends FilterInputStream {
        private long remaining;

        Limited(InputStream in, long n) {
            super(in);
            remaining = n;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = super.read();
            if (b >= 0) remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int n = super.read(b, off, (int) Math.min(len, remaining));
            if (n > 0) remaining -= n;
            return n;
        }
    }

    private static final String PAGE = "<!doctype html><html lang=en><head><meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1'><title>Athan TV upload</title>"
            + "<style>body{font-family:system-ui,sans-serif;background:#062a22;color:#fff;margin:0;padding:24px;max-width:520px}"
            + "h1{color:#e2c275;font-size:22px}label{display:block;margin:16px 0 6px;color:#b7c9c2}"
            + "input,select,button{font-size:18px;width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:1px solid #e2c275;background:#0b3a30;color:#fff}"
            + "button{background:#e2c275;color:#062a22;font-weight:700;margin-top:20px}#msg{margin-top:18px;font-size:17px}</style></head>"
            + "<body><h1>Send an athan to your TV</h1>"
            + "<label>Athan file (MP3, M4A or OGG, up to 30 MB)</label><input id=f type=file accept='audio/*'>"
            + "<label>Use it for</label><select id=s><option value=regular>All prayers</option><option value=fajr>Fajr only</option></select>"
            + "<label>PIN shown on the TV</label><input id=p inputmode=numeric maxlength=6 autocomplete=off>"
            + "<button id=b>Send to TV</button><div id=msg></div>"
            + "<script>document.getElementById('b').onclick=async function(){var f=document.getElementById('f').files[0];"
            + "var m=document.getElementById('msg');if(!f){m.textContent='Pick a file first.';return;}"
            + "m.textContent='Sending\\u2026';try{var r=await fetch('/upload',{method:'POST',body:f,headers:{"
            + "'X-Pin':document.getElementById('p').value.trim(),'X-Slot':document.getElementById('s').value,"
            + "'X-Name':encodeURIComponent(f.name),'Content-Type':'application/octet-stream'}});"
            + "m.textContent=await r.text();}catch(e){m.textContent='Could not reach the TV: '+e;}};</script></body></html>";
}
