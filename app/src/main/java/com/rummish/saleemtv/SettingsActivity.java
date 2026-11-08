package com.rummish.saleemtv;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * iOS-style Settings: sections on the left, grouped rounded cards on the right.
 * Device-level pages open the box's own Android settings screens; launcher / Live TV / wallpaper settings live here.
 */
public class SettingsActivity extends Activity {
    private static final String[] SEC = {"Network", "Display & Sound", "Live TV", "Launcher", "Apps & Storage", "System", "About"};
    private static final int[] SCOL = {0xFF0A84FF, 0xFF5E5CE6, 0xFFFF453A, 0xFFFF9F0A, 0xFF30D158, 0xFF8E8E93, 0xFF64D2FF};
    private static final String[] SIC = {"📶", "🖥", "📺", "🏠", "📦", "⚙", "ℹ"};

    private interface BoolCb { void on(boolean v); }

    private static final class IosSwitch extends View {
        boolean on;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        IosSwitch(Context c, boolean on) { super(c); this.on = on; }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(Util.px(86), Util.px(52)); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(on ? 0xFF30D158 : 0xFF636366);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, h / 2, h / 2, p);
            p.setColor(Color.WHITE);
            c.drawCircle(on ? w - h / 2 : h / 2, h / 2, h / 2 - Util.px(4), p);
        }
    }

    private final View[] side = new View[SEC.length];
    private LinearLayout pane;
    private ScrollView paneScroll;
    private int sel = -1;
    private View firstRow;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Util.init(this);
        FrameLayout root = new FrameLayout(this);
        WallpaperView wall = new WallpaperView(this);
        wall.setTheme(WallpaperView.THEMES[Prefs.sp(this).getInt("wp", 0) % WallpaperView.THEMES.length]);
        root.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackgroundColor(0xB3000000); // dark, iOS-style
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setPadding(Util.px(70), Util.px(40), Util.px(70), Util.px(30));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText("Settings");
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(64));
        t.setPadding(Util.px(16), 0, 0, Util.px(24));
        left.addView(t);
        for (int i = 0; i < SEC.length; i++) {
            side[i] = sideItem(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Util.px(8);
            left.addView(side[i], lp);
        }
        main.addView(left, new LinearLayout.LayoutParams(Math.round(Util.screenW(this) * 0.28f), -1));

        paneScroll = new ScrollView(this);
        paneScroll.setVerticalScrollBarEnabled(false);
        paneScroll.setClipToPadding(false);
        pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPadding(Util.px(40), Util.px(10), Util.px(20), Util.px(60));
        paneScroll.addView(pane, new ScrollView.LayoutParams(-1, -2));
        main.addView(paneScroll, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        select(0);
        side[0].requestFocus();
    }

    // ---------- sidebar ----------
    private View sideItem(final int i) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Util.px(16), Util.px(12), Util.px(16), Util.px(12));
        r.setFocusable(true);
        r.setClickable(true);
        r.setId(View.generateViewId());
        r.addView(iconBox(SIC[i], SCOL[i], 64), new LinearLayout.LayoutParams(Util.px(64), Util.px(64)));
        TextView n = new TextView(this);
        n.setText(SEC[i]);
        n.setTextColor(Color.WHITE);
        n.setSingleLine(true);
        n.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(32));
        n.setPadding(Util.px(20), 0, 0, 0);
        r.addView(n);
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                if (has) select(i);
                refreshSide();
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (firstRow != null) firstRow.requestFocus(); }
        });
        return r;
    }

    private TextView iconBox(String emoji, int color, int sizePx) {
        TextView ic = new TextView(this);
        ic.setText(emoji);
        ic.setGravity(Gravity.CENTER);
        ic.setTextColor(Color.WHITE);
        ic.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(sizePx * 0.55f));
        ic.setBackground(Util.round(color, Util.px(sizePx * 0.24f)));
        return ic;
    }

    private void refreshSide() {
        for (int j = 0; j < side.length; j++) {
            int c = side[j].hasFocus() ? 0x55FFFFFF : (j == sel ? 0x26FFFFFF : 0);
            side[j].setBackground(c == 0 ? null : Util.round(c, Util.px(20)));
        }
    }

    private boolean isSide(View v) {
        for (View s : side) if (s == v) return true;
        return false;
    }

    private void select(int i) {
        if (i == sel) return;
        sel = i;
        pane.removeAllViews();
        firstRow = null;
        switch (i) {
            case 0: pageNetwork(); break;
            case 1: pageDisplay(); break;
            case 2: pageLiveTv(); break;
            case 3: pageLauncher(); break;
            case 4: pageApps(); break;
            case 5: pageSystem(); break;
            default: pageAbout(); break;
        }
        for (View s : side) s.setNextFocusRightId(firstRow != null ? firstRow.getId() : View.NO_ID);
        paneScroll.scrollTo(0, 0);
        refreshSide();
    }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK) {
            View f = getCurrentFocus();
            if (f != null && !isSide(f) && sel >= 0) { side[sel].requestFocus(); return true; } // back: pane -> sidebar
        }
        return super.onKeyDown(k, e);
    }

    // ---------- building blocks ----------
    private LinearLayout group(String header) {
        if (header != null) {
            TextView h = new TextView(this);
            h.setText(header.toUpperCase());
            h.setTextColor(0xFF9A9DB0);
            h.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(22));
            h.setPadding(Util.px(24), Util.px(26), 0, Util.px(10));
            pane.addView(h);
        } else {
            View sp = new View(this);
            pane.addView(sp, new LinearLayout.LayoutParams(-1, Util.px(20)));
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Util.round(0x2EFFFFFF, Util.px(26)));
        pane.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private LinearLayout row(LinearLayout card, String emoji, int color, String title, String value, boolean chevron, final Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Util.px(24), Util.px(16), Util.px(26), Util.px(16));
        if (emoji != null) {
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Util.px(58), Util.px(58));
            il.rightMargin = Util.px(22);
            r.addView(iconBox(emoji, color, 58), il);
        }
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(31));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        if (value != null) {
            TextView v = new TextView(this);
            v.setText(value);
            v.setTextColor(0xFFA5A8BA);
            v.setSingleLine(true);
            v.setGravity(Gravity.END);
            v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(27));
            r.addView(v, new LinearLayout.LayoutParams(-2, -2));
        }
        if (chevron) {
            TextView c = new TextView(this);
            c.setText("›");
            c.setTextColor(0xFF8E8E93);
            c.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(44));
            c.setPadding(Util.px(16), 0, 0, 0);
            r.addView(c);
        }
        if (card.getChildCount() > 0) {
            View div = new View(this);
            div.setBackgroundColor(0x22FFFFFF);
            card.addView(div, new LinearLayout.LayoutParams(-1, 1));
        }
        if (action != null) {
            r.setFocusable(true);
            r.setClickable(true);
            r.setId(View.generateViewId());
            r.setNextFocusLeftId(side[sel].getId());
            r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { action.run(); } });
            r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) {
                    v.setBackground(has ? Util.round(0x55FFFFFF, Util.px(22)) : null);
                }
            });
            if (firstRow == null) firstRow = r;
        }
        card.addView(r);
        return r;
    }

    private void info(LinearLayout card, String title, String value) { row(card, null, 0, title, value, false, null); }

    private void switchRow(LinearLayout card, String emoji, int color, String title, boolean init, final BoolCb cb) {
        final IosSwitch sw = new IosSwitch(this, init);
        LinearLayout r = row(card, emoji, color, title, null, false, new Runnable() {
            @Override public void run() { sw.on = !sw.on; sw.invalidate(); cb.on(sw.on); }
        });
        r.addView(sw);
    }

    private Runnable go(final String action) {
        return new Runnable() { @Override public void run() { safeStart(new Intent(action)); } };
    }

    private void safeStart(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                toast("That page is not available on this box - opened general Android settings.");
            } catch (Exception e2) {
                toast("Not available on this box.");
            }
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    // ---------- pages ----------
    private void pageNetwork() {
        LinearLayout g = group("Connection");
        row(g, "📶", 0xFF0A84FF, "Wi-Fi", netLabel(), true, go(Settings.ACTION_WIFI_SETTINGS));
        row(g, "🔌", 0xFF30B0C7, "Ethernet & other networks", null, true, go(Settings.ACTION_WIRELESS_SETTINGS));
        row(g, "🎧", 0xFF5E5CE6, "Bluetooth remotes & devices", null, true, go(Settings.ACTION_BLUETOOTH_SETTINGS));
        LinearLayout s = group("Status");
        info(s, "Internet", netLabel());
        info(s, "IP address", ip());
    }

    private void pageDisplay() {
        LinearLayout g = group("Device");
        row(g, "🖥", 0xFF5E5CE6, "Display", "Screen size, overscan", true, go(Settings.ACTION_DISPLAY_SETTINGS));
        row(g, "🔊", 0xFFFF453A, "Sound", "Volume, HDMI audio", true, go(Settings.ACTION_SOUND_SETTINGS));
        LinearLayout w = group("Wallpaper");
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(Util.px(18), Util.px(18), Util.px(18), Util.px(18));
        int cur = Prefs.sp(this).getInt("wp", 0) % WallpaperView.THEMES.length;
        final String[] names = {"Aurora", "Ocean", "Sunset", "Forest", "Midnight"};
        for (int i = 0; i < WallpaperView.THEMES.length; i++) {
            final int idx = i;
            final FrameLayout cell = new FrameLayout(this);
            cell.setFocusable(true);
            cell.setClickable(true);
            cell.setId(View.generateViewId());
            cell.setNextFocusLeftId(i == 0 ? side[sel].getId() : View.NO_ID);
            cell.setPadding(Util.px(7), Util.px(7), Util.px(7), Util.px(7));
            WallpaperView wv = new WallpaperView(this);
            wv.setTheme(WallpaperView.THEMES[i]);
            wv.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Util.px(14)); }
            });
            wv.setClipToOutline(true);
            cell.addView(wv, new FrameLayout.LayoutParams(Util.px(190), Util.px(108)));
            TextView lab = new TextView(this);
            lab.setText((i == cur ? "✅ " : "") + names[i]);
            lab.setTextColor(Color.WHITE);
            lab.setGravity(Gravity.CENTER);
            lab.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(24));
            lab.setShadowLayer(Util.px(4), 0, Util.px(2), 0xAA000000);
            cell.addView(lab, new FrameLayout.LayoutParams(-1, -1));
            cell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) { v.setBackground(has ? Util.round(0x77FFFFFF, Util.px(20)) : null); }
            });
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Prefs.sp(SettingsActivity.this).edit().putInt("wp", idx).apply();
                    int keep = sel;
                    sel = -1; select(keep); // rebuild to move the check mark
                    if (firstRow != null) firstRow.requestFocus();
                }
            });
            strip.addView(cell);
            if (firstRow == null) firstRow = cell;
        }
        w.addView(strip);

        LinearLayout hs = group("Home screen");
        switchRow(hs, "\u2728", 0xFFFF9F0A, "Live wallpaper (slowly moving background)", Prefs.sp(this).getBoolean("wp_live", true), new BoolCb() {
            @Override public void on(boolean v) { Prefs.sp(SettingsActivity.this).edit().putBoolean("wp_live", v).apply(); }
        });
        switchRow(hs, "\uD83E\uDDE9", 0xFF5E5CE6, "Widgets (resume TV, weather, box status)", Prefs.sp(this).getBoolean("widgets", true), new BoolCb() {
            @Override public void on(boolean v) { Prefs.sp(SettingsActivity.this).edit().putBoolean("widgets", v).apply(); }
        });
        row(hs, "\uD83C\uDF24", 0xFF0A84FF, "Weather city", Prefs.sp(this).getString("wx_city", "Islamabad"), true, new Runnable() {
            @Override public void run() {
                Util.prompt(SettingsActivity.this, "Weather city", "e.g. Lahore",
                        Prefs.sp(SettingsActivity.this).getString("wx_city", "Islamabad"), new Util.OnText() {
                            @Override public void got(final String name) {
                                if (name.isEmpty()) return;
                                toast("Looking up " + name + "...");
                                new Thread(new Runnable() {
                                    @Override public void run() {
                                        String city = null, lat = null, lon = null;
                                        try {
                                            File f = new File(getCacheDir(), "geo.json");
                                            Util.download("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                                                    + java.net.URLEncoder.encode(name, "UTF-8"), f, 100000);
                                            org.json.JSONArray r = new org.json.JSONObject(Util.readText(f)).optJSONArray("results");
                                            if (r != null && r.length() > 0) {
                                                org.json.JSONObject o = r.getJSONObject(0);
                                                city = o.getString("name");
                                                lat = String.valueOf(o.getDouble("latitude"));
                                                lon = String.valueOf(o.getDouble("longitude"));
                                            }
                                        } catch (Exception ignored) { }
                                        final String fc = city, fa = lat, fo = lon;
                                        runOnUiThread(new Runnable() {
                                            @Override public void run() {
                                                if (isDestroyed()) return;
                                                if (fc == null) { toast("City not found (or no internet)."); return; }
                                                Prefs.sp(SettingsActivity.this).edit().putString("wx_city", fc)
                                                        .putString("wx_lat", fa).putString("wx_lon", fo)
                                                        .putLong("wx_time", 0).apply();
                                                toast("Weather city set to " + fc);
                                                int keep = sel;
                                                sel = -1; select(keep);
                                            }
                                        });
                                    }
                                }, "geocode").start();
                            }
                        });
            }
        });
    }

    private void pageLiveTv() {
        LinearLayout g = group("Live TV");
        switchRow(g, "⏻", 0xFF30D158, "Open an app automatically when the TV turns on", Prefs.sp(this).getBoolean("autostart", true), new BoolCb() {
            @Override public void on(boolean v) { Prefs.sp(SettingsActivity.this).edit().putBoolean("autostart", v).apply(); }
        });
        row(g, "📺", 0xFFFF453A, "Open Live TV", null, true, new Runnable() {
            @Override public void run() { startActivity(new Intent(SettingsActivity.this, IptvActivity.class)); }
        });
        LinearLayout d = group("Data");
        row(d, "📖", 0xFF0A84FF, "TV guide (EPG) address", Prefs.sp(this).getString("epg_url", "").isEmpty() ? "Automatic" : "Custom", true, new Runnable() {
            @Override public void run() {
                Util.prompt(SettingsActivity.this, "EPG (XMLTV) URL", "http://…/epg.xml or .xml.gz",
                        Prefs.sp(SettingsActivity.this).getString("epg_url", ""), new Util.OnText() {
                            @Override public void got(String s) {
                                Prefs.sp(SettingsActivity.this).edit().putString("epg_url", s).apply();
                                toast("Saved. It is used the next time a playlist loads.");
                            }
                        });
            }
        });
        row(d, "⭐", 0xFFFF9F0A, "Clear favorite channels", null, false, new Runnable() {
            @Override public void run() {
                Util.dialog(SettingsActivity.this).setTitle("Clear favorite channels?")
                        .setPositiveButton("Clear", (x, w) -> { Prefs.putList(SettingsActivity.this, "fav_ch", new ArrayList<String>()); toast("Favorites cleared"); })
                        .setNegativeButton("Cancel", null).show();
            }
        });
        row(d, "🗑", 0xFF8E8E93, "Clear saved playlists", "Re-downloaded next time", false, new Runnable() {
            @Override public void run() {
                int n = 0;
                File[] fs = getFilesDir().listFiles();
                if (fs != null) for (File f : fs) if (f.getName().startsWith("pl_") && f.delete()) n++;
                toast(n + " saved playlist(s) removed");
            }
        });
    }

    private void pageLauncher() {
        final boolean digOk = getPackageManager().getLaunchIntentForPackage("com.fun.faisal") != null;
        LinearLayout pu = group("When the TV turns on");
        row(pu, "\u23FB", 0xFF30D158, "On power-up open", Prefs.startLabel(Prefs.sp(this).getString("start_app", "iptv")), true, new Runnable() {
            @Override public void run() {
                final java.util.List<String> keys = new java.util.ArrayList<String>();
                final java.util.List<String> names = new java.util.ArrayList<String>();
                keys.add("iptv"); names.add("Live TV");
                if (digOk) { keys.add("digital"); names.add("Digital TV"); }
                keys.add("home"); names.add("Home screen only");
                Util.dialog(SettingsActivity.this).setTitle("On power-up open")
                        .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) {
                                Prefs.sp(SettingsActivity.this).edit().putString("start_app", keys.get(w)).apply();
                                int keep = sel;
                                sel = -1; select(keep);
                            }
                        }).show();
            }
        });
        if (!digOk) info(pu, "Digital TV", "Not installed on this box");
        LinearLayout g = group("Home screen");
        row(g, "🏠", 0xFFFF9F0A, "Make this the default home screen", null, true, new Runnable() {
            @Override public void run() {
                toast("Press HOME on the remote and choose Rummish Saleem TV (tick \"Always\").");
                safeStart(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
            }
        });
        row(g, "↩", 0xFF8E8E93, "Stop being the default home screen", null, false, new Runnable() {
            @SuppressWarnings("deprecation")
            @Override public void run() {
                try {
                    getPackageManager().clearPackagePreferredActivities(getPackageName());
                    toast("Done. Press HOME and choose a launcher.");
                } catch (Exception e) { toast("Use Android Settings > Apps > Rummish Saleem TV > Clear defaults."); }
            }
        });
        row(g, "📡", 0xFF0A84FF, "Open the original PTCL home screen", null, true, new Runnable() {
            @Override public void run() { openOtherLauncher(); }
        });
        LinearLayout t = group("Tools");
        row(t, "🎮", 0xFF5E5CE6, "Remote key tester", "Shows each button's code", true, new Runnable() {
            @Override public void run() { startActivity(new Intent(SettingsActivity.this, KeyTestActivity.class)); }
        });
    }

    private void openOtherLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        final List<ResolveInfo> others = new ArrayList<>();
        for (ResolveInfo r : getPackageManager().queryIntentActivities(home, 0))
            if (!r.activityInfo.packageName.equals(getPackageName())) others.add(r);
        if (others.isEmpty()) { toast("No other home screen found on this box."); return; }
        if (others.size() == 1) { startOther(others.get(0)); return; }
        String[] names = new String[others.size()];
        for (int i = 0; i < names.length; i++)
            names[i] = others.get(i).loadLabel(getPackageManager()) + "  (" + others.get(i).activityInfo.packageName + ")";
        Util.dialog(this).setTitle("Choose home screen").setItems(names, (d, w) -> startOther(others.get(w))).show();
    }

    private void startOther(ResolveInfo r) {
        safeStart(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClassName(r.activityInfo.packageName, r.activityInfo.name));
    }

    private void pageApps() {
        LinearLayout g = group("Apps");
        row(g, "📥", 0xFF1B6EF3, "App Store", "Get free apps", true, new Runnable() {
            @Override public void run() { startActivity(new Intent(SettingsActivity.this, StoreActivity.class)); }
        });
        row(g, "📁", 0xFF2D5BD0, "Files", null, true, new Runnable() {
            @Override public void run() { startActivity(new Intent(SettingsActivity.this, FileManagerActivity.class)); }
        });
        row(g, "📦", 0xFF30D158, "Manage installed apps", "Uninstall, clear data", true, go(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS));
        LinearLayout s = group("Storage");
        row(s, "💾", 0xFF8E8E93, "Storage", storageFree(), true, go(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
    }

    private void pageSystem() {
        LinearLayout g = group("General");
        row(g, "🕒", 0xFF0A84FF, "Date & time", null, true, go(Settings.ACTION_DATE_SETTINGS));
        row(g, "🌍", 0xFF30D158, "Language", null, true, go(Settings.ACTION_LOCALE_SETTINGS));
        row(g, "⌨", 0xFF8E8E93, "Keyboard", null, true, go(Settings.ACTION_INPUT_METHOD_SETTINGS));
        row(g, "♿", 0xFF5E5CE6, "Accessibility", null, true, go(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        LinearLayout a = group("Advanced");
        row(a, "🛠", 0xFFFF9F0A, "Developer options", "USB / network debugging", true, go(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        row(a, "⚙", 0xFF636366, "All Android settings", "The box's original settings", true, go(Settings.ACTION_SETTINGS));
    }

    private void pageAbout() {
        LinearLayout g = group("This TV box");
        info(g, "Model", Build.MANUFACTURER + " " + Build.MODEL);
        info(g, "Android", Build.VERSION.RELEASE + "  (API " + Build.VERSION.SDK_INT + ")");
        info(g, "Memory (RAM)", ramTotal());
        info(g, "Storage free", storageFree());
        info(g, "IP address", ip());
        LinearLayout a = group("Rummish Saleem TV");
        String ver = "1.0";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName;
        } catch (Exception ignored) { }
        info(a, "Version", ver);
        info(a, "Developed by", "Rummish Saleem");
    }

    // ---------- device info ----------
    private String netLabel() {
        try {
            NetworkInfo ni = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return "Not connected";
            if (ni.getType() == ConnectivityManager.TYPE_WIFI) return "Wi-Fi";
            if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) return "Ethernet";
            return "Connected";
        } catch (Exception e) { return "Unknown"; }
    }

    private static String ip() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) { }
        return "Not connected";
    }

    private String ramTotal() {
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
            return Formatter.formatFileSize(this, mi.totalMem);
        } catch (Exception e) { return "Unknown"; }
    }

    private String storageFree() {
        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            return Formatter.formatFileSize(this, sf.getAvailableBytes()) + " of " + Formatter.formatFileSize(this, sf.getTotalBytes());
        } catch (Exception e) { return "Unknown"; }
    }
}
