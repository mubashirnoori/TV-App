package com.rummish.saleemtv;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Streaming XMLTV parser. Keeps ONLY "now" and "next" for channels in the current playlist,
 * so memory stays tiny even for large guide files.
 */
final class EpgLoader {
    static final class Epg {
        String now, next;
        long nowStop, nextStart;
    }

    private static final long MAX_BYTES = 60L * 1024 * 1024;

    static Map<String, Epg> load(File f, Set<String> ids) throws Exception {
        Map<String, Epg> out = new HashMap<>();
        if (ids.isEmpty() || f.length() > MAX_BYTES) return out;
        long now = System.currentTimeMillis();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        InputStream in = Util.openMaybeGzip(f);
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(in, null);
            int ev = p.getEventType();
            String ch = null;
            long st = 0, sp = 0;
            boolean want = false;
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String n = p.getName();
                    if ("programme".equals(n)) {
                        ch = p.getAttributeValue(null, "channel");
                        want = ch != null && ids.contains(ch);
                        if (want) {
                            st = time(fmt, p.getAttributeValue(null, "start"));
                            sp = time(fmt, p.getAttributeValue(null, "stop"));
                            if (st == 0 || sp <= now || st > now + 12L * 3600 * 1000) want = false;
                        }
                    } else if (want && "title".equals(n)) {
                        String t = p.nextText();
                        Epg e = out.get(ch);
                        if (e == null) { e = new Epg(); out.put(ch, e); }
                        if (st <= now && now < sp) { e.now = t; e.nowStop = sp; }
                        else if (st > now && (e.next == null || st < e.nextStart)) { e.next = t; e.nextStart = st; }
                        want = false;
                    }
                }
                ev = p.next();
            }
        } finally {
            in.close();
        }
        return out;
    }

    private static long time(SimpleDateFormat fmt, String s) {
        try {
            if (s == null) return 0;
            s = s.trim();
            if (s.length() < 14) return 0;
            long t = fmt.parse(s.substring(0, 14)).getTime();
            String z = s.substring(14).trim();
            if (z.length() == 5 && (z.charAt(0) == '+' || z.charAt(0) == '-')) {
                long off = (Integer.parseInt(z.substring(1, 3)) * 60L + Integer.parseInt(z.substring(3, 5))) * 60000L;
                t += z.charAt(0) == '+' ? -off : off;
            }
            return t;
        } catch (Exception e) {
            return 0;
        }
    }
}
