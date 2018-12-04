package org.videolan.medialibrary;


import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

import androidx.annotation.Nullable;

public class Tools {

    private static final String TAG = "VLC/Tools";
    private static StringBuilder sb = new StringBuilder();
    private static DecimalFormat format = (DecimalFormat) NumberFormat.getInstance(Locale.US);
    static {
        format.applyPattern("00");
    }

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
        if (lastTime == 0L) return "";
        return String.format("%s / %s",
                millisToString(lastTime, true, false),
                millisToString(media.getLength(), true, false));
    }

    /**
     * Convert time to a string
     * @param millis e.g.time/length from file
     * @return formated string (hh:)mm:ss
     */
    public static String millisToString(long millis) {
        return millisToString(millis, false, true);
    }

    /**
     * Convert time to a string
     * @param millis e.g.time/length from file
     * @return formated string "[hh]h[mm]min" / "[mm]min[s]s"
     */
    public static String millisToText(long millis) {
        return millisToString(millis, true, true);
    }

    public static String getResolution(MediaWrapper media) {
        if (media.getWidth() > 0 && media.getHeight() > 0)
            return String.format(Locale.US, "%dx%d", media.getWidth(), media.getHeight());
        return "";
    }

    public static void setMediaDescription (MediaLibraryItem item) {
        if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
            final MediaWrapper mw = (MediaWrapper) item;
            if (mw.getType() == MediaWrapper.TYPE_VIDEO) {
                final String progress = mw.getLength() == 0L ? null : mw.getTime() == 0L ? Tools.millisToString(mw.getLength()) : getProgressText(mw);
                final String resolution = getResolution(mw);
                boolean hasprogress = !TextUtils.isEmpty(progress), hasResolution = !TextUtils.isEmpty(resolution);
                final StringBuilder sb = new StringBuilder();
                if (hasprogress && hasResolution) sb.append(resolution).append(" - ").append(progress);
                else if (hasprogress) sb.append(progress);
                else sb.append(resolution);
                item.setDescription(sb.toString());
            } else if (mw.getType() == MediaWrapper.TYPE_AUDIO) {
                final String artist = mw.getReferenceArtist(), album = mw.getAlbum();
                final StringBuilder sb = new StringBuilder();
                boolean hasArtist = !TextUtils.isEmpty(artist), hasAlbum = !TextUtils.isEmpty(album);
                if (hasArtist && hasAlbum) sb.append(album).append(" - ").append(artist);
                else if (hasArtist) sb.append(artist);
                else sb.append(album);
                item.setDescription(sb.toString());
            }
        }
    }

    public static String millisToString(long millis, boolean text, boolean seconds) {
        sb.setLength(0);
        if (millis < 0) {
            millis = -millis;
            sb.append("-");
        }

        millis /= 1000;
        int sec = (int) (millis % 60);
        millis /= 60;
        int min = (int) (millis % 60);
        millis /= 60;
        int hours = (int) millis;

        if (text) {
            if (hours > 0)
                sb.append(hours).append('h');
            if (min > 0)
                sb.append(min).append("min");
            if ((seconds || sb.length() == 0) && sec > 0)
                sb.append(sec).append("s");
        } else {
            if (hours > 0)
                sb.append(hours).append(':').append(format.format(min)).append(':').append(format.format(sec));
            else
                sb.append(min).append(':').append(format.format(sec));
        }
        return sb.toString();
    }

    public static String encodeVLCString(String mrl) {
        return Uri.encode(Uri.decode(mrl), ".-_~/()&!$*+,;='@:");
    }

    public static String encodeVLCMrl(String mrl) {
        if (mrl.startsWith("/")) mrl = "file://"+mrl;
        return encodeVLCString(mrl);
    }
}
