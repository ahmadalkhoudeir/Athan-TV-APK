package com.alkhoudeir.athantv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Draws the athan alert on top of whatever app is on the TV, using a system overlay window
 * (TYPE_APPLICATION_OVERLAY). Requires the "display over other apps" permission.
 * All methods must be called on the main thread.
 */
public final class OverlayController {

    public interface Listener {
        void onDismissRequested();
    }

    static final int GOLD = Color.parseColor("#E2C275");
    static final int WHITE = Color.WHITE;
    static final int MUTED = Color.parseColor("#B7C9C2");
    static final int BG_TOP = Color.parseColor("#F2062A22");
    static final int BG_BOTTOM = Color.parseColor("#F2021612");

    private final Context ctx;
    private final WindowManager wm;
    private View root;
    private ProgressBar progress;
    private LinearLayout textColumn;
    private TextView hint;
    private boolean bannerStyle;

    public OverlayController(Context ctx) {
        this.ctx = ctx;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public static boolean canShow(Context ctx) {
        return Settings.canDrawOverlays(ctx);
    }

    public boolean isShowing() {
        return root != null;
    }

    // ------------------------------------------------------------------ athan alert

    public void showAlert(String arabicName, String englishName, String time, String place,
                          boolean isFajr, String status, boolean banner, Listener listener) {
        hide();
        bannerStyle = banner;
        KeyCatcher frame = new KeyCatcher(ctx, listener);
        frame.setFocusable(true);
        frame.setFocusableInTouchMode(true);
        frame.setBackground(gradient(banner));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(banner ? 20 : 48);
        row.setPadding(pad, pad, pad, pad);

        // Left: who/when.
        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        left.addView(text("حان الآن موعد أذان", banner ? 18 : 24, MUTED, false));
        left.addView(text(arabicName, banner ? 48 : 84, GOLD, true));
        left.addView(text("It's time for " + englishName, banner ? 20 : 30, WHITE, true));
        left.addView(text(time + "  ·  " + place, banner ? 16 : 22, MUTED, false));
        progress = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(GOLD));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(banner ? 260 : 380), dp(8));
        plp.topMargin = dp(banner ? 10 : 24);
        left.addView(progress, plp);
        hint = text(status, banner ? 14 : 18, MUTED, false);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(10);
        left.addView(hint, hlp);

        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Right: the words of the athan (full-screen style only).
        textColumn = new LinearLayout(ctx);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        if (!banner) {
            fillAdhan(isFajr);
            View divider = new View(ctx);
            divider.setBackgroundColor(Color.parseColor("#55E2C275"));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT);
            dlp.leftMargin = dp(32);
            dlp.rightMargin = dp(32);
            row.addView(divider, dlp);
            row.addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));
        }

        frame.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                banner ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        attach(frame, banner, true);
        frame.requestFocus();
    }

    /** 0..1 shows progress; negative hides the bar. */
    public void setProgress(float fraction) {
        if (progress == null) return;
        if (fraction < 0) {
            progress.setVisibility(View.INVISIBLE);
        } else {
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(Math.round(Math.min(1f, fraction) * 1000));
        }
    }

    public void setStatus(String status) {
        if (hint != null) hint.setText(status);
    }

    /** Swaps the right column (or the banner line) to the after-athan supplication. */
    public void showDua() {
        if (root == null) return;
        setProgress(-1);
        if (bannerStyle) {
            setStatus(Texts.DUA_AR + "\n" + Texts.DUA_EN);
            return;
        }
        textColumn.removeAllViews();
        textColumn.addView(text(Texts.DUA_TITLE_AR, 26, GOLD, true));
        textColumn.addView(text(Texts.DUA_TITLE_EN, 16, MUTED, false));
        TextView ar = text(Texts.DUA_AR, 30, WHITE, true);
        ar.setLineSpacing(dp(6), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(18);
        textColumn.addView(ar, lp);
        TextView en = text(Texts.DUA_EN, 18, MUTED, false);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = dp(14);
        textColumn.addView(en, lp2);
        textColumn.addView(text(Texts.DUA_SOURCE, 14, MUTED, false));
    }

    // ------------------------------------------------------------------ reminder banner

    public void showReminder(String line1, String line2) {
        hide();
        bannerStyle = true;
        FrameLayout frame = new FrameLayout(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E6062A22"));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(2), GOLD);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(bg);
        col.setPadding(dp(28), dp(16), dp(28), dp(16));
        col.addView(text(line1, 26, GOLD, true));
        col.addView(text(line2, 18, WHITE, false));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lp.setMargins(dp(32), dp(32), dp(32), dp(32));
        frame.addView(col, lp);
        attach(frame, false, false);
    }

    // ------------------------------------------------------------------ common

    public void hide() {
        if (root != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
                // Already gone.
            }
        }
        root = null;
        progress = null;
        textColumn = null;
        hint = null;
    }

    @SuppressWarnings("deprecation")
    private void attach(View v, boolean banner, boolean interactive) {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        if (!interactive) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        // TYPE_APPLICATION_OVERLAY is required from Android 8.0; Android 7.1 (Fire OS 6) uses TYPE_PHONE.
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                banner ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        lp.gravity = banner ? Gravity.BOTTOM : Gravity.CENTER;
        lp.setTitle("AthanTV overlay");
        v.setAlpha(0f);
        wm.addView(v, lp);
        v.animate().alpha(1f).setDuration(450).start();
        root = v;
    }

    private void fillAdhan(boolean isFajr) {
        textColumn.removeAllViews();
        for (int i = 0; i < Texts.ADHAN.length; i++) {
            addPhrase(Texts.ADHAN[i]);
            if (isFajr && i == 4) addPhrase(Texts.FAJR_ADDITION);
        }
    }

    private void addPhrase(String[] pair) {
        // Sized so eight phrase pairs fit a 960x540 dp TV screen (the usual 1080p/720p layout size).
        TextView ar = text(pair[0], 24, WHITE, true);
        TextView en = text(pair[1], 12, MUTED, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        textColumn.addView(ar, lp);
        textColumn.addView(en);
    }

    private GradientDrawable gradient(boolean banner) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP, BG_BOTTOM});
        if (banner) g.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics()));
    }

    /** Root view that turns BACK / OK on the remote into "stop the athan". */
    private static final class KeyCatcher extends FrameLayout {
        private final Listener listener;

        KeyCatcher(Context c, Listener l) {
            super(c);
            listener = l;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            int code = event.getKeyCode();
            boolean stopKey = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_CENTER
                    || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_ESCAPE
                    || code == KeyEvent.KEYCODE_MEDIA_STOP || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            if (stopKey) {
                if (event.getAction() == KeyEvent.ACTION_UP && listener != null) listener.onDismissRequested();
                return true;
            }
            return super.dispatchKeyEvent(event); // volume keys etc. keep working
        }
    }
}
