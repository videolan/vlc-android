package org.videolan.vlc.gui.audio;

import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.AudioPlayerBinding;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class Lyrics {
    private static final String TAG = "Lyrics";
    private static final int QSIZE = 5;
    private static AudioPlayerBinding binding;
    private static PlaybackService service;
    private static boolean justOpened;

    public static class Line {
        String text;
        long ts;
    }

    static List<Line> lines;
    public static int lastPos;
    static Map<String, List<Line>> cache = Collections.synchronizedMap(new HashMap<>());

    public static void open(Uri uri, PlaybackService service0) {
        service = service0;
        clear();
        String fn = uri.toString(); // maybe also work for url?
        Log.d(TAG, "open: " + fn);
        int p1 = fn.lastIndexOf('.');
        if (p1 < 0) {

            return;
        }
        fn = fn.substring(0, p1) + ".lrc";
        try {
            openFile(Uri.parse(fn).getPath());
        } catch (Exception ex) {
            Log.w(TAG, "open: " + ex);
        }

    }

    static String url;

    private static int openFile(String url) {
        Log.d(TAG, "open: " + url);
        Lyrics.url = url;
        lines = cache.get(url);
        if (lines != null) {
            justOpened = true;
            return 0;
        }
        int p1 = url.lastIndexOf(".");
        if (p1 < 0) return 0;
        String url2 = url.substring(0, p1) + ".lrc";
        try {
            lines = readLyrics(url2);
            cache.put(url, lines);
            Log.d(TAG, String.format("open:read %,d lines of lyrics", lines.size()));
            if (lines.isEmpty()) lines = null;
            else {
                setLinePos(0);
                justOpened = true;
            }
        } catch (Throwable ex) {
            Log.e(TAG, "open: ", ex);
        }
        return 0;
    }

    public static void setLinePos(int p) {
        if (lines == null) return;
        if (p < 0) p = 0;
        p = Math.min(p, lines.size() - 1);
        if (p != lastPos || p == 0) {
            if (setUILines(p)) lastPos = p;
        }
    }

    public static boolean setUILines(int p) {
        try {
            if (binding == null) {
                Log.d(TAG, "setUILines: exit cos binding==null");
                return false;
            }
            View v = binding.coverMediaSwitcher.getRootView();
            if (v != null && v.findViewById(R.id.line_current) != null) {// init timing
                v.post(() -> {
                    try {
                        setText(v.findViewById(R.id.line_current), getLineText(p));
                        setText(v.findViewById(R.id.line_n1), getLineText(p + 1));
                        setText(v.findViewById(R.id.line_p1), getLineText(p - 1));
                        setText(v.findViewById(R.id.line_p2), getLineText(p - 2));
                        Log.d(TAG, "setUILines: OK");
                    } catch (Exception ex) {
                        Log.w(TAG, "setUILines: " + ex);
                    }
                });
                return true;
            } else {
                Log.d(TAG, "setUILines: exit cos v==null");
                return false;
            }
        } catch (Exception ex) {
            Log.w(TAG, "setUILines: " + ex);
        }
        return false;
    }

    private static void setText(TextView view, CharSequence text) {
        view.setText(text);
    }

    private static String getLineText(int p) {
        if (p < 0 || p >= lines.size()) return "";
        return specialFilter1(lines.get(p).text);
    }

    private static String specialFilter1(String s) {
        if (s == null) return "";
        //step1
        {
            int p1 = s.indexOf("--(");
            if (p1 > 0) {
                s = s.substring(p1 + 3);
                if (s.endsWith(")")) s = s.substring(0, s.length() - 1);
            }
        }
        //step2
        int safe = 0;
        while (true) {
            if (safe++ > 10) break;
            int p1 = s.indexOf("(");
            if (p1 < 0) break;
            int p2 = s.indexOf(")", p1 + 1);
            if (p2 < 0) break;
            String s2 = s.substring(p1 + 1, p2);
            if (s2.startsWith("st ") || allIn(s2, "0123456789,/")) {
                s = s.substring(0, p1) + s.substring(p2 + 1);
            } else break;
        }
        return s;
    }

    private static boolean allIn(String s, String pat) {
        for (char c : s.toCharArray()) {
            if (pat.indexOf(c) < 0) return false;
        }
        return true;
    }

    private static List<Line> readLyrics(String url) throws IOException {
        String text0 = readString(new FileInputStream(url), null);
        List<Line> res = new ArrayList<>();
        Finder f = new Finder(text0);
        f.find("[");
        while (true) {
            if (f.finished()) break;
            String ts = f.readUntil("]");
            long ts1 = parseLrcTime(ts);
            if (ts1 < 0) {//failed
                f.find("[");
                continue;
            }
            String line0 = f.readUntil("\n") + f.readUntil("[").trim();
            Line line = new Line();
            line.ts = ts1;
            line.text = line0;
            res.add(line);
        }
        //no sort by ts, assume source data is sorted
        return res;
    }

    private static long parseLrcTime(String ts) {
        int p1 = ts.indexOf(":");
        if (p1 <= 0) return -1;
        try {
            return Long.parseLong(ts.substring(0, p1).trim()) * 60000 + (long) (1000 * Float.parseFloat(ts.substring(p1 + 1).trim()));
        } catch (Exception ex) {
        }
        return -1;
    }


    static class FixSizedQueue<T> {
        int maxsize;

        FixSizedQueue(int size) {
            this.maxsize = size;
        }

        void add(T v) {
            queue.add(v);
            if (queue.size() > maxsize) queue.removeFirst();
        }

        int size() {
            return queue.size();
        }

        LinkedList<T> queue = new LinkedList<T>();
    }

    static FixSizedQueue queue = new FixSizedQueue<Integer>(QSIZE);

    public static void clear() {
        Log.d(TAG, "clear");
        hint = 0;
        lines = null;
        lastPos = -1;
        url = null;
    }

    public static void seek(long time) {
        hint = 0;
        lastPos = -1;
    }

    private static int locate(long pos, int hint0) {
        int size = lines.size();
        int f = size - 1;
        for (int i = hint0; i < size; i++) {
            if (lines.get(i).ts > pos) {
                f = i - 1;
                break;
            }
        }
        if (f < 0) f = 0;
        if (f == size - 1 && hint0 > 0) {
            f = locate(pos, 0);
        }
        Log.d(TAG, String.format("process: pos=%,d and linepos=%,d, lastPos=%,d hint=%,d", pos, f, lastPos, hint));
        Lyrics.hint = f;
        return f;
    }

    public static int hint;

    public static int progress(long pos) {

        if (lines == null) return 0;
        if (justOpened) {
            justOpened = false;
            if (resume()) return 0;
        }
        int f = locate(pos, hint);

        setLinePos(f);
        queue.add(f);
        if (f > 0 && queue.size() >= QSIZE
                && ((Integer) queue.queue.getLast()) - ((Integer) queue.queue.getFirst())
                <= QSIZE * 2) {
            try {
                SmallPersistantMap.put(url, f);
                Log.d(TAG, "record linepos=" + f);
            } catch (Exception ex) {
                Log.e(TAG, "SmallPersistantMap.save " + ex);
            }
            queue.queue.clear();// wait for another round
        }
        return 0;

    }


    public static void setUI(@NotNull AudioPlayerBinding binding0) {
        binding = binding0;
        binding.coverMediaSwitcher.setVisibility(View.GONE);
    }

    public static String readString(InputStream ins, String enc) throws IOException {
        if (enc == null)
            enc = "UTF-8";
        BufferedReader in = new BufferedReader(new InputStreamReader(ins, enc));
        char[] buf = new char[1000];
        int len;
        StringBuilder sb = new StringBuilder();
        while ((len = in.read(buf)) > 0) {
            sb.append(buf, 0, len);
        }
        in.close();
        return sb.toString();
    }

    static
    class Finder {
        public int i;
        public String text;

        public Finder(String page) {
            this.text = page;
            i = 0;
        }

        public void find(String s) {
            int p1 = text.indexOf(s, i);
            if (p1 < 0)
                i = text.length();
            else
                i = p1 + s.length();
        }

        public void findReverse(String s) {
            int p1 = text.substring(0, i).lastIndexOf(s);
            if (p1 < 0)
                i = text.length();
            else
                i = p1 + s.length();
        }

        public String readUntil(String s) {
            int p1 = text.indexOf(s, i);
            if (p1 < 0) {
                return readRemain();
            } else {
                String r = text.substring(i, p1);
                i = p1 + s.length();
                return r;
            }
        }

        public boolean finished() {
            return i >= text.length();
        }

        public void reset() {
            i = 0;
        }

        public void setPos(int pos) {
            i = pos;
        }

        public int getPos() {
            return i;
        }

        public String readRemain() {
            String r = text.substring(i);
            i = text.length();
            return r;
        }

        public char readChar() {
            if (finished()) {
                return (char) -1;
            }
            char c = text.charAt(i++);
            return c;
        }
    }

    private static boolean resume() {
        if (lines == null) return false;
        try {
            Integer pos = SmallPersistantMap.getInt(url);
            Log.d(TAG, String.format("try resume:(%s) for %s", pos, url));
            if (pos == null) return false;
            int ms = (int) lines.get(pos).ts;
            ms -= 1000;//why, but needed...
            if (ms < 0) ms = 0;
            service.seek(ms);
            Log.d(TAG, "resume: linepos=" + pos);
            return true;
        } catch (Exception ex) {
            Log.w("lyrics", "resume: " + ex);
        }
        return false;
    }
}
