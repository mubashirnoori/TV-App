package com.rummish.saleemtv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

/**
 * Makes the TV remote usable in touch-only apps: a mouse-style cursor you move with the D-pad.
 * OK = tap (hold = long press), CH+/CH- (or PAGE UP/DOWN) = scroll, D-pad into a screen edge = scroll.
 * One button (default MENU, changeable in Settings) switches the cursor on/off. Back always works normally.
 * Must be enabled once in Android Settings > Accessibility. Prefs: rc_on, rc_mode (toggle|auto), rc_key.
 */
public class RemoteCursorService extends AccessibilityService {
    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View cursor;
    private WindowManager.LayoutParams clp;
    private boolean shown;
    private Boolean override;      // set by the toggle button; reset when another app comes to the front
    private String lastPkg = "";
    private int cx = -1, cy = -1, sw, sh, size;
    private boolean longFired;
    private long lastEdgeSwipe;
    private final Runnable hideLater = new Runnable() { @Override public void run() { hideCursor(); } };

    static boolean isEnabled(Context c) {
        try {
            if (Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false;
            String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) return false;
            ComponentName cn = new ComponentName(c, RemoteCursorService.class);
            String a = cn.flattenToString().toLowerCase(), b = cn.flattenToShortString().toLowerCase();
            for (String x : s.split(":")) {
                String y = x.trim().toLowerCase();
                if (y.equals(a) || y.equals(b)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    static int toggleKey(Context c) { return Prefs.sp(c).getInt("rc_key", KeyEvent.KEYCODE_MENU); }

    private static final class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path p = new Path();
        CursorView(Context c) {
            super(c);
            fill.setColor(0xFFFFFFFF);
            line.setColor(0xFF000000);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(3f);
        }
        @Override protected void onDraw(Canvas c) {
            float s = getWidth();
            p.reset();
            p.moveTo(2, 2);
            p.lineTo(2, s * 0.80f);
            p.lineTo(s * 0.24f, s * 0.60f);
            p.lineTo(s * 0.40f, s * 0.95f);
            p.lineTo(s * 0.56f, s * 0.88f);
            p.lineTo(s * 0.40f, s * 0.54f);
            p.lineTo(s * 0.70f, s * 0.54f);
            p.close();
            c.drawPath(p, fill);
            c.drawPath(p, line);
        }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = e.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        // ignore the cursor's own window, keyboards and system dialogs popping over the app
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) {
            if (pkg.equals(getPackageName())) { lastPkg = pkg; hideCursor(); }
            return;
        }
        if (!pkg.equals(lastPkg)) {
            lastPkg = pkg;
            override = null;
            hideCursor();
        }
    }

    @Override public void onInterrupt() { hideCursor(); }

    @Override public boolean onUnbind(android.content.Intent i) {
        hideCursor();
        return super.onUnbind(i);
    }

    // ---------- keys ----------
    private boolean featureOn() {
        return Prefs.sp(this).getBoolean("rc_on", false) && !lastPkg.equals(getPackageName());
    }

    private boolean active() {
        if (!featureOn()) return false;
        if (override != null) return override;
        if ("auto".equals(Prefs.sp(this).getString("rc_mode", "toggle"))) return autoDetect();
        return false;
    }

    /** Auto mode: the cursor is on when the front app has no D-pad focus anywhere (typical for touch-only apps). */
    private boolean autoDetect() {
        AccessibilityNodeInfo root = null, f = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) return false;
            f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            return f == null;
        } catch (Exception e) {
            return false;
        } finally {
            if (f != null) f.recycle();
            if (root != null) root.recycle();
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        try {
            return handleKey(e);
        } catch (Throwable t) {
            return false; // never block the remote because of a bug here
        }
    }

    private boolean handleKey(KeyEvent e) {
        if (!featureOn()) return false;
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (k == toggleKey(this)) {
            if (down && e.getRepeatCount() == 0) {
                boolean now = !active();
                override = now;
                if (now) {
                    showCursor();
                    Toast.makeText(this, "Remote cursor ON  -  arrows move, OK clicks, CH+/CH- scroll", Toast.LENGTH_LONG).show();
                } else {
                    hideCursor();
                    Toast.makeText(this, "Remote cursor OFF", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }

        boolean dpad = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean pgUp = k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_PAGE_UP;
        boolean pgDn = k == KeyEvent.KEYCODE_CHANNEL_DOWN || k == KeyEvent.KEYCODE_PAGE_DOWN;
        if (!(dpad || ok || pgUp || pgDn)) return false;
        if (!active()) return false;

        readScreen();
        if (!shown) { // first press only wakes the cursor, so nothing gets clicked by accident
            if (down) showCursor();
            return true;
        }
        if (down) restartHideTimer();

        if (dpad) {
            if (!down) return true;
            int step = Math.max(8, sw / 90) * (1 + Math.min(e.getRepeatCount(), 24) / 4);
            int margin = Math.max(24, sw / 40);
            int dx = k == KeyEvent.KEYCODE_DPAD_LEFT ? -step : (k == KeyEvent.KEYCODE_DPAD_RIGHT ? step : 0);
            int dy = k == KeyEvent.KEYCODE_DPAD_UP ? -step : (k == KeyEvent.KEYCODE_DPAD_DOWN ? step : 0);
            boolean atEdge = (dx < 0 && cx <= margin) || (dx > 0 && cx >= sw - margin)
                    || (dy < 0 && cy <= margin) || (dy > 0 && cy >= sh - margin);
            if (atEdge) { // pushing into a screen edge scrolls the page
                long now = System.currentTimeMillis();
                if (now - lastEdgeSwipe > 450) {
                    lastEdgeSwipe = now;
                    swipe(dx < 0 ? -1 : (dx > 0 ? 1 : 0), dy < 0 ? -1 : (dy > 0 ? 1 : 0));
                }
                return true;
            }
            cx = clamp(cx + dx, 0, sw - 1);
            cy = clamp(cy + dy, 0, sh - 1);
            moveCursor();
            return true;
        }
        if (ok) {
            if (down) {
                if (e.getRepeatCount() == 0) longFired = false;
                else if (!longFired && e.getEventTime() - e.getDownTime() > 650) {
                    longFired = true;
                    gesture(cx, cy, cx, cy, 750);
                }
            } else if (!longFired) {
                gesture(cx, cy, cx, cy, 50);
            }
            return true;
        }
        if (down && e.getRepeatCount() == 0) swipe(0, pgUp ? -1 : 1);
        return true;
    }

    // ---------- gestures ----------
    /** dirX/dirY: direction the PAGE should move (-1 up/left, +1 down/right). The finger moves the opposite way. */
    private void swipe(int dirX, int dirY) {
        readScreen();
        int dist = Math.min(sw, sh) / 3;
        int x1 = cx, y1 = cy, x2 = cx - dirX * dist, y2 = cy - dirY * dist;
        if (dirY != 0) { y1 = sh / 2 + dirY * dist / 2; y2 = sh / 2 - dirY * dist / 2; }
        if (dirX != 0) { x1 = sw / 2 + dirX * dist / 2; x2 = sw / 2 - dirX * dist / 2; }
        gesture(x1, y1, x2, y2, 320);
    }

    private void gesture(int x1, int y1, int x2, int y2, long ms) {
        try {
            Path p = new Path();
            p.moveTo(x1, y1);
            if (x1 != x2 || y1 != y2) p.lineTo(x2, y2);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(new GestureDescription.StrokeDescription(p, 0, ms));
            dispatchGesture(b.build(), null, null);
        } catch (Throwable ignored) { }
    }

    // ---------- cursor window ----------
    private void readScreen() {
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        sw = m.widthPixels; sh = m.heightPixels;
        size = Math.max(40, Math.min(sw, sh) / 20);
        if (cx < 0 || cx >= sw) cx = sw / 2;
        if (cy < 0 || cy >= sh) cy = sh / 2;
    }

    private void showCursor() {
        if (wm == null) return;
        readScreen();
        if (!shown) {
            try {
                cursor = new CursorView(this);
                clp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                clp.gravity = Gravity.TOP | Gravity.START;
                clp.x = cx; clp.y = cy;
                wm.addView(cursor, clp);
                shown = true;
            } catch (Throwable t) { cursor = null; shown = false; }
        }
        restartHideTimer();
    }

    private void moveCursor() {
        if (!shown || cursor == null) return;
        try { clp.x = cx; clp.y = cy; wm.updateViewLayout(cursor, clp); } catch (Throwable ignored) { }
    }

    private void restartHideTimer() {
        h.removeCallbacks(hideLater);
        h.postDelayed(hideLater, 6000); // hide while idle (cleaner video); the next key press wakes it
    }

    private void hideCursor() {
        h.removeCallbacks(hideLater);
        if (shown && cursor != null && wm != null) { try { wm.removeView(cursor); } catch (Throwable ignored) { } }
        cursor = null;
        shown = false;
    }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
