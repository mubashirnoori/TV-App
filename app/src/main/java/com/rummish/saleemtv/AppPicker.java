package com.rummish.saleemtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Remote-friendly list of every app installed on the TV (icon + name). Used for "On power-up open". */
final class AppPicker {
    interface OnPick { void picked(String pkg, String cls, String label); }

    static final class Item {
        String pkg, cls, label;
        Drawable icon;
    }

    /** Loads all launchable apps (leanback + normal launcher), without this launcher itself. Call off the main thread. */
    static List<Item> load(Context c) {
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        List<Item> out = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (ResolveInfo r : all) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
            Item it = new Item();
            it.pkg = pkg;
            it.cls = r.activityInfo.name;
            CharSequence l = r.loadLabel(pm);
            it.label = l == null ? pkg : l.toString();
            try { it.icon = r.loadIcon(pm); } catch (Throwable ignored) { }
            out.add(it);
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return a.label.toLowerCase(Locale.ROOT).compareTo(b.label.toLowerCase(Locale.ROOT)); }
        });
        return out;
    }

    static void show(final Activity a, final String title, final OnPick cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Item> items = load(a);
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (a.isDestroyed() || a.isFinishing()) return;
                        display(a, title, items, cb);
                    }
                });
            }
        }, "apppicker").start();
    }

    private static void display(final Activity a, String title, final List<Item> items, final OnPick cb) {
        if (items.isEmpty()) {
            Util.dialog(a).setTitle(title).setMessage("No other apps are installed on this TV yet.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        final ListView lv = new ListView(a);
        lv.setDividerHeight(0);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup p) {
                LinearLayout row;
                ImageView iv;
                TextView tv;
                if (v == null) {
                    row = new LinearLayout(a);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Util.px(24), Util.px(12), Util.px(24), Util.px(12));
                    iv = new ImageView(a);
                    iv.setId(1);
                    row.addView(iv, new LinearLayout.LayoutParams(Util.px(72), Util.px(72)));
                    tv = new TextView(a);
                    tv.setId(2);
                    tv.setTextColor(Color.WHITE);
                    tv.setSingleLine(true);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(34));
                    tv.setPadding(Util.px(24), 0, 0, 0);
                    row.addView(tv);
                } else {
                    row = (LinearLayout) v;
                    iv = (ImageView) row.findViewById(1);
                    tv = (TextView) row.findViewById(2);
                }
                Item it = items.get(i);
                iv.setImageDrawable(it.icon);
                tv.setText(it.label);
                return row;
            }
        });
        final AlertDialog d = Util.dialog(a).setTitle(title).setView(lv)
                .setNegativeButton("Cancel", null).create();
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                Item it = items.get(pos);
                d.dismiss();
                cb.picked(it.pkg, it.cls, it.label);
            }
        });
        d.show();
        lv.requestFocus();
    }
}
