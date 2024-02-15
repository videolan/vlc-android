package org.videolan.vlc.gui.audio;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class SmallPersistantMap {
    public static File path;

    public synchronized static void put(String key, Object v) throws IOException {
        initPath();
        key = toFn(key);
        Log.d("SmallMap", String.format("put[%s]=%s", key, v));
        File f = new File(path, key);
        FileOutputStream out = new FileOutputStream(f);
        out.write(v.toString().getBytes("utf8"));
        out.close();
    }

    private static String toFn(String key) {
        int p1 = key.lastIndexOf('/');
        if (p1 >= 0) key = key.substring(p1 + 1);

        return "v_" + key.replace('/', '_')
                .replace('\\', '_') + ".txt";
    }

    private synchronized static void initPath() {
        if (path == null) {
            File dir = Environment.getExternalStorageDirectory();
            File dir2 = new File(dir, "neoesmallmap");
            dir2.mkdirs();
            path = dir2;
        }
    }

    public synchronized static String get(String key) throws IOException {
        initPath();
        key = toFn(key);
        Log.d("SmallMap", String.format("get[%s]", key));
        File f = new File(path, key);
        if (!f.isFile()) return null;
        return Lyrics.readString(new FileInputStream(f), null);
    }

    public static Integer getInt(String key) throws IOException {
        String v = get(key);
        if (v == null) return null;
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}