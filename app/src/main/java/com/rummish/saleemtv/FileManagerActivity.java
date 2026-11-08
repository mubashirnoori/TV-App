package com.rummish.saleemtv;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Simple remote-friendly file manager: internal storage + USB/SD, open files, play .m3u in Live TV, install APKs, delete. */
public class FileManagerActivity extends Activity {
    private static final List<String> VIDEO = Arrays.asList("mp4", "mkv", "avi", "mov", "ts", "m4v", "3gp", "webm", "flv", "mpg", "mpeg", "wmv");
    private static final List<String> AUDIO = Arrays.asList("mp3", "aac", "m4a", "wav", "flac", "ogg", "wma");
    private static final List<String> IMAGE = Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp");

    private final List<File> roots = new ArrayList<>(), items = new ArrayList<>();
    private File cur; // null = list of storage devices
    private ListView list;
    private TextView pathTv;
    private Adapter adapter;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Util.init(this);
        Util.allowFileUris();

        FrameLayout root = new FrameLayout(this);
        WallpaperView wall = new WallpaperView(this);
        wall.setTheme(WallpaperView.THEMES[4]);
        root.addView(wall, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Util.px(90), Util.px(44), Util.px(90), Util.px(30));
        TextView t = new TextView(this);
        t.setText("Files");
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(70));
        col.addView(t);
        pathTv = new TextView(this);
        pathTv.setTextColor(0xCCFFFFFF);
        pathTv.setSingleLine(true);
        pathTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(26));
        pathTv.setPadding(0, 0, 0, Util.px(14));
        col.addView(pathTv);
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(Util.round(0x44FFFFFF, Util.px(16)));
        adapter = new Adapter();
        list.setAdapter(adapter);
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView hint = new TextView(this);
        hint.setText("OK open     MENU options (open / delete)     BACK up one folder");
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(24));
        hint.setPadding(0, Util.px(12), 0, 0);
        col.addView(hint);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= items.size()) return;
                File f = items.get(pos);
                if (f.isDirectory()) show(f); else open(f);
            }
        });

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
        }
        loadRoots();
        show(null);
        list.requestFocus();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        loadRoots();
        show(cur);
        if (res.length > 0 && res[0] != PackageManager.PERMISSION_GRANTED)
            Toast.makeText(this, "Storage permission denied - allow it in Settings > Apps > Rummish Saleem TV > Permissions.", Toast.LENGTH_LONG).show();
    }

    private void loadRoots() {
        roots.clear();
        File ext = Environment.getExternalStorageDirectory();
        if (ext != null && ext.exists()) roots.add(ext);
        File[] k = new File("/storage").listFiles();
        if (k != null) for (File f : k) {
            String n = f.getName();
            if (n.equals("self") || n.equals("emulated") || n.equals("sdcard0")) continue;
            if (f.canRead() && !roots.contains(f)) roots.add(f);
        }
    }

    private void show(File dir) {
        cur = dir;
        items.clear();
        if (dir == null) {
            items.addAll(roots);
            pathTv.setText("Storage");
        } else {
            File[] fs = dir.listFiles();
            if (fs != null) for (File f : fs) if (!f.getName().startsWith(".")) items.add(f);
            Collections.sort(items, new Comparator<File>() {
                public int compare(File a, File b) {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            pathTv.setText(dir.getPath());
            if (fs == null) Toast.makeText(this, "Cannot read this folder (permission?)", Toast.LENGTH_SHORT).show();
            else if (items.isEmpty()) Toast.makeText(this, "Empty folder", Toast.LENGTH_SHORT).show();
        }
        adapter.notifyDataSetChanged();
        list.setSelection(0);
    }

    private static String ext(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private void open(File f) {
        String e = ext(f);
        if (e.equals("m3u") || e.equals("m3u8")) { // playlist: play it in Live TV
            startActivity(new Intent(this, IptvActivity.class)
                    .putExtra(IptvActivity.EXTRA_URL, f.getAbsolutePath()).putExtra(IptvActivity.EXTRA_TITLE, f.getName()));
            return;
        }
        String mime = e.equals("apk") ? "application/vnd.android.package-archive"
                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(e);
        if (mime == null) mime = "*/*";
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(f), mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ex) {
            Toast.makeText(this, "No app on this box can open this file. Install VLC for videos.", Toast.LENGTH_LONG).show();
        }
    }

    private void options() {
        int p = list.getSelectedItemPosition();
        if (p < 0 || p >= items.size()) return;
        final File f = items.get(p);
        if (cur == null) return; // storage devices cannot be deleted
        Util.dialog(this).setTitle(f.getName())
                .setItems(new String[]{"Open", "Delete"}, (d, w) -> {
                    if (w == 0) { if (f.isDirectory()) show(f); else open(f); }
                    else confirmDelete(f);
                }).setNegativeButton("Close", null).show();
    }

    private void confirmDelete(final File f) {
        Util.dialog(this).setTitle("Delete?")
                .setMessage((f.isDirectory() ? "This folder and everything in it will be deleted:\n" : "This file will be deleted:\n") + f.getName())
                .setPositiveButton("Delete", (d, w) -> {
                    boolean ok = deleteRec(f);
                    Toast.makeText(this, ok ? "Deleted" : "Could not delete (read-only or no permission)", Toast.LENGTH_SHORT).show();
                    show(cur);
                }).setNegativeButton("Cancel", null).show();
    }

    private static boolean deleteRec(File f) {
        File[] kids = f.isDirectory() ? f.listFiles() : null;
        boolean ok = true;
        if (kids != null) for (File k : kids) ok &= deleteRec(k);
        return f.delete() && ok;
    }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_INFO || k == KeyEvent.KEYCODE_PROG_YELLOW) { options(); return true; }
        return super.onKeyDown(k, e);
    }

    @Override public void onBackPressed() {
        if (cur == null) { finish(); return; }
        File p = cur.getParentFile();
        if (roots.contains(cur) || p == null || !p.canRead()) show(null); else show(p);
    }

    private String iconFor(File f) {
        if (roots.contains(f)) return "💾";
        if (f.isDirectory()) return "📁";
        String e = ext(f);
        if (VIDEO.contains(e)) return "🎬";
        if (AUDIO.contains(e)) return "🎵";
        if (IMAGE.contains(e)) return "🖼";
        if (e.equals("apk")) return "📦";
        if (e.equals("m3u") || e.equals("m3u8")) return "📺";
        return "📄";
    }

    private final class Adapter extends BaseAdapter {
        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View v, ViewGroup parent) {
            LinearLayout row;
            TextView[] tv;
            if (v == null) {
                row = new LinearLayout(FileManagerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Util.px(24), Util.px(12), Util.px(24), Util.px(12));
                TextView ic = new TextView(FileManagerActivity.this);
                ic.setGravity(Gravity.CENTER);
                ic.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(46));
                row.addView(ic, new LinearLayout.LayoutParams(Util.px(84), -2));
                LinearLayout col = new LinearLayout(FileManagerActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView n = new TextView(FileManagerActivity.this), s = new TextView(FileManagerActivity.this);
                n.setSingleLine(true); s.setSingleLine(true);
                n.setTextColor(Color.WHITE); s.setTextColor(0xFFA5A8B8);
                n.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(32));
                s.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(22));
                col.addView(n); col.addView(s);
                row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
                tv = new TextView[]{ic, n, s};
                row.setTag(tv);
            } else { row = (LinearLayout) v; tv = (TextView[]) row.getTag(); }
            File f = items.get(i);
            tv[0].setText(iconFor(f));
            if (roots.contains(f)) {
                tv[1].setText(f.equals(Environment.getExternalStorageDirectory()) ? "Internal storage" : "USB / SD card  (" + f.getName() + ")");
                tv[2].setText(f.getPath());
            } else {
                tv[1].setText(f.getName());
                tv[2].setText(f.isDirectory() ? "Folder"
                        : Formatter.formatFileSize(FileManagerActivity.this, f.length()) + "   ·   "
                        + DateFormat.format("d MMM yyyy", new Date(f.lastModified())));
            }
            return row;
        }
    }
}
