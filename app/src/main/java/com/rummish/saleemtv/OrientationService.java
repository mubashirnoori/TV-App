package com.rummish.saleemtv;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * "Force landscape": an invisible 1x1 overlay window that asks the window manager for landscape.
 * While it exists, apps that insist on portrait are shown landscape (TV-friendly) instead of sideways.
 * Needs the "Display over other apps" permission (SYSTEM_ALERT_WINDOW). Mode is stored in Prefs "force_orient": 0 off, 1 landscape, 2 reverse landscape.
 */
public class OrientationService extends Service {
    private WindowManager wm;
    private View v;

    static int mode(Context c) { return Prefs.sp(c).getInt("force_orient", 0); }

    static boolean canDraw(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    /** Starts / updates / stops the overlay to match the saved mode. Safe to call any time. */
    static void apply(Context c) {
        Context app = c.getApplicationContext();
        try {
            if (mode(app) == 0 || !canDraw(app)) app.stopService(new Intent(app, OrientationService.class));
            else app.startService(new Intent(app, OrientationService.class));
        } catch (Exception ignored) { }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        int m = mode(this);
        if (m == 0 || !canDraw(this)) { stopSelf(); return START_NOT_STICKY; }
        try {
            if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.screenOrientation = m == 2 ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            if (v == null) {
                v = new View(this);
                wm.addView(v, lp);
            } else {
                wm.updateViewLayout(v, lp);
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Force landscape could not start (permission \"Display over other apps\" missing?)", Toast.LENGTH_LONG).show();
            removeOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void removeOverlay() {
        try { if (wm != null && v != null) wm.removeView(v); } catch (Throwable ignored) { }
        v = null;
    }

    @Override public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }
}
