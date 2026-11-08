package com.rummish.saleemtv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One home-screen tile. External apps are detected from what is really installed. */
final class AppEntry {
    static final int EXTERNAL = 0, IPTV = 1, KIDS = 2, SETTINGS = 3, AIRPLAY = 4, FILES = 5, YTFREE = 6, STORE = 7;

    final String id, label, glyph;
    final int kind, color;
    final String[] pkgs, keys; // keys: "=exact label" or "label contains"
    Intent intent;
    Drawable icon;
    boolean available;
    boolean extra; // any other installed app, listed under "All Apps"

    AppEntry(String id, String label, int kind, int color, String[] pkgs, String[] keys, String glyph) {
        this.id = id; this.label = label; this.kind = kind; this.color = color;
        this.pkgs = pkgs; this.keys = keys; this.glyph = glyph;
    }

    private static final String[] NONE = new String[0];

    static List<AppEntry> catalog() {
        List<AppEntry> l = new ArrayList<>();
        l.add(new AppEntry("iptv", "Live TV", IPTV, 0xFF0A4FA8, NONE, NONE, "LIVE"));
        // "Digital TV" = the operator's streaming app (package com.fun.faisal, label "Faisal"). We only launch it; the app itself is untouched.
        l.add(new AppEntry("digital", "Digital TV", EXTERNAL, 0xFF0B7A5C,
                new String[]{"com.fun.faisal"}, new String[]{"=faisal"}, "D"));
        l.add(new AppEntry("ytfree", "YouTube No Ads", YTFREE, 0xFFB3202A,
                new String[]{"com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"},
                new String[]{"smarttube"}, "\u25B6"));
        l.add(new AppEntry("youtube", "YouTube", EXTERNAL, 0xFF2B1416,
                new String[]{"com.google.android.youtube.tv", "com.google.android.youtube"}, new String[]{"=youtube"}, "\u25B6"));
        l.add(new AppEntry("netflix", "Netflix", EXTERNAL, 0xFF1A1A1C,
                new String[]{"com.netflix.ninja", "com.netflix.mediaclient"}, new String[]{"=netflix"}, "N"));
        l.add(new AppEntry("tapmad", "Tapmad", EXTERNAL, 0xFF14202B, NONE, new String[]{"tapmad"}, "T"));
        l.add(new AppEntry("tamasha", "Tamasha", EXTERNAL, 0xFF241A2B, NONE, new String[]{"tamasha"}, "T"));
        l.add(new AppEntry("prime", "Prime Video", EXTERNAL, 0xFF10222B,
                new String[]{"com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"},
                new String[]{"prime video"}, "P"));
        l.add(new AppEntry("spotify", "Spotify", EXTERNAL, 0xFF0F2B1A,
                new String[]{"com.spotify.tv.android", "com.spotify.music"}, new String[]{"=spotify"}, "S"));
        l.add(new AppEntry("vlc", "VLC", EXTERNAL, 0xFF2B1D10,
                new String[]{"org.videolan.vlc"}, new String[]{"=vlc", "vlc for"}, "V"));
        l.add(new AppEntry("kids", "Kids Zone", KIDS, 0xFFE8741A, NONE, NONE, "\u2605"));
        l.add(new AppEntry("files", "Files", FILES, 0xFF2D5BD0, NONE, NONE, "\uD83D\uDCC1"));
        l.add(new AppEntry("store", "App Store", STORE, 0xFF1B6EF3, NONE, NONE, "\uD83D\uDCE5"));
        l.add(new AppEntry("chrome", "Chrome", EXTERNAL, 0xFF1C1C1E,
                new String[]{"com.android.chrome"}, new String[]{"=chrome", "browser"}, "C"));
        l.add(new AppEntry("airplay", "AirPlay", AIRPLAY, 0xFF1D2B24,
                new String[]{"com.ionitech.airscreen"},
                new String[]{"airscreen", "airplay", "airreceiver", "air receiver", "letsview"}, "\u25B2"));
        l.add(new AppEntry("settings", "Settings", SETTINGS, 0xFF2C2C2E, NONE, NONE, "S"));
        return l;
    }

    /** Fills intent/icon/available for every entry. Call off the main thread. */
    static void resolve(Context c, List<AppEntry> list) {
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        all.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        String self = c.getPackageName();

        for (AppEntry e : list) {
            if (e.kind == IPTV || e.kind == KIDS || e.kind == FILES || e.kind == STORE) { e.available = true; continue; }
            if (e.kind == SETTINGS) {
                Intent si = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ResolveInfo r = pm.resolveActivity(si, 0);
                e.intent = si;
                e.available = true; // our own Settings app always opens
                if (r != null) e.icon = r.loadIcon(pm);
                continue;
            }
            ResolveInfo m = null;
            for (String p : e.pkgs) {
                for (ResolveInfo r : all) if (r.activityInfo.packageName.equals(p)) { m = r; break; }
                if (m != null) break;
            }
            if (m == null) {
                for (String k : e.keys) {
                    boolean exact = k.startsWith("=");
                    String key = exact ? k.substring(1) : k;
                    for (ResolveInfo r : all) {
                        if (r.activityInfo.packageName.equals(self)) continue;
                        CharSequence lab = r.loadLabel(pm);
                        if (lab == null) continue;
                        String s = lab.toString().toLowerCase(Locale.ROOT);
                        if (exact ? s.equals(key) : s.contains(key)) { m = r; break; }
                    }
                    if (m != null) break;
                }
            }
            if (m != null) {
                e.intent = new Intent(Intent.ACTION_MAIN)
                        .setComponent(new ComponentName(m.activityInfo.packageName, m.activityInfo.name))
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                e.icon = m.loadIcon(pm);
                e.available = true;
            } else {
                e.intent = null; e.icon = null; e.available = false;
            }
        }
    }
}
