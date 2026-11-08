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

    // ---- "On power-up open": iptv (Live TV) | digital (Digital TV app) | home (stay on the home screen)
    static String startLabel(String s) {
        if ("digital".equals(s)) return "Digital TV";
        if ("home".equals(s)) return "Home screen only";
        return "Live TV";
    }

    static String nextStart(String cur, boolean digitalAvailable) {
        if ("iptv".equals(cur)) return digitalAvailable ? "digital" : "home";
        if ("digital".equals(cur)) return "home";
        return "iptv";
    }

    static void pushRecent(Context c, String id) {
        List<String> l = list(c, "recent");
        l.remove(id);
        l.add(0, id);
        while (l.size() > 6) l.remove(l.size() - 1);
        putList(c, "recent", l);
    }
}
