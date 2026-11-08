package com.rummish.saleemtv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Lightweight live-TV player: M3U/M3U8 + Xtream Codes, categories, favorites, search, EPG now/next. */
public class IptvActivity extends Activity {
    static final String EXTRA_URL = "url", EXTRA_TITLE = "title", EXTRA_KIDS = "kids";

    private static final String FAV = "\u2605 Favorites";
    private static final int MAX_CH = 3000;
    private static final String B = "https://iptv-org.github.io/iptv/";
    // Community-maintained, publicly listed free-to-air/free streams (iptv-org). Availability varies.
    static final String[][] PRESETS = {
            {"Pakistan", B + "countries/pk.m3u"},
            {"News (International)", B + "categories/news.m3u"},
            {"Sports", B + "categories/sports.m3u"},
            {"Entertainment", B + "categories/entertainment.m3u"},
            {"Kids", B + "categories/kids.m3u"},
            {"Religious", B + "categories/religious.m3u"},
            {"Music", B + "categories/music.m3u"},
            {"International (General)", B + "categories/general.m3u"}};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SurfaceView surface;
    private View panel;
    private ListView catList, chList;
    private EditText search;
    private TextView title, status, info;

    private static final String UA = "RummishSaleemTV/1.0 (Android TV)";
    private static final int MAX_RETRIES = 3;
    private ExoPlayer player;
    private DefaultHttpDataSource.Factory httpFactory;
    private int retries;
    private boolean forceHls, okLongDone;
    private final Runnable retryRun = new Runnable() { public void run() {
        if (!destroyed && current != null) startPlayback(current);
    } };
    private boolean kids, destroyed;
    private int loadGen;
    private String srcName = "", srcUrl = "";
    private String catSel = "";        // "" = all, FAV = favorites, else group name
    private String query = "";
    private Channel current;
    private boolean everStarted;

    private final List<Channel> all = new ArrayList<>(), shown = new ArrayList<>();
    private final List<String> catKeys = new ArrayList<>();
    private final Map<String, Integer> catCounts = new HashMap<>();
    private final Set<String> favs = new HashSet<>();
    private volatile Map<String, EpgLoader.Epg> epg = new HashMap<>();
    private ChannelAdapter chAdapter;
    private CatAdapter catAdapter;

    private final Runnable hideInfo = new Runnable() { public void run() { info.setVisibility(View.GONE); } };
    private final Runnable hideStatus = new Runnable() { public void run() { status.setVisibility(View.GONE); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Util.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_iptv);
        surface = (SurfaceView) findViewById(R.id.surface);
        panel = findViewById(R.id.panel);
        catList = (ListView) findViewById(R.id.catList);
        chList = (ListView) findViewById(R.id.chList);
        search = (EditText) findViewById(R.id.search);
        title = (TextView) findViewById(R.id.title);
        status = (TextView) findViewById(R.id.status);
        info = (TextView) findViewById(R.id.info);
        kids = getIntent().getBooleanExtra(EXTRA_KIDS, false);

        panel.getLayoutParams().width = Math.round(Util.screenW(this) * 0.64f);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(40));
        title.setPadding(Util.px(30), Util.px(24), Util.px(30), Util.px(6));
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(32));
        search.setPadding(Util.px(30), Util.px(10), Util.px(30), Util.px(10));
        status.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(34));
        status.setPadding(Util.px(36), Util.px(20), Util.px(36), Util.px(20));
        info.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(34));
        info.setPadding(Util.px(60), Util.px(24), Util.px(60), Util.px(34));

        catAdapter = new CatAdapter();
        chAdapter = new ChannelAdapter();
        catList.setAdapter(catAdapter);
        chList.setAdapter(chAdapter);
        catList.setSelector(new ColorDrawable(0x44FFFFFF));
        chList.setSelector(new ColorDrawable(0x44FFFFFF));

        catList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                catSel = catKeys.get(pos);
                applyFilter();
                chList.requestFocus();
            }
        });
        chList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < shown.size()) play(shown.get(pos));
            }
        });
        chList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < shown.size()) toggleFav(shown.get(pos));
                return true;
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { query = e.toString().trim().toLowerCase(Locale.ROOT); applyFilter(); }
        });
        search.setOnEditorActionListener((v, action, ev) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
                chList.requestFocus();
                return true;
            }
            return false;
        });

        // choose source
        String u = getIntent().getStringExtra(EXTRA_URL);
        if (kids) {
            loadSource(getIntent().getStringExtra(EXTRA_TITLE), u);
        } else {
            String ln = Prefs.sp(this).getString("last_src_name", null), lu = Prefs.sp(this).getString("last_src_url", null);
            if (ln != null && lu != null) loadSource(ln, lu); else loadSource(PRESETS[0][0], PRESETS[0][1]);
        }
        chList.requestFocus();
    }

    // ---------- playlist loading ----------
    private void loadSource(final String name, final String url) {
        srcName = name == null ? "Playlist" : name;
        srcUrl = url;
        if (!kids) Prefs.sp(this).edit().putString("last_src_name", srcName).putString("last_src_url", url).apply();
        title.setText(srcName);
        showStatus("Loading playlist\u2026", 0);
        final int gen = ++loadGen;
        new Thread(new Runnable() {
            @Override public void run() {
                File f = new File(getFilesDir(), "pl_" + Integer.toHexString(url.hashCode()) + ".m3u");
                String err = null;
                try { Util.download(url, f, 12L * 1024 * 1024); } catch (Exception ex) { err = ex.getMessage(); }
                final boolean cached = err != null && f.exists();
                final String fErr = err;
                final List<Channel> list;
                final String[] hdr = new String[1];
                List<Channel> tmp = null;
                String perr = null;
                if (f.exists()) {
                    try { tmp = M3uParser.parse(f, MAX_CH, hdr); } catch (Exception ex) { perr = ex.getMessage(); }
                }
                list = tmp;
                final String fPerr = perr;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (destroyed || gen != loadGen) return;
                        if (list == null) {
                            showStatus("Could not load playlist (" + (fErr != null ? fErr : fPerr) + "). Check the internet connection or the playlist address.", 6000);
                            return;
                        }
                        all.clear(); all.addAll(list);
                        epg = new HashMap<>();
                        loadFavs();
                        buildCategories();
                        catSel = "";
                        applyFilter();
                        title.setText(srcName + "  \u00B7  " + all.size() + " channels");
                        if (all.isEmpty()) showStatus("The playlist contains no playable entries.", 5000);
                        else if (cached) showStatus("Offline - showing saved copy of this playlist.", 4000);
                        else hideStatusNow();
                        startEpg(hdr[0], gen);
                    }
                });
            }
        }, "playlist").start();
    }

    private void startEpg(String headerUrl, final int gen) {
        String u = Prefs.sp(this).getString("epg_url", "");
        if (u.isEmpty()) u = Prefs.sp(this).getString("xt_epg", "");
        if (u.isEmpty() && headerUrl != null) u = headerUrl;
        if (u.isEmpty() || kids) return;
        final String url = u;
        final Set<String> ids = new HashSet<>();
        for (Channel c : all) if (!c.tvgId.isEmpty()) ids.add(c.tvgId);
        if (ids.isEmpty()) return;
        new Thread(new Runnable() {
            @Override public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                try {
                    File f = new File(getCacheDir(), "epg.xml");
                    Util.download(url, f, 60L * 1024 * 1024);
                    final Map<String, EpgLoader.Epg> m = EpgLoader.load(f, ids);
                    f.delete();
                    ui.post(new Runnable() { public void run() {
                        if (destroyed || gen != loadGen) return;
                        epg = m; chAdapter.notifyDataSetChanged();
                        if (current != null) showInfo(current);
                    } });
                } catch (Throwable t) { /* EPG is optional; ignore */ }
            }
        }, "epg").start();
    }

    private void loadFavs() {
        favs.clear();
        favs.addAll(Prefs.list(this, "fav_ch"));
    }

    private void buildCategories() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Channel c : all) { Integer n = counts.get(c.group); counts.put(c.group, n == null ? 1 : n + 1); }
        List<String> groups = new ArrayList<>(counts.keySet());
        Collections.sort(groups, new Comparator<String>() {
            public int compare(String a, String b) { return a.compareToIgnoreCase(b); }
        });
        catKeys.clear(); catCounts.clear();
        catKeys.add(FAV); catKeys.add("");
        catKeys.addAll(groups);
        catCounts.putAll(counts);
        catAdapter.notifyDataSetChanged();
    }

    private void applyFilter() {
        shown.clear();
        for (Channel c : all) {
            boolean ok = catSel.equals(FAV) ? favs.contains(c.url) : (catSel.isEmpty() || c.group.equals(catSel));
            if (ok && (query.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(query))) shown.add(c);
        }
        chAdapter.notifyDataSetChanged();
        catAdapter.notifyDataSetChanged();
    }

    // ---------- playback ----------
    private ExoPlayer ensurePlayer() {
        if (player == null) {
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setUserAgent(UA)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(10000).setReadTimeoutMs(12000);
            // Small buffers: keeps RAM low on a 1GB box while still absorbing short network hiccups.
            DefaultLoadControl lc = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(10000, 25000, 1500, 3000)
                    .setTargetBufferBytes(8 * 1024 * 1024)
                    .build();
            httpFactory = http;
            // Decoder fallback: if the box's hardware decoder rejects a stream, try another decoder instead of failing.
            DefaultRenderersFactory rf = new DefaultRenderersFactory(this).setEnableDecoderFallback(true);
            player = new ExoPlayer.Builder(this, rf)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                    .setLoadControl(lc).build();
            player.setWakeMode(C.WAKE_MODE_NETWORK); // keep Wi-Fi awake while streaming
            player.setVideoSurfaceView(surface);
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int s) {
                    if (s == Player.STATE_BUFFERING) showStatus("Buffering\u2026", 0);
                    else if (s == Player.STATE_READY) { retries = 0; hideStatusNow(); }
                }
                @Override public void onPlayerError(PlaybackException e) {
                    ExoPlayer p = player;
                    if (p != null && e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        p.seekToDefaultPosition(); p.prepare();
                    } else if (!forceHls && current != null
                            && (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                            || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)) {
                        // URL has no .m3u8 extension but may still be HLS: retry once, forcing HLS.
                        forceHls = true;
                        startPlayback(current);
                    } else if (current != null && retries < MAX_RETRIES) {
                        retries++;
                        showStatus("Connection lost - retrying (" + retries + "/" + MAX_RETRIES + ")\u2026", 0);
                        ui.removeCallbacks(retryRun);
                        ui.postDelayed(retryRun, 2000L * retries);
                    } else {
                        showStatus("This stream is offline or cannot be played on this device (" + e.getErrorCodeName()
                                + "). Press CH+/CH- or OK to pick another channel.", 8000);
                    }
                }
            });
        }
        return player;
    }

    private void play(Channel ch) {
        current = ch;
        everStarted = true;
        retries = 0;
        forceHls = false;
        ui.removeCallbacks(retryRun);
        startPlayback(ch);
        hidePanel();
        showInfo(ch);
    }

    private void startPlayback(Channel ch) {
        showStatus("Loading " + ch.name + "\u2026", 0);
        ExoPlayer p = ensurePlayer();
        java.util.Map<String, String> hdr = new HashMap<>();
        if (!ch.referer.isEmpty()) hdr.put("Referer", ch.referer);
        httpFactory.setDefaultRequestProperties(hdr);
        httpFactory.setUserAgent(ch.ua.isEmpty() ? UA : ch.ua);
        MediaItem.Builder mi = new MediaItem.Builder().setUri(ch.url);
        if (forceHls) mi.setMimeType(MimeTypes.APPLICATION_M3U8);
        p.setMediaItem(mi.build());
        p.prepare();
        p.setPlayWhenReady(true);
    }

    private void zap(int dir) {
        if (shown.isEmpty()) return;
        int i = shown.indexOf(current);
        i = i < 0 ? 0 : (i + dir + shown.size()) % shown.size();
        play(shown.get(i));
    }

    private void toggleFav(Channel c) {
        boolean added = Prefs.toggle(this, "fav_ch", c.url);
        loadFavs();
        applyFilter();
        Toast.makeText(this, added ? "Added to favorites" : "Removed from favorites", Toast.LENGTH_SHORT).show();
    }

    // ---------- overlays ----------
    private void showPanel() { panel.setVisibility(View.VISIBLE); chList.requestFocus(); }
    private void hidePanel() { panel.setVisibility(View.GONE); }

    private void showStatus(String s, long hideAfter) {
        status.setText(s);
        status.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideStatus);
        if (hideAfter > 0) ui.postDelayed(hideStatus, hideAfter);
    }
    private void hideStatusNow() { ui.removeCallbacks(hideStatus); status.setVisibility(View.GONE); }

    private void showInfo(Channel c) {
        StringBuilder sb = new StringBuilder(c.name);
        EpgLoader.Epg e = epg.get(c.tvgId);
        if (e != null) {
            if (e.now != null && e.nowStop > System.currentTimeMillis()) sb.append("\nNow: ").append(e.now);
            if (e.next != null) sb.append("\nNext: ").append(e.next);
        }
        info.setText(sb.toString());
        info.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideInfo);
        ui.postDelayed(hideInfo, 5000);
    }

    // ---------- remote keys ----------
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean on = panel.getVisibility() == View.VISIBLE;
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
        // Hold OK on a channel in the list = favorite (ListView has no long-press for the remote's OK key)
        if (ok && on && chList.hasFocus()) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() >= 1 && !okLongDone) {
                okLongDone = true;
                int p = chList.getSelectedItemPosition();
                if (p >= 0 && p < shown.size()) toggleFav(shown.get(p));
                return true;
            }
            if (e.getAction() == KeyEvent.ACTION_UP && okLongDone) { okLongDone = false; return true; } // swallow the click
            if (okLongDone) return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        if (k == KeyEvent.KEYCODE_BACK && on && current != null) { hidePanel(); return true; }
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_INFO || k == KeyEvent.KEYCODE_PROG_YELLOW) {
            if (kids) { if (on && current != null) hidePanel(); else showPanel(); }
            else showSourceMenu();
            return true;
        }
        if (k == KeyEvent.KEYCODE_PROG_RED) {
            Channel t = current;
            if (on) { int p = chList.getSelectedItemPosition(); t = (p >= 0 && p < shown.size()) ? shown.get(p) : null; }
            if (t != null) toggleFav(t);
            return true;
        }
        if (k == KeyEvent.KEYCODE_CHANNEL_UP) { zap(-1); return true; }
        if (k == KeyEvent.KEYCODE_CHANNEL_DOWN) { zap(1); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && player != null) { player.setPlayWhenReady(!player.getPlayWhenReady()); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY && player != null) { player.setPlayWhenReady(true); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_PAUSE && player != null) { player.setPlayWhenReady(false); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_STOP) { finish(); return true; }
        if (!on) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_UP: zap(-1); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: zap(1); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT: showPanel(); return true;
                default: break;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------- source menu (Menu button) ----------
    private void showSourceMenu() {
        final List<String> labels = new ArrayList<>();
        for (String[] p : PRESETS) labels.add(p[0]);
        final int n = PRESETS.length;
        final String custom = Prefs.sp(this).getString("custom_url", "");
        final boolean hasXt = !Prefs.sp(this).getString("xt_server", "").isEmpty();
        labels.add("My playlist (M3U / M3U8 URL or file path)\u2026");
        labels.add(hasXt ? "Xtream Codes login (edit)\u2026" : "Xtream Codes login\u2026");
        labels.add("Set EPG / XMLTV guide URL\u2026");
        labels.add("Reload current playlist");
        Util.dialog(this).setTitle("Live TV sources")
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    if (w < n) loadSource(PRESETS[w][0], PRESETS[w][1]);
                    else if (w == n) Util.prompt(this, "M3U / M3U8 playlist", "http://\u2026/playlist.m3u", custom, s -> {
                        if (s.isEmpty()) return;
                        Prefs.sp(this).edit().putString("custom_url", s).apply();
                        loadSource("My playlist", s);
                    });
                    else if (w == n + 1) xtreamDialog();
                    else if (w == n + 2) Util.prompt(this, "EPG (XMLTV) URL", "http://\u2026/epg.xml or .xml.gz",
                            Prefs.sp(this).getString("epg_url", ""), s -> {
                                Prefs.sp(this).edit().putString("epg_url", s).apply();
                                loadSource(srcName, srcUrl);
                            });
                    else if (srcUrl != null) loadSource(srcName, srcUrl);
                }).setNegativeButton("Close", null).show();
    }

    private void xtreamDialog() {
        final EditText s = field("Server, e.g. http://host:8080", Prefs.sp(this).getString("xt_server", ""), false);
        final EditText u = field("Username", Prefs.sp(this).getString("xt_user", ""), false);
        final EditText p = field("Password", Prefs.sp(this).getString("xt_pass", ""), true);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Util.px(30), Util.px(10), Util.px(30), 0);
        l.addView(s); l.addView(u); l.addView(p);
        Util.dialog(this).setTitle("Xtream Codes (your own subscription)").setView(l)
                .setPositiveButton("Connect", (d, w) -> {
                    String sv = s.getText().toString().trim();
                    while (sv.endsWith("/")) sv = sv.substring(0, sv.length() - 1);
                    String us = u.getText().toString().trim(), pw = p.getText().toString().trim();
                    if (!sv.startsWith("http") || us.isEmpty()) { Toast.makeText(this, "Enter server (http://\u2026) and username", Toast.LENGTH_LONG).show(); return; }
                    try {
                        String q = "username=" + URLEncoder.encode(us, "UTF-8") + "&password=" + URLEncoder.encode(pw, "UTF-8");
                        Prefs.sp(this).edit().putString("xt_server", sv).putString("xt_user", us).putString("xt_pass", pw)
                                .putString("xt_epg", sv + "/xmltv.php?" + q).apply();
                        loadSource("Xtream", sv + "/get.php?" + q + "&type=m3u_plus&output=m3u8");
                    } catch (Exception ex) { Toast.makeText(this, "Invalid credentials", Toast.LENGTH_LONG).show(); }
                }).setNegativeButton("Cancel", null).show();
    }

    private EditText field(String hint, String val, boolean pass) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setSingleLine(true); e.setText(val);
        e.setInputType(pass ? (android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
                : (android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI));
        return e;
    }

    // ---------- lifecycle ----------
    @Override protected void onStart() {
        super.onStart();
        if (everStarted && current != null && player == null) startPlayback(current);
    }

    @Override protected void onStop() {
        super.onStop();
        ui.removeCallbacks(retryRun);
        if (player != null) { player.release(); player = null; } // free decoder + RAM when not visible
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        loadGen++;
        ui.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
    }

    // ---------- adapters ----------
    private final class CatAdapter extends BaseAdapter {
        public int getCount() { return catKeys.size(); }
        public Object getItem(int i) { return catKeys.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View v, ViewGroup parent) {
            TextView t = (TextView) v;
            if (t == null) {
                t = new TextView(IptvActivity.this);
                t.setSingleLine(true);
                t.setPadding(Util.px(30), Util.px(18), Util.px(10), Util.px(18));
                t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(30));
            }
            String k = catKeys.get(i);
            String txt;
            if (k.equals(FAV)) txt = FAV + " (" + favCount() + ")";
            else if (k.isEmpty()) txt = "All channels (" + all.size() + ")";
            else txt = k + " (" + catCounts.get(k) + ")";
            t.setText(txt);
            t.setTextColor(k.equals(catSel) ? Color.WHITE : 0xFF9A9AA0);
            t.setTypeface(null, k.equals(catSel) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            return t;
        }
        private int favCount() { int n = 0; for (Channel c : all) if (favs.contains(c.url)) n++; return n; }
    }

    private final class ChannelAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View v, ViewGroup parent) {
            LinearLayout row;
            TextView[] tv;
            if (v == null) {
                row = new LinearLayout(IptvActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(Util.px(24), Util.px(12), Util.px(16), Util.px(12));
                TextView n = new TextView(IptvActivity.this), s = new TextView(IptvActivity.this);
                n.setSingleLine(true); s.setSingleLine(true);
                n.setTextColor(Color.WHITE); s.setTextColor(0xFF9A9AA0);
                n.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(30));
                s.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(22));
                row.addView(n); row.addView(s);
                tv = new TextView[]{n, s};
                row.setTag(tv);
            } else { row = (LinearLayout) v; tv = (TextView[]) row.getTag(); }
            Channel c = shown.get(i);
            tv[0].setText((favs.contains(c.url) ? "\u2605 " : "") + c.name);
            EpgLoader.Epg e = epg.get(c.tvgId);
            String sub = (e != null && e.now != null && e.nowStop > System.currentTimeMillis()) ? "Now: " + e.now : c.group;
            tv[1].setText(sub);
            return row;
        }
    }
}
