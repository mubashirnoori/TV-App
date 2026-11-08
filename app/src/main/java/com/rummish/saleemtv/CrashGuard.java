package com.rummish.saleemtv;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Recovery for a launcher that replaces HOME: if the app crashes 3+ times within 60 s, the next start
 * clears "default launcher" and opens the original (PTCL) launcher instead of leaving the box in a crash loop.
 */
final class CrashGuard {
    private static final long WINDOW = 60000L;
    private static final int LIMIT = 3;

    static void install(Context c) {
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    long now = System.currentTimeMillis();
                    StringBuilder sb = new StringBuilder();
                    for (long x : recent(app, now)) sb.append(x).append(',');
                    sb.append(now);
                    Prefs.sp(app).edit().putString("crashes", sb.toString()).commit(); // commit: process is dying
                } catch (Throwable ignored) { }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    private static long[] recent(Context c, long now) {
        String s = Prefs.sp(c).getString("crashes", "");
        String[] parts = s.isEmpty() ? new String[0] : s.split(",");
        long[] tmp = new long[parts.length];
        int n = 0;
        for (String p : parts) {
            try { long v = Long.parseLong(p.trim()); if (now - v >= 0 && now - v < WINDOW) tmp[n++] = v; }
            catch (NumberFormatException ignored) { }
        }
        long[] r = new long[n];
        System.arraycopy(tmp, 0, r, 0, n);
        return r;
    }

    static boolean loopDetected(Context c) {
        return recent(c, System.currentTimeMillis()).length >= LIMIT;
    }

    static void reset(Context c) {
        SharedPreferences sp = Prefs.sp(c);
        sp.edit().remove("crashes").apply();
    }
}
