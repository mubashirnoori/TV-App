package com.rummish.saleemtv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.TextView;

/** Shows the Android key code of each remote button, to verify/diagnose the PTCL remote. */
public class KeyTestActivity extends Activity {
    private TextView tv;
    private int backs = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Util.init(this);
        tv = new TextView(this);
        tv.setBackgroundColor(Color.BLACK);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(48));
        tv.setText("Remote key tester\n\nPress any remote button.\nPress BACK 3 times to exit.");
        setContentView(tv);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            int k = e.getKeyCode();
            tv.setText(KeyEvent.keyCodeToString(k) + "\n(" + k + ")\n\nPress BACK 3 times to exit.");
            if (k == KeyEvent.KEYCODE_BACK) { if (++backs >= 3) finish(); } else backs = 0;
        }
        return true;
    }
}
