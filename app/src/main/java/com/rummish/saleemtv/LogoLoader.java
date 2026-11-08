package com.rummish.saleemtv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny channel-logo loader: 2 threads, ~3 MB LRU cache, bitmaps decoded at thumbnail size. Failures are remembered. */
final class LogoLoader {
    interface Cb { boolean wanted(); void done(Bitmap b); }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(3 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final Set<String> FAILED = new HashSet<>();
    private static final ExecutorService EX = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static Bitmap peek(String url) { return CACHE.get(url); }

    static boolean failed(String url) { synchronized (FAILED) { return FAILED.contains(url); } }

    static void load(final String url, final int target, final Cb cb) {
        if (url == null || !url.startsWith("http") || failed(url)) return;
        EX.execute(new Runnable() {
            @Override public void run() {
                if (!cb.wanted()) return; // row was recycled/scrolled away: skip the download
                Bitmap b = CACHE.get(url);
                if (b == null) {
                    b = fetch(url, target);
                    if (b == null) { synchronized (FAILED) { FAILED.add(url); } return; }
                    CACHE.put(url, b);
                }
                final Bitmap fb = b;
                MAIN.post(new Runnable() { @Override public void run() { if (cb.wanted()) cb.done(fb); } });
            }
        });
    }

    private static Bitmap fetch(String url, int target) {
        HttpURLConnection c = null;
        InputStream in = null;
        try {
            String cur = url;
            for (int i = 0; i < 4; i++) {
                c = (HttpURLConnection) new URL(cur).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(6000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "RummishSaleemTV/1.0 (Android TV)");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null) return null;
                    cur = new URL(new URL(cur), loc).toString();
                    c = null;
                    continue;
                }
                if (code != 200) return null;
                break;
            }
            if (c == null) return null;
            in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, tot = 0;
            while ((n = in.read(buf)) > 0) {
                tot += n;
                if (tot > 600 * 1024) return null; // logos are small; refuse big files
                bo.write(buf, 0, n);
            }
            byte[] d = bo.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(d, 0, d.length, o);
            if (o.outWidth <= 0) return null;
            int s = 1;
            while (o.outWidth / (s * 2) >= target && o.outHeight / (s * 2) >= target) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            return BitmapFactory.decodeByteArray(d, 0, d.length, o);
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            if (c != null) c.disconnect();
        }
    }
}
