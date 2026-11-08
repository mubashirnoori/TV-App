package com.rummish.saleemtv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Kids Zone: big colourful cards, one press to start. Video cards use built-in public playlists automatically
 * (no PIN, no server to choose). Learning games work offline.
 */
public class KidsActivity extends Activity {
    private static final String B = "https://iptv-org.github.io/iptv/";
    // title, emoji, colour 1, colour 2, playlist, optional name filter
    private static final String[][] VIDEO = {
            {"Cartoons", "🎬", "FFB347", "FF5E3A", B + "categories/kids.m3u", ""},
            {"Learn & Explore", "📚", "4FACFE", "2B5CFF", B + "categories/education.m3u", ""},
            {"Kids Movies", "🍿", "C471F5", "7B2FF7", B + "categories/animation.m3u", ""},
            {"Islamic Learning", "🕌", "43E97B", "11998E", B + "categories/religious.m3u",
                    "quran|islam|makkah|madina|mecca|iqra|noor|madani|hadith|qtv|huda|peace tv|sunnah"}};

    private int cardW, cardH;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Util.init(this);
        cardW = Math.round(Util.screenW(this) / 5.5f);
        cardH = Math.round(cardW * 0.92f);

        FrameLayout root = new FrameLayout(this);
        WallpaperView wall = new WallpaperView(this);
        wall.setTheme(WallpaperView.KIDS);
        root.addView(wall, new FrameLayout.LayoutParams(-1, -1));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setPadding(Util.px(80), Util.px(50), Util.px(80), Util.px(70));

        TextView t = new TextView(this);
        t.setText("Kids Zone 🎈");
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(84));
        t.setShadowLayer(Util.px(8), 0, Util.px(5), 0x66000000);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        TextView sub = new TextView(this);
        sub.setText("Pick something fun!");
        sub.setTextColor(0xDDFFFFFF);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(36));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, Util.px(20));
        col.addView(sub);

        List<View> cards = new ArrayList<>();
        for (final String[] v : VIDEO) {
            View c = card(v[0], v[1], hex(v[2]), hex(v[3]));
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    Intent i = new Intent(KidsActivity.this, IptvActivity.class);
                    i.putExtra(IptvActivity.EXTRA_KIDS, true).putExtra(IptvActivity.EXTRA_URL, v[4])
                            .putExtra(IptvActivity.EXTRA_TITLE, v[0]).putExtra(IptvActivity.EXTRA_FILTER, v[5]);
                    startActivity(i);
                }
            });
            cards.add(c);
        }
        cards.add(learn("ABC Learning", "Aa", 0xFFFF6B8B, 0xFFE0245E, "abc"));
        cards.add(learn("Urdu Alphabet", "ا", 0xFF5EE7DF, 0xFF1E9BD7, "urdu"));
        cards.add(learn("Math Fun", "123", 0xFFFFE259, 0xFFFFA751, "math"));

        LinearLayout row = null;
        for (int i = 0; i < cards.size(); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                row.setClipChildren(false);
                row.setPadding(0, Util.px(26), 0, Util.px(10));
                col.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            lp.setMargins(Util.px(18), 0, Util.px(18), 0);
            row.addView(cards.get(i), lp);
        }
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (!cards.isEmpty()) cards.get(0).requestFocus();
    }

    private static int hex(String h) { return (int) Long.parseLong("FF" + h, 16); }

    private View learn(String name, String glyph, int c1, int c2, final String mode) {
        View v = card(name, glyph, c1, c2);
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                startActivity(new Intent(KidsActivity.this, LearnActivity.class).putExtra(LearnActivity.MODE, mode));
            }
        });
        return v;
    }

    /** Big rounded gradient card with a huge picture/letter and a label. Focus = grow + white ring + slight tilt. */
    private View card(String label, String glyph, int c1, int c2) {
        FrameLayout f = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        bg.setCornerRadius(Util.px(52));
        f.setBackground(bg);
        f.setFocusable(true);
        f.setClickable(true);

        TextView g = new TextView(this);
        g.setText(glyph);
        g.setGravity(Gravity.CENTER);
        g.setTextColor(Color.WHITE);
        g.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        g.setTextSize(TypedValue.COMPLEX_UNIT_PX, cardH * (glyph.length() > 2 ? 0.30f : 0.40f));
        g.setShadowLayer(Util.px(6), 0, Util.px(4), 0x44000000);
        f.addView(g, new FrameLayout.LayoutParams(-1, Math.round(cardH * 0.72f), Gravity.TOP));

        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(34));
        f.addView(t, new FrameLayout.LayoutParams(-1, Math.round(cardH * 0.26f), Gravity.BOTTOM));

        f.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).rotation(has ? -2f : 0f).setDuration(140).start();
                bg.setStroke(has ? Util.px(8) : 0, Color.WHITE);
            }
        });
        return f;
    }
}
