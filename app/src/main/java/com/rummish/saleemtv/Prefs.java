package com.rummish.saleemtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

final class Prefs {
    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("rstv", Context.MODE_PRIVATE);
    }

    static List<String> list(Context c, String key) {
        String s = sp(c).getString(key, "");
        List<String> r = new ArrayList<>();
        if (s.length() > 0) for (String x : s.split("\n")) if (x.length() > 0) r.add(x);
        return r;
    }

    static void putList(Context c, String key, List<String> l) {
        sp(c).edit().putString(key, TextUtils.join("\n", l)).apply();
    }

    /** @return true if added, false if removed */
    static boolean toggle(Context c, String key, String v) {
        List<String> l = list(c, key);
        boolean added;
        if (l.contains(v)) { l.remove(v); added = false; } else { l.add(v); added = true; }
        putList(c, key, l);
        return added;
    }

    // ---- "On power-up open": iptv (Live TV) | home (stay on the home screen) | app (any installed app, see start_pkg/start_cls/start_label)
    static String startLabel(Context c, String s) {
        if ("home".equals(s)) return "Home screen only";
        if ("app".equals(s) || "digital".equals(s)) {
            String l = sp(c).getString("start_label", null);
            if (l != null && l.length() > 0) return l;
            return "digital".equals(s) ? "Digital TV" : "Chosen app";
        }
        return "Live TV";
    }

    /** Saves "open this installed app at power-up". */
    static void setStartApp(Context c, String pkg, String cls, String label) {
        sp(c).edit().putString("start_app", "app").putString("start_pkg", pkg)
                .putString("start_cls", cls == null ? "" : cls).putString("start_label", label).apply();
    }

    static void setStartSimple(Context c, String which) {
        sp(c).edit().putString("start_app", which).apply();
    }

    static void pushRecent(Context c, String id) {
        List<String> l = list(c, "recent");
        l.remove(id);
        l.add(0, id);
        while (l.size() > 6) l.remove(l.size() - 1);
        putList(c, "recent", l);
    }
}
