package org.videolan.medialibrary;


import android.net.Uri;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class Tools {


    /*
     * Convert file:// uri from real path to emulated FS path.
     */
    public static Uri convertLocalUri(Uri uri) {
        if (!TextUtils.equals(uri.getScheme(), "file") || !uri.getPath().startsWith("/sdcard"))
            return uri;
        String path = uri.toString();
        return Uri.parse(path.replace("/sdcard", Environment.getExternalStorageDirectory().getPath()));
    }

    public static boolean isArrayEmpty(@Nullable Object[] array) {
        return array == null || array.length == 0;
    }
    public static String getProgressText(MediaWrapper media) {
        long lastTime = media.getTime();
        if (lastTime == 0L)
            return "";
        return String.format("%s / %s",
                millisToString(lastTime, true),
                millisToString(media.getLength(), true));
    }

    public static String getResolution(MediaWrapper media) {
        if (media.getWidth() > 0 && media.getHeight() > 0)
            return String.format(Locale.US, "%dx%d", media.getWidth(), media.getHeight());
        return "";
    }

    public static void setMediaDescription (MediaLibraryItem item) {
        if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
            MediaWrapper mw = (MediaWrapper) item;
            if (mw.getType() == MediaWrapper.TYPE_VIDEO) {
                String progress = getProgressText(mw);
                String resolution = getResolution(mw);
                boolean hasprogress = !TextUtils.isEmpty(progress), hasResolution = !TextUtils.isEmpty(resolution);
                if (hasprogress && hasResolution)
                    item.setDescription(resolution+" - "+progress);
                else if (hasprogress)
                    item.setDescription(progress);
                else
                    item.setDescription(resolution);
            } else if (mw.getType() == MediaWrapper.TYPE_AUDIO) {
                String artist = mw.getReferenceArtist(), album = mw.getAlbum();
                boolean hasArtist = !TextUtils.isEmpty(artist), hasAlbum = !TextUtils.isEmpty(album);
                if (hasArtist && hasAlbum)
                    item.setDescription(album+" - "+artist);
                else if (hasArtist)
                    item.setDescription(artist);
                else
                    item.setDescription(album);
            }
        }
    }

    static String millisToString(long millis, boolean text) {
        boolean negative = millis < 0;
        millis = java.lang.Math.abs(millis);

        millis /= 1000;
        int sec = (int) (millis % 60);
        millis /= 60;
        int min = (int) (millis % 60);
        millis /= 60;
        int hours = (int) millis;

        String time;
        DecimalFormat format = (DecimalFormat) NumberFormat.getInstance(Locale.US);
        format.applyPattern("00");
        if (text) {
            if (millis > 0)
                time = (negative ? "-" : "") + hours + "h" + format.format(min) + "min";
            else if (min > 0)
                time = (negative ? "-" : "") + min + "min";
            else
                time = (negative ? "-" : "") + sec + "s";
        }
        else {
            if (millis > 0)
                time = (negative ? "-" : "") + hours + ":" + format.format(min) + ":" + format.format(sec);
            else
                time = (negative ? "-" : "") + min + ":" + format.format(sec);
        }
        return time;
    }
}
