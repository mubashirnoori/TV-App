package com.rummish.saleemtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * "Aurora" wallpaper: one linear gradient + three soft glowing blobs. No bitmaps (1 GB RAM friendly).
 * Optional LIVE mode: the blobs drift slowly (about 20 frames/s, shaders are built once, no allocation per frame).
 * Animation runs only while the window is visible, so it costs nothing while a video or another app is on screen.
 */
final class WallpaperView extends View {
    // base top, base bottom, blob1, blob2, blob3
    static final int[][] THEMES = {
            {0xFF241B5E, 0xFF07091F, 0xFF8B5CFF, 0xFF1FA2FF, 0xFFFF4F9A}, // Aurora
            {0xFF063B4F, 0xFF020F1A, 0xFF00D4C8, 0xFF2D7BFF, 0xFF7CFFCB}, // Ocean
            {0xFF4A1442, 0xFF140616, 0xFFFF7A45, 0xFFFF3D7F, 0xFFB05CFF}, // Sunset
            {0xFF0F3B2E, 0xFF04120D, 0xFF2EE59D, 0xFF14B8A6, 0xFFB6F500}, // Forest
            {0xFF1A1F3A, 0xFF050609, 0xFF4F6BFF, 0xFF9B6BFF, 0xFF2EC5FF}  // Midnight
    };
    static final int[] KIDS = {0xFF5B2EFF, 0xFF1A0B6B, 0xFFFF4FA3, 0xFFFFC53D, 0xFF2EE0FF};

    // blob anchors (fraction of width/height), radius (fraction of height), drift speed (rad/s) and phase
    private static final float[] AX = {0.82f, 0.12f, 0.55f};
    private static final float[] AY = {0.18f, 0.85f, 0.55f};
    private static final float[] AR = {0.75f, 0.80f, 0.55f};
    private static final float[] SP = {0.11f, 0.08f, 0.14f};
    private static final float[] PH = {0f, 2.1f, 4.2f};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] t = THEMES[0];
    private boolean animated, visible = true, dirty = true;
    private int bw, bh;
    private LinearGradient base;
    private final RadialGradient[] glow = new RadialGradient[3];

    WallpaperView(Context c) { super(c); }

    void setTheme(int[] th) { t = th; dirty = true; invalidate(); }

    /** Live (slowly moving) wallpaper on/off. */
    void setAnimated(boolean on) {
        if (animated == on) return;
        animated = on;
        invalidate();
    }

    @Override protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        visible = (v == VISIBLE);
        if (visible && animated) invalidate();
    }

    private void build(int w, int h) {
        base = new LinearGradient(0, 0, w * 0.3f, h, t[0], t[1], Shader.TileMode.CLAMP);
        for (int i = 0; i < 3; i++) {
            int rgb = t[2 + i] & 0x00FFFFFF;
            float r = h * AR[i];
            // centred on (0,0): the canvas is translated to the blob position when drawing
            glow[i] = new RadialGradient(0, 0, r, rgb | 0x55000000, rgb, Shader.TileMode.CLAMP);
        }
        bw = w; bh = h; dirty = false;
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (dirty || w != bw || h != bh) build(w, h);

        p.setShader(base);
        c.drawRect(0, 0, w, h, p);

        float tm = animated ? (SystemClock.uptimeMillis() % 6000000L) / 1000f : 0f;
        for (int i = 0; i < 3; i++) {
            float x = w * AX[i] + (animated ? (float) Math.sin(tm * SP[i] + PH[i]) * w * 0.10f : 0f);
            float y = h * AY[i] + (animated ? (float) Math.cos(tm * SP[i] * 0.8f + PH[i]) * h * 0.12f : 0f);
            c.save();
            c.translate(x, y);
            p.setShader(glow[i]);
            c.drawCircle(0, 0, h * AR[i], p);
            c.restore();
        }
        if (animated && visible) postInvalidateDelayed(50);
    }
}
