package com.alkhoudeir.athantv;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
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

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lets the user load the athan recording(s): from the TV or a USB drive, from an https link,
 * or by uploading from a phone on the same Wi-Fi. Fajr can have its own recording.
 */
public final class SoundActivity extends Activity {

    private static final int REQ_PICK = 10;
    private static final int REQ_PERM = 11;

    /**
     * Suggested free recording on Wikimedia Commons, where every file must carry a free license.
     * Check the file page for its license and credit line.
     */
    static final String SUGGESTED_URL = "https://commons.wikimedia.org/wiki/Special:FilePath/Beautiful_adhan.ogg";
    static final String SUGGESTED_PAGE = "commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg";

    private AppSettings s;
    private ArrayAdapter<String> adapter;
    private final List<Runnable> actions = new ArrayList<>();
    private UploadServer server;
    private MediaPlayer preview;
    private String pendingSlot = AudioStore.SLOT_REGULAR;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = new AppSettings(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(Ui.background());
        int pad = Ui.dp(this, 40);
        col.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 20));
        col.addView(Ui.text(this, "Athan sound  ·  صوت الأذان", 28, Ui.GOLD, true));
        col.addView(Ui.text(this, "The app plays the whole recording, start to finish. "
                + "Use any athan you own or have permission to use.", 15, Ui.MUTED, false));

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
        list.setOnItemClickListener((p, v, pos, id) -> actions.get(pos).run());
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

    @Override
    protected void onPause() {
        stopPreview();
        stopServer();
        super.onPause();
    }

    private void refresh() {
        adapter.clear();
        actions.clear();
        add("Regular athan: " + labelOf(AudioStore.SLOT_REGULAR, "Not set"), () -> slotMenu(AudioStore.SLOT_REGULAR));
        add("Fajr athan: " + labelOf(AudioStore.SLOT_FAJR, "Uses the regular athan"), () -> slotMenu(AudioStore.SLOT_FAJR));
        if (server != null && server.isRunning()) {
            add("Phone upload is ON: " + uploadAddress() + "   PIN " + server.pin(), this::stopServer);
        }
        add("Preview the Fajr / regular athan", this::previewMenu);
        adapter.notifyDataSetChanged();
    }

    private void add(String label, Runnable r) {
        adapter.add(label);
        actions.add(r);
    }

    private String labelOf(String slot, String empty) {
        if (!AudioStore.has(this, slot)) return empty;
        String l = s.audioLabel(slot);
        return l == null ? "Set" : l;
    }

    // ------------------------------------------------------------------ menus

    private void slotMenu(String slot) {
        String title = AudioStore.SLOT_FAJR.equals(slot) ? "Fajr athan" : "Regular athan";
        List<String> opts = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        opts.add("Upload from my phone (same Wi-Fi)");
        acts.add(() -> startServer(slot));
        opts.add("Choose a file on this TV or a USB drive");
        acts.add(() -> chooseLocal(slot));
        opts.add("Browse files (if your TV has a file picker)");
        acts.add(() -> openPicker(slot));
        opts.add("Download from an https link");
        acts.add(() -> askLink(slot, ""));
        opts.add("Download a suggested free recording (Wikimedia Commons)");
        acts.add(() -> confirmSuggested(slot));
        if (AudioStore.has(this, slot)) {
            opts.add("Remove");
            acts.add(() -> {
                AudioStore.remove(this, slot);
                refresh();
            });
        }
        Ui.dialog(this).setTitle(title)
                .setItems(opts.toArray(new String[0]), (d, i) -> acts.get(i).run())
                .show();
    }

    private void previewMenu() {
        Ui.dialog(this).setTitle("Preview")
                .setItems(new String[]{"Regular athan", "Fajr athan", "Stop preview"}, (d, i) -> {
                    if (i == 2) {
                        stopPreview();
                        return;
                    }
                    File f = AudioStore.forPrayer(this, i == 1);
                    if (f == null) {
                        toast("No recording set yet");
                        return;
                    }
                    playPreview(f);
                }).show();
    }

    // ------------------------------------------------------------------ sources

    private void startServer(String slot) {
        stopServer();
        server = new UploadServer(this, (savedSlot, label) -> runOnUiThread(() -> {
            toast("Received " + label);
            refresh();
        }));
        try {
            server.start();
        } catch (Exception e) {
            server = null;
            toast("Couldn't start the upload page: " + e.getMessage());
            return;
        }
        refresh();
        String fajrNote = AudioStore.SLOT_FAJR.equals(slot) ? "\n\nChoose \"Fajr only\" on the page." : "";
        Ui.dialog(this).setTitle("Upload from your phone")
                .setMessage("1. Connect your phone to the same Wi-Fi as the TV.\n"
                        + "2. Open this address in the phone's browser:\n\n     " + uploadAddress()
                        + "\n\n3. Pick the athan file and enter this PIN:\n\n     " + server.pin()
                        + fajrNote
                        + "\n\nThe page only works while this screen is open.")
                .setPositiveButton("OK", null).show();
    }

    private String uploadAddress() {
        String ip = NetUtil.localIpv4();
        return "http://" + (ip == null ? "<TV-IP>" : ip) + ":" + (server == null ? 8080 : server.port());
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
            if (adapter != null) refresh();
        }
    }

    private void chooseLocal(String slot) {
        pendingSlot = slot;
        String perm = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{perm}, REQ_PERM);
            return;
        }
        listDeviceAudio(slot);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_PERM) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                listDeviceAudio(pendingSlot);
            } else {
                toast("Permission denied. Use phone upload or a link instead.");
            }
        }
    }

    private void listDeviceAudio(String slot) {
        List<String> names = new ArrayList<>();
        List<Uri> uris = new ArrayList<>();
        ContentResolver cr = getContentResolver();
        Set<String> volumes = Build.VERSION.SDK_INT >= 29
                ? MediaStore.getExternalVolumeNames(this) // includes USB drives
                : java.util.Collections.singleton("external");
        String[] cols = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION};
        for (String vol : volumes) {
            Uri base = MediaStore.Audio.Media.getContentUri(vol);
            try (Cursor c = cr.query(base, cols, null, null, MediaStore.Audio.Media.DISPLAY_NAME)) {
                if (c == null) continue;
                while (c.moveToNext() && names.size() < 300) {
                    long id = c.getLong(0);
                    String name = c.getString(1);
                    long dur = c.getLong(2);
                    names.add(name + "  (" + AudioStore.formatDuration((int) dur) + ")");
                    uris.add(Uri.withAppendedPath(base, Long.toString(id)));
                }
            } catch (RuntimeException ignored) {
                // A volume may be unmounted.
            }
        }
        if (names.isEmpty()) {
            Ui.dialog(this).setTitle("No audio found")
                    .setMessage("No audio files were found on this TV or a USB drive. Copy an MP3 onto a USB "
                            + "drive and plug it in, or use \"Upload from my phone\".")
                    .setPositiveButton("OK", null).show();
            return;
        }
        Ui.dialog(this).setTitle("Pick the athan")
                .setItems(names.toArray(new String[0]), (d, i) -> importUri(slot, uris.get(i), names.get(i)))
                .show();
    }

    private void openPicker(String slot) {
        pendingSlot = slot;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*");
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (ActivityNotFoundException e) {
            toast("This TV has no file picker. Use phone upload or a USB drive instead.");
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_PICK && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            importUri(pendingSlot, uri, displayName(uri));
        }
    }

    private void askLink(String slot, String prefill) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_CLASS_TEXT);
        e.setHint("https://example.com/athan.mp3");
        e.setText(prefill);
        Ui.dialog(this).setTitle("Download athan from a link")
                .setView(e)
                .setPositiveButton("Download", (d, w) -> download(slot, e.getText().toString()))
                .setNegativeButton("Cancel", null).show();
    }

    private void confirmSuggested(String slot) {
        Ui.dialog(this).setTitle("Suggested free recording")
                .setMessage("Downloads \"Beautiful adhan\" from Wikimedia Commons. Commons only hosts freely "
                        + "licensed media; the license and credit line are on its page:\n\n" + SUGGESTED_PAGE
                        + "\n\nListen with Preview and replace it any time with your favorite athan.")
                .setPositiveButton("Download", (d, w) -> download(slot, SUGGESTED_URL))
                .setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ work

    private void download(String slot, String url) {
        toast("Downloading…");
        new Thread(() -> {
            try {
                int ms = AudioStore.download(this, slot, url);
                runOnUiThread(() -> {
                    toast("Saved (" + AudioStore.formatDuration(ms) + ")");
                    refresh();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Download failed: " + ex.getMessage()));
            }
        }, "athan-download").start();
    }

    private void importUri(String slot, Uri uri, String label) {
        toast("Importing…");
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("Couldn't open the file");
                int ms = AudioStore.save(this, slot, in, label == null ? "file" : label);
                runOnUiThread(() -> {
                    toast("Saved (" + AudioStore.formatDuration(ms) + ")");
                    refresh();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Import failed: " + ex.getMessage()));
            }
        }, "athan-import").start();
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignored) {
            // Fall through.
        }
        return "file";
    }

    private void playPreview(File f) {
        stopPreview();
        preview = new MediaPlayer();
        try {
            preview.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            preview.setDataSource(f.getAbsolutePath());
            preview.setOnCompletionListener(mp -> stopPreview());
            preview.prepare();
            preview.start();
            toast("Playing preview. Press Back on this screen to stop.");
        } catch (Exception e) {
            stopPreview();
            toast("Couldn't play: " + e.getMessage());
        }
    }

    private void stopPreview() {
        if (preview != null) {
            try {
                preview.stop();
            } catch (IllegalStateException ignored) {
                // Not started.
            }
            preview.release();
            preview = null;
        }
    }

    @Override
    @SuppressWarnings("deprecation") // still delivered on API 34 without predictive-back opt-in
    public void onBackPressed() {
        if (preview != null) {
            stopPreview();
            return;
        }
        super.onBackPressed();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
