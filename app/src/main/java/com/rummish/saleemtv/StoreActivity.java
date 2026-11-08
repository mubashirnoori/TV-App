package com.rummish.saleemtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * App Store: a short list of free / open-source apps for a TV box. Each app is downloaded directly from its
 * official release page (GitHub releases, VideoLAN, Kodi mirrors) and handed to Android's installer.
 * Nothing is bundled. Android asks once to allow "Unknown sources".
 */
public class StoreActivity extends Activity {
    private static final int GH = 0, KODI = 1, VLC = 2, PLAY = 3;
    private static final String[] A32 = {"armeabi-v7a", "armv7", "armeabi"};
    private static final String[] A64 = {"arm64-v8a", "armv8", "arm64", "aarch64"};
    private static final String[] X86 = {"x86"};

    private static final class App {
        final String name, desc, emoji, src, web;
        final String[] pkgs;
        final int kind, c1, c2;
        App(String name, String desc, String emoji, int kind, String src, String web, String[] pkgs, int c1, int c2) {
            this.name = name; this.desc = desc; this.emoji = emoji; this.kind = kind; this.src = src;
            this.web = web; this.pkgs = pkgs; this.c1 = c1; this.c2 = c2;
        }
    }

    private static final App[] APPS = {
            new App("YouTube No Ads (SmartTube)", "YouTube for TV. No Google sign-in needed, ads blocked.", "▶", GH,
                    "yuliskov/SmartTube", "github.com/yuliskov/SmartTube/releases",
                    new String[]{"com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"},
                    0xFFFF5A5A, 0xFFB3202A),
            new App("Spotify (free)", "Music and podcasts. The free plan works - sign in or create a free account.", "🎵", PLAY,
                    "com.spotify.tv.android", "play.google.com/store/apps/details?id=com.spotify.tv.android",
                    new String[]{"com.spotify.tv.android", "com.spotify.music"}, 0xFF5BEA8A, 0xFF138A3E),
            new App("VLC", "Plays almost any video or audio file and network stream.", "🎬", VLC,
                    "https://get.videolan.org/vlc-android/", "videolan.org/vlc/download-android.html",
                    new String[]{"org.videolan.vlc"}, 0xFFFFA24D, 0xFFE8650A),
            new App("Kodi", "Media center for movies, music and photos (about 90 MB).", "🎭", KODI,
                    "https://mirrors.kodi.tv/releases/android/", "kodi.tv/download/android",
                    new String[]{"org.xbmc.kodi"}, 0xFF4FACFE, 0xFF1B5BD6),
            new App("TV Bro", "A web browser made for TV remotes.", "🌐", GH,
                    "truefedex/tv-bro", "github.com/truefedex/tv-bro/releases",
                    new String[]{"com.phlox.tvwebbrowser"}, 0xFF43E97B, 0xFF0E9A6A),
            new App("NewPipe", "Lightweight YouTube client without Google services.", "📺", GH,
                    "TeamNewPipe/NewPipe", "github.com/TeamNewPipe/NewPipe/releases",
                    new String[]{"org.schabi.newpipe"}, 0xFFFF7EB3, 0xFFD6246E),
            new App("LocalSend", "Send photos and files from your phone to this TV.", "📡", GH,
                    "localsend/localsend", "localsend.org",
                    new String[]{"org.localsend.localsend_app"}, 0xFFC471F5, 0xFF6E2FD6)
    };

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private volatile boolean cancelled;
    private boolean busy;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Util.init(this);
        Util.allowFileUris();
        FrameLayout root = new FrameLayout(this);
        WallpaperView wall = new WallpaperView(this);
        wall.setTheme(WallpaperView.THEMES[Prefs.sp(this).getInt("wp", 0) % WallpaperView.THEMES.length]);
        root.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Util.px(120), Util.px(46), Util.px(120), Util.px(60));
        TextView t = new TextView(this);
        t.setText("App Store");
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(72));
        col.addView(t);
        TextView s = new TextView(this);
        s.setText("Free and open-source apps for your TV. Press OK to install or open.");
        s.setTextColor(0xCCFFFFFF);
        s.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(28));
        s.setPadding(0, 0, 0, Util.px(20));
        col.addView(s);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        build();
    }

    private String installedPkg(App a) {
        PackageManager pm = getPackageManager();
        for (String p : a.pkgs) {
            try { pm.getPackageInfo(p, 0); return p; } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return null;
    }

    private void build() {
        int focusIdx = 0;
        View f = getCurrentFocus();
        if (f != null && f.getTag() instanceof Integer) focusIdx = (Integer) f.getTag();
        list.removeAllViews();
        View toFocus = null;
        for (int i = 0; i < APPS.length; i++) {
            final App a = APPS[i];
            final String inst = installedPkg(a);
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Util.px(26), Util.px(20), Util.px(30), Util.px(20));
            row.setFocusable(true);
            row.setClickable(true);
            row.setTag(i);
            row.setBackground(Util.round(0x22FFFFFF, Util.px(26)));
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) {
                    v.setBackground(Util.round(has ? 0x55FFFFFF : 0x22FFFFFF, Util.px(26)));
                }
            });

            TextView badge = new TextView(this);
            badge.setText(a.emoji);
            badge.setGravity(Gravity.CENTER);
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(52));
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a.c1, a.c2});
            g.setCornerRadius(Util.px(24));
            badge.setBackground(g);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Util.px(100), Util.px(100));
            bl.rightMargin = Util.px(28);
            row.addView(badge, bl);

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            TextView n = new TextView(this), d = new TextView(this);
            n.setText(a.name); d.setText(a.desc);
            n.setTextColor(Color.WHITE); d.setTextColor(0xFFC5C8D6);
            n.setSingleLine(true);
            n.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            n.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(36));
            d.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(25));
            txt.addView(n); txt.addView(d);
            row.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView btn = new TextView(this);
            btn.setText(inst != null ? "OPEN" : "GET");
            btn.setGravity(Gravity.CENTER);
            btn.setTextColor(Color.WHITE);
            btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            btn.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(28));
            btn.setBackground(Util.round(inst != null ? 0xFF30D158 : 0xFF0A84FF, Util.px(30)));
            btn.setPadding(Util.px(44), Util.px(12), Util.px(44), Util.px(12));
            row.addView(btn);

            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (inst != null) openApp(inst, a);
                    else confirmInstall(a);
                }
            });
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.bottomMargin = Util.px(18);
            list.addView(row, rl);
            if (i == focusIdx) toFocus = row;
        }
        if (toFocus != null) toFocus.requestFocus();
    }

    private void openApp(String pkg, App a) {
        try {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) throw new Exception("no launcher");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, a.name + " could not be opened on this box.", Toast.LENGTH_LONG).show();
        }
    }

    /** Spotify is closed-source and has no public APK page, so it is installed from Google Play (or used in a browser). */
    private void spotifyInstall(final App a) {
        final String[] opts = {"Install from Google Play", "Use Spotify in a browser (open.spotify.com)"};
        Util.dialog(this).setTitle("Get Spotify (free)")
                .setItems(opts, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        try {
                            if (w == 0) {
                                try {
                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + a.src))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                } catch (Exception noPlay) {
                                    // no Play Store on this box: try the Spotify phone app page in a browser, else explain
                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + a.src))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                }
                            } else {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            }
                        } catch (Exception e) {
                            Util.dialog(StoreActivity.this).setTitle("Cannot open that here")
                                    .setMessage("This box has no Google Play and no web browser. Install a browser first (TV Bro is in this store), "
                                            + "then choose \"Use Spotify in a browser\". Turn on Settings > Launcher > Remote works in all apps "
                                            + "if the page needs a mouse.")
                                    .setPositiveButton("OK", null).show();
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void confirmInstall(final App a) {
        if (busy) return;
        if (a.kind == PLAY) { spotifyInstall(a); return; }
        Util.dialog(this).setTitle("Install " + a.name + "?")
                .setMessage("It will be downloaded from its official release page (" + a.web + ") and installed on this box.\n\n"
                        + "If Android asks, allow \"Unknown sources\" for the installer.")
                .setPositiveButton("Install", (d, w) -> install(a))
                .setNegativeButton("Cancel", null).show();
    }

    // ---------- download + install ----------
    private void install(final App a) {
        busy = true;
        cancelled = false;
        final AlertDialog dlg = Util.dialog(this).setTitle("Downloading " + a.name)
                .setMessage("Looking for the right version for this box…")
                .setCancelable(false)
                .setNegativeButton("Cancel", (d, w) -> cancelled = true).create();
        dlg.show();
        new Thread(new Runnable() {
            @Override public void run() {
                final String[] err = new String[1];
                final File[] apk = new File[1];
                try {
                    String url = resolveUrl(a);
                    File dir = getExternalCacheDir();
                    if (dir == null) dir = getCacheDir();
                    apk[0] = new File(dir, a.pkgs[0] + ".apk");
                    Util.download(url, apk[0], 250L * 1024 * 1024, new Util.Progress() {
                        @Override public boolean on(long done, long total) {
                            if (cancelled) return false;
                            final String m = total > 0
                                    ? "Downloading… " + (done * 100 / total) + "%   (" + (done >> 20) + " of " + (total >> 20) + " MB)"
                                    : "Downloading… " + (done >> 20) + " MB";
                            ui.post(new Runnable() { public void run() { dlg.setMessage(m); } });
                            return true;
                        }
                    });
                    apk[0].setReadable(true, false);
                } catch (Throwable t) {
                    err[0] = cancelled ? "cancelled" : String.valueOf(t.getMessage());
                }
                ui.post(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        try { dlg.dismiss(); } catch (Exception ignored) { }
                        if (isDestroyed()) return;
                        if (err[0] != null) {
                            if (!cancelled) Util.dialog(StoreActivity.this).setTitle("Could not download")
                                    .setMessage("(" + err[0] + ")\n\nCheck the internet connection and try again. You can also get it from "
                                            + a.web + " with a browser.")
                                    .setPositiveButton("OK", null).show();
                            return;
                        }
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(Uri.fromFile(apk[0]), "application/vnd.android.package-archive")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (Exception ex) {
                            Toast.makeText(StoreActivity.this, "This box cannot open the installer.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }, "store").start();
    }

    private boolean isArm64() { return abi().startsWith("arm64"); }
    private boolean isX86() { return abi().startsWith("x86"); }
    private String abi() { return Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "armeabi-v7a"; }

    /** Finds the newest APK for this box's processor. Returns {name, url} pairs; throws with a readable message. */
    private String resolveUrl(App a) throws Exception {
        List<String[]> files = new ArrayList<>();
        if (a.kind == GH) {
            File j = new File(getCacheDir(), "store.json");
            Util.download("https://api.github.com/repos/" + a.src + "/releases/latest", j, 4L * 1024 * 1024);
            JSONArray as = new JSONObject(Util.readText(j)).getJSONArray("assets");
            for (int i = 0; i < as.length(); i++) {
                JSONObject o = as.getJSONObject(i);
                String n = o.getString("name");
                if (n.toLowerCase(Locale.ROOT).endsWith(".apk")) files.add(new String[]{n, o.getString("browser_download_url")});
            }
        } else if (a.kind == KODI) {
            files = listApks(a.src + (isArm64() ? "arm64-v8a/" : (isX86() ? "x86/" : "arm/")));
        } else {
            String top = fetchText(a.src);
            Matcher m = Pattern.compile("href=\"(\\d+(?:\\.\\d+)+)/\"").matcher(top);
            String best = null;
            while (m.find()) if (best == null || natCmp(m.group(1), best) > 0) best = m.group(1);
            if (best == null) throw new Exception("could not read the VLC download list");
            files = listApks(a.src + best + "/");
        }
        String url = pick(files);
        if (url == null) throw new Exception("no download for this box's processor (" + abi() + ")");
        return url;
    }

    private String fetchText(String url) throws Exception {
        File f = new File(getCacheDir(), "store.html");
        Util.download(url, f, 3L * 1024 * 1024);
        return Util.readText(f);
    }

    private List<String[]> listApks(String dirUrl) throws Exception {
        List<String[]> out = new ArrayList<>();
        Matcher m = Pattern.compile("href=\"([^\"?#]+\\.apk)\"", Pattern.CASE_INSENSITIVE).matcher(fetchText(dirUrl));
        while (m.find()) {
            String href = m.group(1);
            String name = href.substring(href.lastIndexOf('/') + 1);
            out.add(new String[]{name, new URL(new URL(dirUrl), href).toString()});
        }
        return out;
    }

    private static boolean hasAny(String name, String[] toks) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String t : toks) if (n.contains(t)) return true;
        return false;
    }

    private String pick(List<String[]> files) {
        String[][] order = isArm64() ? new String[][]{A64, A32} : (isX86() ? new String[][]{X86} : new String[][]{A32});
        for (String[] group : order) {
            String[] best = null;
            for (String[] f : files) if (hasAny(f[0], group) && (best == null || natCmp(f[0], best[0]) > 0)) best = f;
            if (best != null) return best[1];
        }
        // universal APK (no processor name in the file name)
        String[] best = null;
        for (String[] f : files) {
            if (hasAny(f[0], A32) || hasAny(f[0], A64) || f[0].toLowerCase(Locale.ROOT).contains("x86")) continue;
            if (best == null || natCmp(f[0], best[0]) > 0) best = f;
        }
        return best == null ? null : best[1];
    }

    /** Natural order compare: "21.10" > "21.2". */
    private static int natCmp(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", ""), nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                if (na.length() != nb.length()) return na.length() - nb.length();
                int c = na.compareTo(nb);
                if (c != 0) return c;
            } else {
                if (x != y) return x - y;
                i++; j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }
}
