package com.rummish.saleemtv;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.StrictMode;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.widget.EditText;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

/** Small shared helpers. Everything is sized relative to a 1080p screen so the UI scales on any TV. */
final class Util {
    interface OnText { void got(String s); }

    static float S = 1f; // pixels per "1080p pixel"

    static void init(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        S = Math.min(m.widthPixels, m.heightPixels) / 1080f;
    }

    static int mix(int a, int b, float t) {
        return Color.argb(255,
                Math.round(Color.red(a) * (1 - t) + Color.red(b) * t),
                Math.round(Color.green(a) * (1 - t) + Color.green(b) * t),
                Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    /** Android 7 refuses file:// Uris in intents (FileUriExposedException). Needed to open files / install an APK without FileProvider. */
    static void allowFileUris() {
        try { StrictMode.class.getMethod("disableDeathOnFileUriExposure").invoke(null); } catch (Throwable ignored) { }
    }

    static int px(float v) { return Math.round(v * S); }

    static int screenW(Context c) { return c.getResources().getDisplayMetrics().widthPixels; }

    static GradientDrawable round(int color, int radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert);
    }

    static void prompt(Context c, String title, String hint, String initial, final OnText cb) {
        final EditText et = new EditText(c);
        et.setSingleLine(true);
        et.setHint(hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (initial != null) et.setText(initial);
        dialog(c).setTitle(title).setView(et)
                .setPositiveButton("OK", (d, w) -> cb.got(et.getText().toString().trim()))
                .setNegativeButton("Cancel", null).show();
    }

    /** Downloads (or copies a local path) to dest, following http<->https redirects, size-capped. */
    interface Progress { boolean on(long done, long total); } // return false to cancel

    static void download(String url, File dest, long max) throws IOException { download(url, dest, max, null); }

    static String readText(File f) throws IOException {
        InputStream in = openMaybeGzip(f);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return bo.toString("UTF-8");
        } finally { in.close(); }
    }

    static void download(String url, File dest, long max, Progress pr) throws IOException {
        File tmp = new File(dest.getPath() + ".tmp");
        long total = -1, lastTick = 0;
        InputStream in = null;
        HttpURLConnection c = null;
        try {
            if (url.startsWith("/") || url.startsWith("file://")) {
                in = new FileInputStream(url.startsWith("file://") ? url.substring(7) : url);
            } else {
                String cur = url;
                int code = 0;
                for (int i = 0; i < 6; i++) {
                    c = (HttpURLConnection) new URL(cur).openConnection();
                    c.setInstanceFollowRedirects(false);
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(20000);
                    c.setRequestProperty("User-Agent", "RummishSaleemTV/1.0 (Android TV)");
                    code = c.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String loc = c.getHeaderField("Location");
                        c.disconnect();
                        c = null;
                        if (loc == null) throw new IOException("Bad redirect");
                        cur = new URL(new URL(cur), loc).toString();
                        continue;
                    }
                    break;
                }
                if (c == null || code != 200) throw new IOException("HTTP " + code);
                in = c.getInputStream();
                total = c.getContentLength();
            }
            OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp));
            try {
                byte[] buf = new byte[16384];
                long tot = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    tot += n;
                    if (tot > max) throw new IOException("File too large");
                    out.write(buf, 0, n);
                    if (pr != null) {
                        long now = android.os.SystemClock.elapsedRealtime();
                        if (now - lastTick > 400) {
                            lastTick = now;
                            if (!pr.on(tot, total)) throw new IOException("Cancelled");
                        }
                    }
                }
            } finally {
                out.close();
            }
            if (!tmp.renameTo(dest)) {
                dest.delete();
                if (!tmp.renameTo(dest)) throw new IOException("Cannot save file");
            }
        } finally {
            try { if (in != null) in.close(); } catch (IOException ignored) { }
            if (c != null) c.disconnect();
            tmp.delete();
        }
    }

    static InputStream openMaybeGzip(File f) throws IOException {
        BufferedInputStream b = new BufferedInputStream(new FileInputStream(f), 64 * 1024);
        b.mark(2);
        int b0 = b.read(), b1 = b.read();
        b.reset();
        if (b0 == 0x1f && b1 == 0x8b) return new GZIPInputStream(b, 64 * 1024);
        return b;
    }
}
