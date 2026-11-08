package com.rummish.saleemtv;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Streaming M3U/M3U8 (extended) parser. Reads line by line so big lists do not blow the 200MB budget. */
final class M3uParser {
    private static final Pattern ATTR = Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"");

    /** @param hdr optional 1-element array receiving the playlist's EPG url (url-tvg) */
    static List<Channel> parse(File f, int cap, String[] hdr) throws IOException {
        List<Channel> out = new ArrayList<>();
        BufferedReader r = new BufferedReader(new InputStreamReader(Util.openMaybeGzip(f), "UTF-8"), 32 * 1024);
        try {
            String line;
            Channel pending = null;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXTM3U")) {
                    Matcher m = ATTR.matcher(line);
                    while (m.find()) {
                        String k = m.group(1).toLowerCase(Locale.ROOT);
                        if ((k.equals("url-tvg") || k.equals("x-tvg-url")) && hdr != null && hdr.length > 0)
                            hdr[0] = m.group(2).split(",")[0].trim();
                    }
                } else if (line.startsWith("#EXTINF")) {
                    pending = new Channel();
                    String tvgName = "";
                    Matcher m = ATTR.matcher(line);
                    while (m.find()) {
                        String k = m.group(1).toLowerCase(Locale.ROOT), v = m.group(2).trim();
                        if (k.equals("tvg-id")) pending.tvgId = v;
                        else if (k.equals("tvg-logo")) pending.logo = v;
                        else if (k.equals("tvg-name")) tvgName = v;
                        else if (k.equals("group-title") && !v.isEmpty()) pending.group = v.split(";")[0].trim();
                    }
                    int q = line.lastIndexOf('"');
                    int comma = line.indexOf(',', q < 0 ? 0 : q);
                    String name = comma >= 0 ? line.substring(comma + 1).trim() : "";
                    if (name.isEmpty()) name = tvgName;
                    if (name.isEmpty()) name = "Channel " + (out.size() + 1);
                    pending.name = name;
                } else if (line.charAt(0) == '#') {
                    // per-stream headers used by many public lists (iptv-org)
                    if (pending != null && line.regionMatches(true, 0, "#EXTVLCOPT:", 0, 11)) {
                        String o = line.substring(11);
                        int eq = o.indexOf('=');
                        if (eq > 0) {
                            String k = o.substring(0, eq).trim().toLowerCase(Locale.ROOT), v = o.substring(eq + 1).trim();
                            if (k.equals("http-user-agent")) pending.ua = v;
                            else if (k.equals("http-referrer") || k.equals("http-referer")) pending.referer = v;
                        }
                    }
                } else if (pending != null) {
                    pending.url = line;
                    out.add(pending);
                    pending = null;
                    if (out.size() >= cap) break;
                }
            }
        } finally {
            r.close();
        }
        return out;
    }
}
