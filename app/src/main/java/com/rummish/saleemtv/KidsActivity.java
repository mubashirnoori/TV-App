package com.rummish.saleemtv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Kids Zone. Video categories show ONLY what a parent configured (no content is hard-wired).
 * Parent Settings are protected by a 4-digit PIN (stored as a salted SHA-256 hash).
 */
public class KidsActivity extends Activity {
    private static final String B = "https://iptv-org.github.io/iptv/";
    // key, title, glyph, color, suggested source (prefilled for the parent to REVIEW, never auto-enabled)
    private static final String[][] VIDEO = {
            {"cartoons", "Cartoons", "\uD83C\uDFAC", String.valueOf(0xFFFF9500), B + "categories/kids.m3u"},
            {"edu", "Educational Videos", "\uD83D\uDCDA", String.valueOf(0xFF0A84FF), B + "categories/education.m3u"},
            {"movies", "Kids Movies", "\uD83C\uDF9E", String.valueOf(0xFFBF5AF2), ""},
            {"islamic", "Islamic Learning", "\uD83D\uDD4C", String.valueOf(0xFF30D158), B + "categories/religious.m3u"}};

    private LinearLayout grid;
    private int tileW;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Util.init(this);
        tileW = Math.round(Util.screenW(this) / 5.6f);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0B0B16);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Util.px(90), Util.px(50), Util.px(90), Util.px(80));
        col.setClipChildren(false);
        TextView t = new TextView(this);
        t.setText("Kids Zone");
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(70));
        col.addView(t);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        col.addView(grid);
        sv.addView(col);
        setContentView(sv);
    }

    @Override protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        grid.removeAllViews();
        List<TileView> tiles = new ArrayList<>();
        for (final String[] v : VIDEO) {
            if (!Prefs.sp(this).getBoolean("kids_en_" + v[0], true)) continue;
            final String url = Prefs.sp(this).getString("kids_src_" + v[0], "");
            TileView tv = new TileView(this, tileW, v[1], url.isEmpty() ? "Not set up yet" : null,
                    Integer.parseInt(v[3]), null, v[2], false);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    String u = Prefs.sp(KidsActivity.this).getString("kids_src_" + v[0], "");
                    if (u.isEmpty()) { toast("Ask a parent to set this up in Parent Settings."); return; }
                    Intent i = new Intent(KidsActivity.this, IptvActivity.class);
                    i.putExtra(IptvActivity.EXTRA_KIDS, true).putExtra(IptvActivity.EXTRA_URL, u).putExtra(IptvActivity.EXTRA_TITLE, v[1]);
                    startActivity(i);
                }
            });
            tiles.add(tv);
        }
        tiles.add(learn("ABC Learning", "Aa", 0xFFFF375F, "abc"));
        tiles.add(learn("Urdu Alphabet", "\u0627", 0xFF64D2FF, "urdu"));
        tiles.add(learn("Math Fun", "123", 0xFFFFD60A, "math"));
        TileView p = new TileView(this, tileW, "Parent Settings", "PIN protected", 0xFF3A3A3C, null, "\uD83D\uDD12", false);
        p.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { parentGate(); }
        });
        tiles.add(p);

        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setPadding(0, Util.px(34), 0, 0);
                grid.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, -2);
            lp.rightMargin = Util.px(36);
            row.addView(tiles.get(i), lp);
        }
        if (tiles.size() > 0) tiles.get(0).requestFocus();
    }

    private TileView learn(String name, String glyph, int color, final String mode) {
        TileView t = new TileView(this, tileW, name, null, color, null, glyph, false);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                startActivity(new Intent(KidsActivity.this, LearnActivity.class).putExtra(LearnActivity.MODE, mode));
            }
        });
        return t;
    }

    // ---------- parent PIN ----------
    private void parentGate() {
        if (!Prefs.sp(this).contains("pin_hash")) createPin(new Runnable() { public void run() { parentMenu(); } });
        else askPin(new Runnable() { public void run() { parentMenu(); } });
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        return e;
    }

    private void askPin(final Runnable ok) {
        final EditText e = pinField("4-digit PIN");
        Util.dialog(this).setTitle("Parent PIN").setView(e)
                .setPositiveButton("OK", (d, w) -> {
                    String salt = Prefs.sp(this).getString("pin_salt", "");
                    if (hash(salt, e.getText().toString()).equals(Prefs.sp(this).getString("pin_hash", ""))) ok.run();
                    else toast("Wrong PIN");
                }).setNegativeButton("Cancel", null).show();
    }

    private void createPin(final Runnable ok) {
        final EditText a = pinField("New 4-digit PIN"), c = pinField("Repeat PIN");
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Util.px(30), Util.px(10), Util.px(30), 0);
        l.addView(a); l.addView(c);
        Util.dialog(this).setTitle("Create parent PIN").setView(l)
                .setPositiveButton("Save", (d, w) -> {
                    String p1 = a.getText().toString();
                    if (p1.length() != 4 || !p1.equals(c.getText().toString())) { toast("PINs must match and be 4 digits"); return; }
                    byte[] sb = new byte[8];
                    new SecureRandom().nextBytes(sb);
                    StringBuilder salt = new StringBuilder();
                    for (byte x : sb) salt.append(String.format("%02x", x));
                    Prefs.sp(this).edit().putString("pin_salt", salt.toString()).putString("pin_hash", hash(salt.toString(), p1)).apply();
                    toast("PIN saved");
                    ok.run();
                }).setNegativeButton("Cancel", null).show();
    }

    private static String hash(String salt, String pin) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((salt + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : h) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    // ---------- parent settings ----------
    private void parentMenu() {
        final List<String> items = new ArrayList<>();
        for (String[] v : VIDEO) {
            boolean en = Prefs.sp(this).getBoolean("kids_en_" + v[0], true);
            String src = Prefs.sp(this).getString("kids_src_" + v[0], "");
            items.add(v[1] + "  -  " + (en ? "shown" : "hidden") + "  -  " + (src.isEmpty() ? "no source" : "source set"));
        }
        items.add("Change PIN");
        Util.dialog(this).setTitle("Parent Settings")
                .setItems(items.toArray(new String[0]), (d, w) -> {
                    if (w < VIDEO.length) editCategory(VIDEO[w]);
                    else createPin(new Runnable() { public void run() { } });
                }).setNegativeButton("Close", null).show();
    }

    private void editCategory(final String[] v) {
        final EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        String saved = Prefs.sp(this).getString("kids_src_" + v[0], "");
        e.setText(saved.isEmpty() ? v[4] : saved);
        boolean en = Prefs.sp(this).getBoolean("kids_en_" + v[0], true);
        Util.dialog(this).setTitle(v[1] + " - playlist (M3U/M3U8 URL)")
                .setMessage((saved.isEmpty() && !v[4].isEmpty()
                        ? "A suggested public source is prefilled. Community playlists are NOT individually vetted for children - review it before saving, or replace it with your own.\n\n" : "")
                        + "Leave empty and Save to clear.")
                .setView(e)
                .setPositiveButton("Save", (d, w) -> {
                    Prefs.sp(this).edit().putString("kids_src_" + v[0], e.getText().toString().trim()).apply();
                    toast("Saved"); build();
                })
                .setNeutralButton(en ? "Hide this category" : "Show this category", (d, w) -> {
                    Prefs.sp(this).edit().putBoolean("kids_en_" + v[0], !en).apply();
                    build();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
