package com.rummish.saleemtv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Offline learning: ABC, Urdu alphabet, and simple math. No network, no content licensing issues. */
public class LearnActivity extends Activity implements TextToSpeech.OnInitListener {
    static final String MODE = "mode";

    private static final String[] WORDS = {"Apple", "Ball", "Cat", "Dog", "Elephant", "Fish", "Grapes", "Hat", "Ice cream",
            "Juice", "Key", "Lion", "Moon", "Nose", "Orange", "Pizza", "Queen", "Rabbit", "Sun", "Tree", "Umbrella",
            "Van", "Watch", "Xylophone", "Yacht", "Zebra"};
    private static final String[] EMOJI = {"\uD83C\uDF4E", "\u26BD", "\uD83D\uDC31", "\uD83D\uDC36", "\uD83D\uDC18", "\uD83D\uDC1F",
            "\uD83C\uDF47", "\uD83C\uDFA9", "\uD83C\uDF66", "\uD83C\uDF79", "\uD83D\uDD11", "\uD83E\uDD81", "\uD83C\uDF19",
            "\uD83D\uDC43", "\uD83C\uDF4A", "\uD83C\uDF55", "\uD83D\uDC78", "\uD83D\uDC30", "\u2600", "\uD83C\uDF33", "\u2602",
            "\uD83D\uDE90", "\u231A", "\uD83C\uDFBC", "\u26F5", "\uD83E\uDD93"};
    private static final String[] URDU = {"\u0627", "\u0628", "\u067E", "\u062A", "\u0679", "\u062B", "\u062C", "\u0686", "\u062D",
            "\u062E", "\u062F", "\u0688", "\u0630", "\u0631", "\u0691", "\u0632", "\u0698", "\u0633", "\u0634", "\u0635",
            "\u0636", "\u0637", "\u0638", "\u0639", "\u063A", "\u0641", "\u0642", "\u06A9", "\u06AF", "\u0644", "\u0645",
            "\u0646", "\u0648", "\u06C1", "\u06BE", "\u0621", "\u06CC", "\u06D2"};
    private static final String[] URDU_NAMES = {"Alif", "Bay", "Pay", "Tay", "Ttay", "Say", "Jeem", "Chay", "Bari Hay", "Khay",
            "Daal", "Ddaal", "Zaal", "Ray", "Rray", "Zay", "Zhay", "Seen", "Sheen", "Suaad", "Zuaad", "Toay", "Zoay", "Ain",
            "Ghain", "Fay", "Qaaf", "Kaaf", "Gaaf", "Laam", "Meem", "Noon", "Wao", "Choti Hay", "Do-chashmi Hay", "Hamza",
            "Choti Yay", "Bari Yay"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private String mode;
    private int idx = 0;
    private TextToSpeech tts;
    private boolean ttsReady;
    private TextView big, emoji, caption, hint;
    // math
    private TextView question, feedback, scoreTv;
    private final TextView[] answers = new TextView[4];
    private int correct, score;
    private boolean locked;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Util.init(this);
        mode = getIntent().getStringExtra(MODE);
        if (mode == null) mode = "abc";
        tts = new TextToSpeech(this, this);
        if (mode.equals("math")) buildMath(); else buildLetters();
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(Locale.US);
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
        }
    }

    private void say(String s) { if (ttsReady) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "x"); }

    private TextView tv(float sizePx, int color) {
        TextView t = new TextView(this);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        return t;
    }

    // ---------- letters ----------
    private void buildLetters() {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                mode.equals("urdu") ? new int[]{0xFF0B3B4F, 0xFF05141C} : new int[]{0xFF4A1230, 0xFF140512}));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        big = tv(Util.px(380), Color.WHITE);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        emoji = tv(Util.px(150), Color.WHITE);
        caption = tv(Util.px(80), 0xFFFFD60A);
        hint = tv(Util.px(30), 0x99FFFFFF);
        hint.setText("\u25C0  \u25B6  browse      OK  hear it      BACK  exit");
        hint.setPadding(0, Util.px(40), 0, 0);
        c.addView(big); c.addView(emoji); c.addView(caption); c.addView(hint);
        f.addView(c, new FrameLayout.LayoutParams(-1, -1));
        setContentView(f);
        showLetter();
    }

    private int size() { return mode.equals("urdu") ? URDU.length : 26; }

    private void showLetter() {
        if (mode.equals("urdu")) {
            big.setText(URDU[idx]);
            emoji.setText("");
            caption.setText(URDU_NAMES[idx] + "   (" + (idx + 1) + "/" + URDU.length + ")");
        } else {
            char u = (char) ('A' + idx);
            big.setText(u + " " + Character.toLowerCase(u));
            emoji.setText(EMOJI[idx]);
            caption.setText(u + " is for " + WORDS[idx]);
        }
    }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (!mode.equals("math")) {
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { idx = (idx + 1) % size(); showLetter(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) { idx = (idx + size() - 1) % size(); showLetter(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                say(mode.equals("urdu") ? URDU_NAMES[idx] : (char) ('A' + idx) + ". " + WORDS[idx]);
                return true;
            }
        }
        return super.onKeyDown(k, e);
    }

    // ---------- math ----------
    private void buildMath() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF3B2F00, 0xFF120E00}));
        scoreTv = tv(Util.px(44), 0xFFFFD60A);
        question = tv(Util.px(190), Color.WHITE);
        question.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        feedback = tv(Util.px(50), Color.WHITE);
        feedback.setMinHeight(Util.px(80));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < 4; i++) {
            final TextView a = tv(Util.px(100), Color.WHITE);
            final GradientDrawable bg = Util.round(0xFF3A3A3C, Util.px(30));
            a.setBackground(bg);
            a.setFocusable(true);
            a.setClickable(true);
            a.setOnFocusChangeListener((v, fo) -> {
                bg.setStroke(fo ? Util.px(6) : 0, Color.WHITE);
                v.animate().scaleX(fo ? 1.1f : 1f).scaleY(fo ? 1.1f : 1f).setDuration(120).start();
            });
            final int pos = i;
            a.setOnClickListener(v -> choose(pos));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Util.px(280), Util.px(200));
            lp.setMargins(Util.px(24), Util.px(20), Util.px(24), Util.px(20));
            row.addView(a, lp);
            answers[i] = a;
        }
        c.addView(scoreTv); c.addView(question); c.addView(feedback); c.addView(row);
        setContentView(c);
        nextQuestion();
    }

    private void nextQuestion() {
        int level = score / 8;
        int max = level == 0 ? 10 : (level == 1 ? 20 : 50);
        int a = rnd.nextInt(max + 1), b = rnd.nextInt(max + 1);
        int op = rnd.nextInt(level >= 1 ? 3 : 2);
        String sym;
        if (op == 0) { sym = "+"; correct = a + b; }
        else if (op == 1) { sym = "\u2212"; if (b > a) { int t = a; a = b; b = t; } correct = a - b; }
        else { a = 2 + rnd.nextInt(9); b = 1 + rnd.nextInt(10); sym = "\u00D7"; correct = a * b; }
        question.setText(a + " " + sym + " " + b + " = ?");
        List<Integer> opts = new ArrayList<>();
        opts.add(correct);
        while (opts.size() < 4) {
            int d = correct + (rnd.nextInt(11) - 5);
            if (d >= 0 && !opts.contains(d)) opts.add(d);
        }
        Collections.shuffle(opts, rnd);
        for (int i = 0; i < 4; i++) { answers[i].setText(String.valueOf(opts.get(i))); answers[i].setTag(opts.get(i)); }
        locked = false;
        feedback.setText("");
        scoreTv.setText("\u2B50 " + score);
        answers[0].requestFocus();
    }

    private void choose(int pos) {
        if (locked) return; // ignore extra presses while "Great job" is showing
        int v = (Integer) answers[pos].getTag();
        if (v == correct) {
            score++;
            locked = true;
            feedback.setTextColor(0xFF30D158);
            feedback.setText("Great job!");
            say("Great job!");
            ui.postDelayed(this::nextQuestion, 900);
        } else {
            feedback.setTextColor(0xFFFF453A);
            feedback.setText("Try again");
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
