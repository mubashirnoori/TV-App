package com.rummish.saleemtv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
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

/**
 * Live TV. Starts playing automatically (last watched channel, else the first one).
 * UP/DOWN or CH+/CH- change channel, 0-9 types a channel number, OK/LEFT/RIGHT opens the channel browser.
 */
public class IptvActivity extends Activity {
    static final String EXTRA_URL = "url", EXTRA_TITLE = "title", EXTRA_KIDS = "kids", EXTRA_FILTER = "filter";

    private static final String FAV = "★ Favorites";
    private static final int MAX_CH = 3000;
    private static final String B = "https://iptv-org.github.io/iptv/";
    // Community-maintained, publicly listed free-to-air/free streams (iptv-org). Availability varies.
    static final String[][] PRESETS = {
            {"Pakistan", B + "countries/pk.m3u"},
            {"Urdu channels", B + "languages/urd.m3u"},
            {"Free-TV (open source)", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"},
            {"News (International)", B + "categories/news.m3u"},
            {"Sports", B + "categories/sports.m3u"},
            {"Entertainment", B + "categories/entertainment.m3u"},
            {"Kids", B + "categories/kids.m3u"},
            {"Religious", B + "categories/religious.m3u"},
            {"Music", B + "categories/music.m3u"},
            {"International (General)", B + "categories/general.m3u"}};

    private static final int[] BADGE = {0xFF3B82F6, 0xFF8B5CF6, 0xFFEC4899, 0xFFF97316, 0xFF10B981, 0xFF06B6D4, 0xFFEF4444, 0xFF6366F1};
    private static final String UA = "RummishSaleemTV/1.0 (Android TV)";
    private static final int MAX_RETRIES = 2, MAX_SKIP = 8;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SurfaceView surface;
    private View panel, infoBar;
    private ListView catList, chList;
    private EditText search;
    private TextView title, hint, status, numTv, infoNum, infoName, infoEpg;
    private ImageView infoLogo;

    private ExoPlayer player;
    private DefaultHttpDataSource.Factory httpFactory;
    private boolean kids, destroyed;
    private int loadGen;
    private String srcName = "", srcUrl = "";
    private String catSel = "";        // "" = all, FAV = favorites, else group name
    private String query = "";
    private String presetFilter = "";  // name filter given by the caller (Kids "Islamic"), "a|b|c"
    private Channel current;
    private boolean everStarted;
    private int retries, skipCount;
    private boolean forceHls, okLongDone, autoSkipping;
    private final StringBuilder numBuf = new StringBuilder();

    private final List<Channel> all = new ArrayList<>(), shown = new ArrayList<>();
    private final List<String> catKeys = new ArrayList<>();
    private final Map<String, Integer> catCounts = new HashMap<>();
    private final Set<String> favs = new HashSet<>();
    private volatile Map<String, EpgLoader.Epg> epg = new HashMap<>();
    private ChannelAdapter chAdapter;
    private CatAdapter catAdapter;
    private Drawable selBg;

    private final Runnable hideInfo = new Runnable() { public void run() { infoBar.setVisibility(View.GONE); } };
    private final Runnable hideStatus = new Runnable() { public void run() { status.setVisibility(View.GONE); } };
    private final Runnable retryRun = new Runnable() { public void run() {
        if (!destroyed && current != null) startPlayback(current);
    } };
    private final Runnable skipRun = new Runnable() { public void run() {
        if (destroyed) return;
        autoSkipping = true;
        try { zap(1); } finally { autoSkipping = false; }
    } };
    private final Runnable numCommit = new Runnable() { public void run() {
        String s = numBuf.toString();
        numBuf.setLength(0);
        numTv.setVisibility(View.GONE);
        try {
            int n = Integer.parseInt(s);
            if (n >= 1 && n <= shown.size()) play(shown.get(n - 1));
            else showStatus("No channel " + n, 2000);
        } catch (NumberFormatException ignored) { }
    } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Util.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_iptv);
        surface = (SurfaceView) findViewById(R.id.surface);
        panel = findViewById(R.id.panel);
        infoBar = findViewById(R.id.infoBar);
        catList = (ListView) findViewById(R.id.catList);
        chList = (ListView) findViewById(R.id.chList);
        search = (EditText) findViewById(R.id.search);
        title = (TextView) findViewById(R.id.title);
        hint = (TextView) findViewById(R.id.hint);
        status = (TextView) findViewById(R.id.status);
        numTv = (TextView) findViewById(R.id.numTv);
        infoNum = (TextView) findViewById(R.id.infoNum);
        infoName = (TextView) findViewById(R.id.infoName);
        infoEpg = (TextView) findViewById(R.id.infoEpg);
        infoLogo = (ImageView) findViewById(R.id.infoLogo);
        kids = getIntent().getBooleanExtra(EXTRA_KIDS, false);
        String pf = getIntent().getStringExtra(EXTRA_FILTER);
        presetFilter = pf == null ? "" : pf.toLowerCase(Locale.ROOT);

        panel.getLayoutParams().width = Math.round(Util.screenW(this) * 0.66f);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(42));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(Util.px(34), Util.px(26), Util.px(30), Util.px(4));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(22));
        hint.setPadding(Util.px(34), 0, Util.px(30), Util.px(12));
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(30));
        search.setPadding(Util.px(26), Util.px(12), Util.px(26), Util.px(12));
        ((LinearLayout.LayoutParams) search.getLayoutParams()).setMargins(Util.px(26), Util.px(4), Util.px(26), Util.px(14));
        status.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(34));
        status.setPadding(Util.px(44), Util.px(24), Util.px(44), Util.px(24));
        numTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(90));
        numTv.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        numTv.setPadding(Util.px(40), Util.px(10), Util.px(40), Util.px(10));
        ((FrameLayout.LayoutParams) numTv.getLayoutParams()).setMargins(0, Util.px(50), Util.px(60), 0);
        infoBar.setPadding(Util.px(70), Util.px(90), Util.px(70), Util.px(40));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Util.px(150), Util.px(100));
        lp.rightMargin = Util.px(24);
        infoLogo.setLayoutParams(lp);
        infoLogo.setPadding(Util.px(10), Util.px(10), Util.px(10), Util.px(10));
        infoNum.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(54));
        infoNum.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        infoNum.setPadding(0, 0, Util.px(28), 0);
        infoName.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(44));
        infoName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        infoEpg.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(28));

        selBg = Util.round(0x33FFFFFF, Util.px(16));
        catAdapter = new CatAdapter();
        chAdapter = new ChannelAdapter();
        catList.setAdapter(catAdapter);
        chList.setAdapter(chAdapter);
        catList.setSelector(Util.round(0x40FFFFFF, Util.px(16)));
        chList.setSelector(Util.round(0x40FFFFFF, Util.px(16)));
        catList.setPadding(Util.px(16), 0, Util.px(8), Util.px(20));
        chList.setPadding(Util.px(8), 0, Util.px(16), Util.px(20));
        catList.setClipToPadding(false);
        chList.setClipToPadding(false);

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

        // choose source: explicit playlist (Kids card / file manager) or the last one used
        String u = getIntent().getStringExtra(EXTRA_URL);
        if (u != null) {
            loadSource(getIntent().getStringExtra(EXTRA_TITLE), u);
        } else {
            String ln = Prefs.sp(this).getString("last_src_name", null), lu = Prefs.sp(this).getString("last_src_url", null);
            if (ln != null && lu != null) loadSource(ln, lu); else loadSource(PRESETS[0][0], PRESETS[0][1]);
        }
    }

    // ---------- playlist loading ----------
    private void loadSource(final String name, final String url) {
        srcName = name == null ? "Playlist" : name;
        srcUrl = url;
        if (!kids) Prefs.sp(this).edit().putString("last_src_name", srcName).putString("last_src_url", url).apply();
        title.setText(srcName);
        showStatus("Loading playlist…", 0);
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
                            showStatus("Could not load playlist (" + (fErr != null ? fErr : fPerr) + "). Check the internet connection or the playlist address.", 8000);
                            showPanel();
                            return;
                        }
                        all.clear(); all.addAll(list);
                        epg = new HashMap<>();
                        loadFavs();
                        buildCategories();
                        catSel = "";
                        applyFilter();
                        title.setText(srcName + "  ·  " + all.size() + " channels");
                        if (all.isEmpty()) { showStatus("The playlist contains no playable entries.", 6000); showPanel(); }
                        else if (cached) showStatus("Offline - showing saved copy of this playlist.", 4000);
                        else hideStatusNow();
                        autoPlay();
                        startEpg(hdr[0], gen);
                    }
                });
            }
        }, "playlist").start();
    }

    private Channel byUrl(String u) {
        if (u == null) return null;
        for (Channel c : all) if (c.url.equals(u)) return c;
        return null;
    }

    /** Start playing without the user doing anything: keep the current channel if it is in this list, else last watched, else first. */
    private void autoPlay() {
        if (shown.isEmpty()) return;
        if (current != null && byUrl(current.url) != null) return;
        Channel t = kids ? null : byUrl(Prefs.sp(this).getString("last_ch_url", null));
        if (t == null || !shown.contains(t)) t = shown.get(0);
        play(t);
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
                        if (current != null && infoBar.getVisibility() == View.VISIBLE) showInfo(current);
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

    private static boolean nameMatches(Channel c, String q) {
        if (q.isEmpty()) return true;
        String n = c.name.toLowerCase(Locale.ROOT);
        for (String part : q.split("\\|")) {
            part = part.trim();
            if (!part.isEmpty() && n.contains(part)) return true;
        }
        return false;
    }

    private void fill(String q) {
        shown.clear();
        for (Channel c : all) {
            boolean ok = catSel.equals(FAV) ? favs.contains(c.url) : (catSel.isEmpty() || c.group.equals(catSel));
            if (ok && nameMatches(c, q)) shown.add(c);
        }
    }

    private void applyFilter() {
        fill(query.isEmpty() ? presetFilter : query);
        // a caller's filter must never leave the list empty: fall back to the whole category
        if (shown.isEmpty() && query.isEmpty() && !presetFilter.isEmpty()) fill("");
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
                    if (s == Player.STATE_BUFFERING) showStatus("Buffering…", 0);
                    else if (s == Player.STATE_READY) { retries = 0; skipCount = 0; hideStatusNow(); }
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
                        showStatus("Connection lost - retrying (" + retries + "/" + MAX_RETRIES + ")…", 0);
                        ui.removeCallbacks(retryRun);
                        ui.postDelayed(retryRun, 2000L * retries);
                    } else if (skipCount < MAX_SKIP && shown.size() > 1) {
                        // dead channel: move on by itself so the screen is never stuck on a broken stream
                        skipCount++;
                        showStatus("Channel unavailable - trying the next one…", 0);
                        ui.removeCallbacks(skipRun);
                        ui.postDelayed(skipRun, 1200);
                    } else {
                        showStatus("No working channel found right now (" + e.getErrorCodeName()
                                + "). Pick another channel or check the internet.", 8000);
                        showPanel();
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
        if (!autoSkipping) skipCount = 0;
        ui.removeCallbacks(retryRun);
        ui.removeCallbacks(skipRun);
        if (!kids) Prefs.sp(this).edit().putString("last_ch_url", ch.url).putString("last_ch_name", ch.name).apply();
        startPlayback(ch);
        hidePanel();
        showInfo(ch);
    }

    private void startPlayback(Channel ch) {
        showStatus("Loading " + ch.name + "…", 0);
        ExoPlayer p = ensurePlayer();
        Map<String, String> hdr = new HashMap<>();
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
    private void showPanel() {
        panel.setVisibility(View.VISIBLE);
        infoBar.setVisibility(View.GONE);
        int i = shown.indexOf(current);
        if (i >= 0) chList.setSelection(i);
        chList.requestFocus();
    }
    private void hidePanel() { panel.setVisibility(View.GONE); }

    private void showStatus(String s, long hideAfter) {
        status.setText(s);
        status.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideStatus);
        if (hideAfter > 0) ui.postDelayed(hideStatus, hideAfter);
    }
    private void hideStatusNow() { ui.removeCallbacks(hideStatus); status.setVisibility(View.GONE); }

    private void showInfo(final Channel c) {
        int n = shown.indexOf(c);
        if (n < 0) n = all.indexOf(c);
        infoNum.setText(String.valueOf(n + 1));
        infoName.setText(c.name);
        StringBuilder sb = new StringBuilder();
        EpgLoader.Epg e = epg.get(c.tvgId);
        if (e != null) {
            if (e.now != null && e.nowStop > System.currentTimeMillis()) sb.append("Now: ").append(e.now);
            if (e.next != null) sb.append(sb.length() > 0 ? "\n" : "").append("Next: ").append(e.next);
        }
        if (sb.length() == 0) sb.append(c.group);
        infoEpg.setText(sb.toString());
        infoLogo.setImageDrawable(null);
        infoLogo.setTag(c.logo);
        infoLogo.setVisibility(View.GONE);
        Bitmap b = c.logo.isEmpty() ? null : LogoLoader.peek(c.logo);
        if (b != null) { infoLogo.setImageBitmap(b); infoLogo.setVisibility(View.VISIBLE); }
        else if (!c.logo.isEmpty()) {
            LogoLoader.load(c.logo, Util.px(150), new LogoLoader.Cb() {
                public boolean wanted() { return !destroyed && c.logo.equals(infoLogo.getTag()); }
                public void done(Bitmap bm) { infoLogo.setImageBitmap(bm); infoLogo.setVisibility(View.VISIBLE); }
            });
        }
        infoBar.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        ui.removeCallbacks(hideInfo);
        ui.postDelayed(hideInfo, 5000);
    }

    // ---------- remote keys ----------
    private static int digitOf(int k) {
        if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) return k - KeyEvent.KEYCODE_0;
        if (k >= KeyEvent.KEYCODE_NUMPAD_0 && k <= KeyEvent.KEYCODE_NUMPAD_9) return k - KeyEvent.KEYCODE_NUMPAD_0;
        return -1;
    }

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
        if (k == 284 /* KEYCODE_ALL_APPS, the remote's "apps" button */ || k == KeyEvent.KEYCODE_APP_SWITCH) { finish(); return true; }
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_INFO || k == KeyEvent.KEYCODE_PROG_YELLOW) {
            if (kids) { if (on && current != null) hidePanel(); else showPanel(); }
            else showSourceMenu();
            return true;
        }
        if (k == KeyEvent.KEYCODE_GUIDE) { if (on && current != null) hidePanel(); else showPanel(); return true; }
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
            int d = digitOf(k);
            if (d >= 0) {
                if (numBuf.length() >= 4) numBuf.setLength(0);
                numBuf.append(d);
                numTv.setText(numBuf.toString());
                numTv.setVisibility(View.VISIBLE);
                ui.removeCallbacks(numCommit);
                ui.postDelayed(numCommit, 1600);
                return true;
            }
            if (ok && numBuf.length() > 0) { ui.removeCallbacks(numCommit); numCommit.run(); return true; }
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
        labels.add("My playlist (M3U / M3U8 URL or file path)…");
        labels.add(hasXt ? "Xtream Codes login (edit)…" : "Xtream Codes login…");
        labels.add("Set EPG / XMLTV guide URL…");
        labels.add("Reload current playlist");
        labels.add("Exit to apps");
        Util.dialog(this).setTitle("Live TV sources")
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    if (w < n) loadSource(PRESETS[w][0], PRESETS[w][1]);
                    else if (w == n) Util.prompt(this, "M3U / M3U8 playlist", "http://…/playlist.m3u", custom, s -> {
                        if (s.isEmpty()) return;
                        Prefs.sp(this).edit().putString("custom_url", s).apply();
                        loadSource("My playlist", s);
                    });
                    else if (w == n + 1) xtreamDialog();
                    else if (w == n + 2) Util.prompt(this, "EPG (XMLTV) URL", "http://…/epg.xml or .xml.gz",
                            Prefs.sp(this).getString("epg_url", ""), s -> {
                                Prefs.sp(this).edit().putString("epg_url", s).apply();
                                loadSource(srcName, srcUrl);
                            });
                    else if (w == n + 3) { if (srcUrl != null) loadSource(srcName, srcUrl); }
                    else finish();
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
                    if (!sv.startsWith("http") || us.isEmpty()) { Toast.makeText(this, "Enter server (http://…) and username", Toast.LENGTH_LONG).show(); return; }
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
        ui.removeCallbacks(skipRun);
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
                t.setPadding(Util.px(24), Util.px(18), Util.px(10), Util.px(18));
                t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(28));
            }
            String k = catKeys.get(i);
            String txt;
            if (k.equals(FAV)) txt = FAV + " (" + favCount() + ")";
            else if (k.isEmpty()) txt = "All channels (" + all.size() + ")";
            else txt = k + " (" + catCounts.get(k) + ")";
            boolean sel = k.equals(catSel);
            t.setText(txt);
            t.setTextColor(sel ? Color.WHITE : 0xFFA5A8B8);
            t.setTypeface(Typeface.create("sans-serif-medium", sel ? Typeface.BOLD : Typeface.NORMAL));
            t.setBackground(sel ? selBg.getConstantState().newDrawable() : null);
            return t;
        }
        private int favCount() { int n = 0; for (Channel c : all) if (favs.contains(c.url)) n++; return n; }
    }

    private static final class Row {
        TextView num, name, sub, letter;
        FrameLayout box;
        ImageView logo;
    }

    private final class ChannelAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View v, ViewGroup parent) {
            final Row h;
            LinearLayout row;
            if (v == null) {
                row = new LinearLayout(IptvActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Util.px(14), Util.px(10), Util.px(14), Util.px(10));
                h = new Row();
                h.num = new TextView(IptvActivity.this);
                h.num.setTextColor(0xFF8A8DA0);
                h.num.setGravity(Gravity.CENTER);
                h.num.setSingleLine(true);
                h.num.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(24));
                row.addView(h.num, new LinearLayout.LayoutParams(Util.px(66), -2));
                h.box = new FrameLayout(IptvActivity.this);
                h.letter = new TextView(IptvActivity.this);
                h.letter.setGravity(Gravity.CENTER);
                h.letter.setTextColor(Color.WHITE);
                h.letter.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                h.letter.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(34));
                h.box.addView(h.letter, new FrameLayout.LayoutParams(-1, -1));
                h.logo = new ImageView(IptvActivity.this);
                h.logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                h.logo.setPadding(Util.px(6), Util.px(6), Util.px(6), Util.px(6));
                h.box.addView(h.logo, new FrameLayout.LayoutParams(-1, -1));
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Util.px(100), Util.px(64));
                bl.leftMargin = Util.px(6);
                bl.rightMargin = Util.px(20);
                row.addView(h.box, bl);
                LinearLayout col = new LinearLayout(IptvActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                h.name = new TextView(IptvActivity.this);
                h.sub = new TextView(IptvActivity.this);
                h.name.setSingleLine(true); h.sub.setSingleLine(true);
                h.name.setTextColor(Color.WHITE); h.sub.setTextColor(0xFF9A9DB0);
                h.name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(29));
                h.sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Util.px(21));
                col.addView(h.name); col.addView(h.sub);
                row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
                row.setTag(h);
            } else { row = (LinearLayout) v; h = (Row) row.getTag(); }

            Channel c = shown.get(i);
            h.num.setText(String.valueOf(i + 1));
            h.name.setText((favs.contains(c.url) ? "★ " : "") + c.name);
            EpgLoader.Epg e = epg.get(c.tvgId);
            h.sub.setText((e != null && e.now != null && e.nowStop > System.currentTimeMillis()) ? "Now: " + e.now : c.group);

            // logo (loaded lazily) or a coloured initial badge
            String initial = "?";
            for (int k = 0; k < c.name.length(); k++) {
                if (Character.isLetterOrDigit(c.name.charAt(k))) { initial = String.valueOf(c.name.charAt(k)).toUpperCase(Locale.ROOT); break; }
            }
            final String lu = c.logo;
            h.logo.setTag(lu);
            h.logo.setImageDrawable(null);
            h.letter.setText(initial);
            h.letter.setVisibility(View.VISIBLE);
            h.box.setBackground(Util.round(BADGE[(c.name.hashCode() & 0x7fffffff) % BADGE.length], Util.px(12)));
            if (!lu.isEmpty()) {
                Bitmap b = LogoLoader.peek(lu);
                if (b != null) useLogo(h, b);
                else LogoLoader.load(lu, Util.px(100), new LogoLoader.Cb() {
                    public boolean wanted() { return !destroyed && lu.equals(h.logo.getTag()); }
                    public void done(Bitmap bm) { useLogo(h, bm); }
                });
            }
            return row;
        }

        private void useLogo(Row h, Bitmap b) {
            h.logo.setImageBitmap(b);
            h.letter.setVisibility(View.GONE);
            h.box.setBackground(Util.round(0x30FFFFFF, Util.px(12)));
        }
    }
}
