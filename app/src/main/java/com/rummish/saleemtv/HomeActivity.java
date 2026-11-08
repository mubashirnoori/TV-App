package com.rummish.saleemtv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends Activity {
    private static boolean splashDone = false; // splash only on a fresh process

    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private ScrollView scroll;
    private View splash;
    private LinearLayout rowApps, rowRecent, rowFav;
    private View secRecent;
    private TextView wifiTv;
    private List<AppEntry> apps;
    private final Map<String, AppEntry> byId = new HashMap<>();
    private String dynSig = "";
    private int tileW, gap;
    private long lastResolve;
    private BroadcastReceiver netRx;
    private WallpaperView wall;
    private boolean autoStart;
    private LinearLayout secWidgets;
    private TextView wLast, wxCity, wxTemp, wxDesc, wStor, wMem;
    private boolean wxLoading;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (CrashGuard.loopDetected(this) && escapeToOtherLauncher()) { finish(); return; }
        Util.init(this);
        tileW = Math.round(Util.screenW(this) / 6.4f);
        gap = Util.px(26);
        buildSkeleton();
        setContentView(root);
        Util.allowFileUris();
        if (!splashDone) { showSplash(); autoStart = true; }
        resolveAsync(true);
    }

    // ---------- UI skeleton ----------
    private void buildSkeleton() {
        root = new FrameLayout(this);
        wall = new WallpaperView(this);
        wall.setTheme(WallpaperView.THEMES[Prefs.sp(this).getInt("wp", 0) % WallpaperView.THEMES.length]);
        root.addView(wall, new FrameLayout.LayoutParams(-1, -1));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(0, Util.px(40), 0, Util.px(90));
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));

        // top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Util.px(80), 0, Util.px(80), Util.px(10));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(104));
        time.setIncludeFontPadding(false);
        left.addView(time);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(32));
        left.addView(date);
        bar.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        wifiTv = new TextView(this);
        wifiTv.setTextColor(0xCCFFFFFF);
        wifiTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(28));
        wifiTv.setPadding(0, 0, Util.px(40), 0);
        bar.addView(wifiTv, new LinearLayout.LayoutParams(-2, -2));
        TextView brand = new TextView(this);
        brand.setText("Rummish Saleem TV");
        brand.setTextColor(0x99FFFFFF);
        brand.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        brand.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(30));
        bar.addView(brand, new LinearLayout.LayoutParams(-2, -2));
        col.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        buildWidgets(col);

        LinearLayout[] out = new LinearLayout[1];
        col.addView(section("Apps", out)); rowApps = out[0];
        secRecent = section("Recently Used", out); col.addView(secRecent); rowRecent = out[0];
        col.addView(section("Favorites", out)); rowFav = out[0];
    }

    private LinearLayout section(String title, LinearLayout[] rowOut) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setClipChildren(false);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFFE0E0E6);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(34));
        t.setPadding(Util.px(80), Util.px(26), 0, 0);
        s.addView(t);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setPadding(Util.px(80), Util.px(26), Util.px(80), Util.px(26));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        hs.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        s.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        rowOut[0] = row;
        return s;
    }


    // ---------- widgets ----------
    private LinearLayout widgetCard(String title, boolean focusable) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Util.px(30), Util.px(20), Util.px(30), Util.px(20));
        c.setMinimumHeight(Util.px(150));
        c.setClipChildren(false);
        final GradientDrawable bg = Util.round(0x30FFFFFF, Util.px(30));
        c.setBackground(bg);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0x99FFFFFF);
        t.setLetterSpacing(0.08f);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(22));
        c.addView(t);
        if (focusable) {
            c.setFocusable(true);
            c.setClickable(true);
            c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean f) {
                    v.animate().scaleX(f ? 1.04f : 1f).scaleY(f ? 1.04f : 1f).setDuration(120).start();
                    bg.setStroke(f ? Util.px(5) : 0, Color.WHITE);
                }
            });
        }
        return c;
    }

    private TextView widgetText(LinearLayout parent, int sizePx, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(sizePx));
        parent.addView(t);
        return t;
    }

    private void buildWidgets(LinearLayout col) {
        secWidgets = new LinearLayout(this);
        secWidgets.setOrientation(LinearLayout.HORIZONTAL);
        secWidgets.setClipChildren(false);
        secWidgets.setPadding(Util.px(80), Util.px(24), Util.px(80), Util.px(6));

        // 1) Continue watching (focusable: OK opens Live TV, which resumes the last channel)
        LinearLayout a = widgetCard("CONTINUE WATCHING", true);
        wLast = widgetText(a, 42, Color.WHITE, true);
        wLast.setPadding(0, Util.px(10), 0, 0);
        TextView as = widgetText(a, 24, 0xB3FFFFFF, false);
        as.setText("\u25B6  Press OK to resume Live TV");
        a.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(HomeActivity.this, IptvActivity.class)); }
        });
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(Util.px(560), -2);
        la.rightMargin = Util.px(26);
        secWidgets.addView(a, la);

        // 2) Weather (open-meteo.com, no account or key needed)
        LinearLayout w = widgetCard("WEATHER", false);
        wxCity = (TextView) w.getChildAt(0);
        LinearLayout wr = new LinearLayout(this);
        wr.setOrientation(LinearLayout.HORIZONTAL);
        wr.setGravity(Gravity.CENTER_VERTICAL);
        wxTemp = new TextView(this);
        wxTemp.setTextColor(Color.WHITE);
        wxTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        wxTemp.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(66));
        wr.addView(wxTemp);
        wxDesc = new TextView(this);
        wxDesc.setTextColor(0xCCFFFFFF);
        wxDesc.setSingleLine(true);
        wxDesc.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(28));
        wxDesc.setPadding(Util.px(20), 0, 0, 0);
        wr.addView(wxDesc);
        w.addView(wr);
        LinearLayout.LayoutParams lw = new LinearLayout.LayoutParams(Util.px(440), -2);
        lw.rightMargin = Util.px(26);
        secWidgets.addView(w, lw);

        // 3) This box (storage + memory)
        LinearLayout b = widgetCard("THIS BOX", false);
        wStor = widgetText(b, 28, Color.WHITE, false);
        wStor.setPadding(0, Util.px(12), 0, Util.px(4));
        wMem = widgetText(b, 28, Color.WHITE, false);
        secWidgets.addView(b, new LinearLayout.LayoutParams(Util.px(440), -2));

        col.addView(secWidgets, new LinearLayout.LayoutParams(-1, -2));
    }

    private void refreshWidgets() {
        if (secWidgets == null) return;
        SharedPreferences sp = Prefs.sp(this);
        boolean on = sp.getBoolean("widgets", true);
        secWidgets.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) return;
        String ch = sp.getString("last_ch_name", null);
        wLast.setText(ch != null ? ch : "Live TV");
        wxCity.setText("WEATHER  \u00B7  " + sp.getString("wx_city", "Islamabad").toUpperCase(java.util.Locale.ROOT));
        String t = sp.getString("wx_temp", null);
        wxTemp.setText(t != null ? t : "--\u00B0");
        wxDesc.setText(sp.getString("wx_desc", "Waiting for internet"));
        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            wStor.setText("Storage   " + Formatter.formatFileSize(this, sf.getAvailableBlocksLong() * sf.getBlockSizeLong()) + " free");
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            wMem.setText("Memory   " + Formatter.formatFileSize(this, mi.availMem) + " free");
        } catch (Exception e) {
            wStor.setText("Storage   -");
            wMem.setText("Memory   -");
        }
        fetchWeather();
    }

    /** Current weather from open-meteo.com, at most every 30 minutes, in the background. A failure just keeps the last values. */
    private void fetchWeather() {
        final SharedPreferences sp = Prefs.sp(this);
        if (wxLoading || System.currentTimeMillis() - sp.getLong("wx_time", 0) < 30 * 60 * 1000L) return;
        wxLoading = true;
        final String lat = sp.getString("wx_lat", "33.6844"), lon = sp.getString("wx_lon", "73.0479");
        new Thread(new Runnable() {
            @Override public void run() {
                String temp = null, desc = null;
                try {
                    File f = new File(getCacheDir(), "wx.json");
                    Util.download("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                            + "&current_weather=true", f, 200000);
                    JSONObject cw = new JSONObject(Util.readText(f)).getJSONObject("current_weather");
                    temp = Math.round(cw.getDouble("temperature")) + "\u00B0";
                    desc = wxName(cw.getInt("weathercode"));
                } catch (Exception ignored) { }
                final String t = temp, d = desc;
                ui.post(new Runnable() {
                    @Override public void run() {
                        wxLoading = false;
                        if (isDestroyed()) return;
                        if (t != null) {
                            sp.edit().putString("wx_temp", t).putString("wx_desc", d)
                                    .putLong("wx_time", System.currentTimeMillis()).apply();
                            refreshWidgets();
                        }
                    }
                });
            }
        }, "weather").start();
    }

    private static String wxName(int c) {
        if (c == 0) return "Clear sky";
        if (c <= 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c >= 95) return "Thunderstorm";
        return "Weather";
    }

    // ---------- splash ----------
    private void showSplash() {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.BLACK);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_mark);
        c.addView(logo, new LinearLayout.LayoutParams(Util.px(170), Util.px(170)));
        TextView t = new TextView(this);
        t.setText("Rummish Saleem TV");
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(96));
        t.setLetterSpacing(0.02f);
        t.setPadding(0, Util.px(24), 0, Util.px(8));
        c.addView(t);
        TextView d = new TextView(this);
        d.setText("Developed by Rummish Saleem");
        d.setLetterSpacing(0.04f);
        d.setTextColor(0xCCFFFFFF);
        d.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        d.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(38));
        c.addView(d);
        f.addView(c, new FrameLayout.LayoutParams(-1, -1));
        c.setAlpha(0f);
        root.addView(f, new FrameLayout.LayoutParams(-1, -1));
        splash = f;
        c.animate().alpha(1f).setDuration(900).start();
        ui.postDelayed(this::dismissSplash, 2200);
    }

    private void dismissSplash() {
        final View s = splash;
        if (s == null) return;
        splash = null;
        splashDone = true;
        s.animate().alpha(0f).setDuration(450).withEndAction(new Runnable() {
            @Override public void run() { root.removeView(s); focusFirst(); maybeAutoStart(); }
        }).start();
    }

    /** On power-up (fresh process) go straight to Live TV. Back / the remote's apps button returns here. */
    private void maybeAutoStart() {
        if (!autoStart) return;
        autoStart = false;
        if (Prefs.sp(this).getBoolean("autostart", true)) startupApp(0);
    }

    /** Opens what is chosen under "On power-up open" (Live TV / Digital TV / nothing). Waits up to ~4 s for app detection; falls back to Live TV. */
    private void startupApp(final int tries) {
        if (isDestroyed()) return;
        String which = Prefs.sp(this).getString("start_app", "iptv");
        if ("home".equals(which)) return;
        if ("digital".equals(which)) {
            if (apps == null && tries < 10) {
                ui.postDelayed(new Runnable() { @Override public void run() { startupApp(tries + 1); } }, 400);
                return;
            }
            AppEntry e = byId.get("digital");
            if (e != null && e.available && e.intent != null) {
                try { Prefs.pushRecent(this, e.id); startActivity(e.intent); return; } catch (Exception ignored) { }
            }
        }
        try { startActivity(new Intent(this, IptvActivity.class)); } catch (Exception ignored) { }
    }

    // ---------- app detection ----------
    private void resolveAsync(final boolean first) {
        lastResolve = SystemClock.elapsedRealtime();
        new Thread(new Runnable() {
            @Override public void run() {
                final List<AppEntry> fresh = AppEntry.catalog();
                AppEntry.resolve(getApplicationContext(), fresh);
                // Apps that are not installed are hidden (the installable "YouTube No Ads" tile is the one exception).
                for (Iterator<AppEntry> it = fresh.iterator(); it.hasNext(); ) {
                    AppEntry x = it.next();
                    if (!x.available && x.kind != AppEntry.YTFREE) it.remove();
                }
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed()) return;
                        if (first || !sigOf(fresh).equals(sigOf(apps))) {
                            apps = fresh;
                            byId.clear();
                            for (AppEntry e : apps) byId.put(e.id, e);
                            buildAppsRow();
                            dynSig = "";
                            refreshDynamicRows();
                            if (splash == null) focusFirst();
                        }
                    }
                });
            }
        }, "resolve").start();
    }

    private static String sigOf(List<AppEntry> l) {
        StringBuilder sb = new StringBuilder();
        if (l != null) for (AppEntry e : l) sb.append(e.id).append(e.available ? '1' : '0');
        return sb.toString();
    }

    // ---------- rows ----------
    private TileView makeTile(final AppEntry e) {
        String sub = null;
        if (!e.available) sub = e.kind == AppEntry.YTFREE ? "Tap to install" : "Not installed";
        String glyph = e.glyph;
        if (!e.available && e.kind == AppEntry.EXTERNAL) glyph = e.label.substring(0, 1);
        TileView t = new TileView(this, tileW, e.label, sub, e.color, e.available ? e.icon : null, glyph, !e.available && e.kind != AppEntry.YTFREE);
        t.setTag(e);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launch(e); }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { toggleFavorite(e); return true; }
        });
        return t;
    }

    private void addTile(LinearLayout row, AppEntry e) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, -2);
        lp.rightMargin = gap;
        row.addView(makeTile(e), lp);
    }

    private void buildAppsRow() {
        rowApps.removeAllViews();
        for (AppEntry e : apps) addTile(rowApps, e);
    }

    private void refreshDynamicRows() {
        if (apps == null) return;
        List<String> rec = Prefs.list(this, "recent"), fav = Prefs.list(this, "fav");
        String sig = TextUtils.join(",", rec) + "|" + TextUtils.join(",", fav);
        if (sig.equals(dynSig)) return;
        dynSig = sig;
        View f = getCurrentFocus();
        boolean hadDynFocus = f != null && (isIn(f, rowRecent) || isIn(f, rowFav));

        rowRecent.removeAllViews();
        for (String id : rec) { AppEntry e = byId.get(id); if (e != null) addTile(rowRecent, e); }
        secRecent.setVisibility(rowRecent.getChildCount() > 0 ? View.VISIBLE : View.GONE);

        rowFav.removeAllViews();
        for (String id : fav) { AppEntry e = byId.get(id); if (e != null) addTile(rowFav, e); }
        if (rowFav.getChildCount() == 0) {
            TextView h = new TextView(this);
            h.setText("Hold OK (or press the RED button) on any tile to add it here");
            h.setTextColor(0xFF76767E);
            h.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(28));
            h.setPadding(0, Util.px(40), 0, Util.px(40));
            rowFav.addView(h);
        }
        if (hadDynFocus) focusFirst();
    }

    private static boolean isIn(View v, View parent) {
        while (v != null) { if (v == parent) return true; if (!(v.getParent() instanceof View)) return false; v = (View) v.getParent(); }
        return false;
    }

    private void focusFirst() {
        if (rowApps.getChildCount() > 0) rowApps.getChildAt(0).requestFocus();
    }

    // ---------- actions ----------
    private void launch(AppEntry e) {
        switch (e.kind) {
            case AppEntry.IPTV:
                Prefs.pushRecent(this, e.id);
                startActivity(new Intent(this, IptvActivity.class));
                return;
            case AppEntry.KIDS:
                Prefs.pushRecent(this, e.id);
                startActivity(new Intent(this, KidsActivity.class));
                return;
            case AppEntry.SETTINGS:
                startActivity(new Intent(this, SettingsActivity.class));
                return;
            case AppEntry.STORE:
                Prefs.pushRecent(this, e.id);
                startActivity(new Intent(this, StoreActivity.class));
                return;
            case AppEntry.FILES:
                Prefs.pushRecent(this, e.id);
                startActivity(new Intent(this, FileManagerActivity.class));
                return;
            case AppEntry.YTFREE:
                if (!e.available) { startActivity(new Intent(this, StoreActivity.class)); return; }
                break;
            case AppEntry.AIRPLAY:
                if (!e.available) { showAirPlayInfo(); return; }
                break;
            default:
                break;
        }
        if (!e.available || e.intent == null) {
            toast(e.label + " is not installed on this box. Install a version compatible with Android 7.1 and it will appear here automatically.");
            return;
        }
        try {
            Prefs.pushRecent(this, e.id);
            startActivity(e.intent);
        } catch (Exception ex) {
            toast(e.label + " is installed but could not be opened on this device (it may be incompatible with Android 7.1).");
        }
    }

    private void toggleFavorite(AppEntry e) {
        boolean added = Prefs.toggle(this, "fav", e.id);
        toast(added ? e.label + " added to Favorites" : e.label + " removed from Favorites");
        refreshDynamicRows();
        if (rowApps.getFocusedChild() == null && getCurrentFocus() == null) focusFirst();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void showAirPlayInfo() {
        Util.dialog(this).setTitle("AirPlay on this box")
                .setMessage("No AirPlay-capable receiver app is installed.\n\n"
                        + "Android 7.1 has no built-in AirPlay. Install a receiver app that supports your box "
                        + "(for example AirScreen, free with ads, if it runs on 1GB RAM), then reopen this tile "
                        + "- it is detected automatically and launched from here.\n\n"
                        + "Photos/videos via AirPlay are usually fine; full iPhone screen mirroring can stutter "
                        + "on this hardware. See README for details.")
                .setPositiveButton("OK", null).show();
    }

    // ---------- keys ----------
    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (splash != null) { dismissSplash(); return true; }
        View f = getCurrentFocus();
        AppEntry fe = (f != null && f.getTag() instanceof AppEntry) ? (AppEntry) f.getTag() : null;
        if (k == KeyEvent.KEYCODE_PROG_RED && fe != null) { toggleFavorite(fe); return true; }
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_INFO || k == KeyEvent.KEYCODE_PROG_YELLOW) {
            showMenu(fe);
            return true;
        }
        return super.onKeyDown(k, e);
    }

    @Override public void onBackPressed() { /* a launcher has nowhere to go back to */ }

    private void showMenu(final AppEntry fe) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (fe != null) {
            boolean isFav = Prefs.list(this, "fav").contains(fe.id);
            labels.add(isFav ? "Remove \"" + fe.label + "\" from Favorites" : "Add \"" + fe.label + "\" to Favorites");
            acts.add(new Runnable() { public void run() { toggleFavorite(fe); } });
        }
        final boolean auto = Prefs.sp(this).getBoolean("autostart", true);
        labels.add("Start with Live TV when the TV turns on: " + (auto ? "ON" : "OFF"));
        acts.add(new Runnable() { public void run() {
            Prefs.sp(HomeActivity.this).edit().putBoolean("autostart", !auto).apply();
            toast(!auto ? "Live TV will start automatically." : "The home screen will open first.");
        } });
        final String sa = Prefs.sp(this).getString("start_app", "iptv");
        final boolean digOk = byId.get("digital") != null;
        labels.add("On power-up open: " + Prefs.startLabel(sa) + "   (OK = change)");
        acts.add(new Runnable() { public void run() {
            String n = Prefs.nextStart(sa, digOk);
            Prefs.sp(HomeActivity.this).edit().putString("start_app", n).apply();
            toast("On power-up the box will open: " + Prefs.startLabel(n));
        } });
        labels.add("Change wallpaper");
        acts.add(new Runnable() { public void run() {
            int n = (Prefs.sp(HomeActivity.this).getInt("wp", 0) + 1) % WallpaperView.THEMES.length;
            Prefs.sp(HomeActivity.this).edit().putInt("wp", n).apply();
            wall.setTheme(WallpaperView.THEMES[n]);
        } });
        final boolean live = Prefs.sp(this).getBoolean("wp_live", true);
        labels.add("Live wallpaper (moving background): " + (live ? "ON" : "OFF"));
        acts.add(new Runnable() { public void run() {
            Prefs.sp(HomeActivity.this).edit().putBoolean("wp_live", !live).apply();
            wall.setAnimated(!live);
        } });
        final boolean wid = Prefs.sp(this).getBoolean("widgets", true);
        labels.add("Home widgets (weather, resume TV, box status): " + (wid ? "ON" : "OFF"));
        acts.add(new Runnable() { public void run() {
            Prefs.sp(HomeActivity.this).edit().putBoolean("widgets", !wid).apply();
            refreshWidgets();
        } });
        labels.add("Return to original PTCL launcher");
        acts.add(new Runnable() { public void run() { openOtherLauncher(); } });
        labels.add("Set Rummish Saleem TV as default launcher");
        acts.add(new Runnable() { public void run() { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } });
        labels.add("Stop being the default launcher");
        acts.add(new Runnable() { public void run() { clearDefault(); } });
        labels.add("Android Settings");
        acts.add(new Runnable() { public void run() { AppEntry s = byId.get("settings"); if (s != null) launch(s); } });
        labels.add("Remote key tester");
        acts.add(new Runnable() { public void run() { startActivity(new Intent(HomeActivity.this, KeyTestActivity.class)); } });
        Util.dialog(this).setTitle("Rummish Saleem TV")
                .setItems(labels.toArray(new String[0]), (d, which) -> acts.get(which).run())
                .setNegativeButton("Close", null).show();
    }

    @SuppressWarnings("deprecation")
    private void clearDefault() {
        try {
            getPackageManager().clearPackagePreferredActivities(getPackageName());
            toast("Done. Press HOME and choose a launcher (tick \"Always\" to remember).");
        } catch (Exception ex) {
            toast("Could not clear. Use Settings > Apps > Rummish Saleem TV > Clear defaults.");
        }
    }

    /** Crash-loop escape: stop being default and open the original launcher. */
    @SuppressWarnings("deprecation")
    private boolean escapeToOtherLauncher() {
        CrashGuard.reset(this);
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo r : getPackageManager().queryIntentActivities(home, 0)) {
                if (r.activityInfo.packageName.equals(getPackageName())) continue;
                getPackageManager().clearPackagePreferredActivities(getPackageName());
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setClassName(r.activityInfo.packageName, r.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void openOtherLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        final List<ResolveInfo> others = new ArrayList<>();
        for (ResolveInfo r : getPackageManager().queryIntentActivities(home, 0))
            if (!r.activityInfo.packageName.equals(getPackageName())) others.add(r);
        if (others.isEmpty()) { toast("No other launcher found on this device."); return; }
        if (others.size() == 1) { startOther(others.get(0)); return; }
        String[] names = new String[others.size()];
        for (int i = 0; i < names.length; i++)
            names[i] = others.get(i).loadLabel(getPackageManager()) + "  (" + others.get(i).activityInfo.packageName + ")";
        Util.dialog(this).setTitle("Choose launcher")
                .setItems(names, (d, w) -> startOther(others.get(w))).show();
    }

    private void startOther(ResolveInfo r) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClassName(r.activityInfo.packageName, r.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ex) {
            toast("Could not open that launcher.");
        }
    }

    // ---------- lifecycle ----------
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (scroll != null && splash == null) { scroll.smoothScrollTo(0, 0); focusFirst(); }
    }

    @Override protected void onStart() {
        super.onStart();
        if (wifiTv == null) return; // crash-loop escape finished us before the UI was built
        updateNet();
        netRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { updateNet(); }
        };
        registerReceiver(netRx, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override protected void onStop() {
        super.onStop();
        if (netRx != null) { unregisterReceiver(netRx); netRx = null; }
    }

    @Override protected void onResume() {
        super.onResume();
        if (wall == null) return; // crash-loop escape finished us before the UI was built
        wall.setTheme(WallpaperView.THEMES[Prefs.sp(this).getInt("wp", 0) % WallpaperView.THEMES.length]); // may have changed in Settings
        wall.setAnimated(Prefs.sp(this).getBoolean("wp_live", true));
        refreshWidgets();
        refreshDynamicRows();
        if (apps != null && SystemClock.elapsedRealtime() - lastResolve > 15000) resolveAsync(false);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
    }

    private void updateNet() {
        String s;
        try {
            NetworkInfo ni = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) s = "\u25CB  Offline";
            else if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                String ssid = "";
                try {
                    WifiInfo wi = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo();
                    if (wi != null && wi.getSSID() != null && !wi.getSSID().contains("unknown")) ssid = "  " + wi.getSSID().replace("\"", "");
                } catch (Exception ignored) { }
                s = "\u25CF  Wi\u2011Fi" + ssid;
            } else if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) s = "\u25CF  Ethernet";
            else s = "\u25CF  Connected";
        } catch (Exception e) {
            s = "";
        }
        wifiTv.setText(s);
    }
}
