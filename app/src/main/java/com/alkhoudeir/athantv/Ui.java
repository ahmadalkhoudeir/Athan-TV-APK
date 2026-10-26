package com.alkhoudeir.athantv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Small view helpers shared by the activities. Everything is built in code (no XML layouts). */
final class Ui {

    static final int GOLD = Color.parseColor("#E2C275");
    static final int AMBER = Color.parseColor("#FFB74D");
    static final int GREEN_OK = Color.parseColor("#81C784");
    static final int MUTED = Color.parseColor("#B7C9C2");
    static final int CARD = Color.parseColor("#1AFFFFFF");
    static final int CARD_NEXT = Color.parseColor("#33E2C275");
    static final int BG_TOP = Color.parseColor("#062A22");
    static final int BG_BOTTOM = Color.parseColor("#01100D");

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static Drawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG_BOTTOM});
    }

    /** Rounded card that shows a gold outline when focused with the D-pad. */
    static Drawable card(Context c, boolean highlighted) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(highlighted ? CARD_NEXT : CARD);
        normal.setCornerRadius(dp(c, 16));
        if (highlighted) normal.setStroke(dp(c, 2), Color.parseColor("#88E2C275"));

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(highlighted ? Color.parseColor("#55E2C275") : Color.parseColor("#33FFFFFF"));
        focused.setCornerRadius(dp(c, 16));
        focused.setStroke(dp(c, 3), GOLD);

        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{android.R.attr.state_pressed}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    static Button button(Context c, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTextColor(Color.WHITE);
        b.setBackground(card(c, false));
        int p = dp(c, 14);
        b.setPadding(dp(c, 20), p, dp(c, 20), p);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(l);
        b.setFocusable(true);
        return b;
    }

    static AlertDialog.Builder dialog(Activity a) {
        return new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert);
    }
}
