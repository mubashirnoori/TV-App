package com.rummish.saleemtv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Apple-TV-style focusable tile: rounded card + label. Focus = gentle scale + white ring. */
final class TileView extends LinearLayout {
    private final GradientDrawable bg;
    private final TextView label;

    TileView(Context c, int w, String text, String sub, int color, Drawable icon, String glyph, boolean dim) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(true);
        setClickable(true);
        setClipChildren(false);

        int ch = Math.round(w * 0.58f);
        FrameLayout card = new FrameLayout(c);
        bg = Util.round(color, Util.px(30));
        card.setBackground(bg);
        addView(card, new LayoutParams(w, ch));

        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int s = Math.round(ch * 0.68f);
            card.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        } else {
            TextView g = new TextView(c);
            g.setText(glyph);
            g.setTextColor(Color.WHITE);
            g.setGravity(Gravity.CENTER);
            g.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            g.setTextSize(TypedValue.COMPLEX_UNIT_PX, ch * (glyph.length() > 2 ? 0.26f : 0.45f));
            card.addView(g, new FrameLayout.LayoutParams(-1, -1));
        }
        if (dim) card.setAlpha(0.45f);

        label = new TextView(c);
        label.setText(text);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setTextColor(0xFF9A9AA0);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(30));
        label.setPadding(0, Util.px(14), 0, 0);
        addView(label, new LayoutParams(-1, -2));

        if (sub != null) {
            TextView s = new TextView(c);
            s.setText(sub);
            s.setGravity(Gravity.CENTER);
            s.setSingleLine(true);
            s.setTextColor(0xFF6E6E73);
            s.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(22));
            addView(s, new LayoutParams(-1, -2));
        }

        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                animate().scaleX(f ? 1.09f : 1f).scaleY(f ? 1.09f : 1f).setDuration(120).start();
                bg.setStroke(f ? Util.px(5) : 0, Color.WHITE);
                label.setTextColor(f ? Color.WHITE : 0xFF9A9AA0);
            }
        });
    }
}
