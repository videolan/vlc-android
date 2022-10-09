package org.videolan.medialibrary;


import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.media.MediaLibraryItem;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Pattern;

public class Tools {

    private static final String TAG = "VLC/Tools";
    private static final ThreadLocal<NumberFormat> TWO_DIGITS = new ThreadLocal<NumberFormat>() {
        @Override
        protected NumberFormat initialValue() {
            NumberFormat fmt = NumberFormat.getInstance(Locale.US);
            if (fmt instanceof DecimalFormat) ((DecimalFormat) fmt).applyPattern("00");
            return fmt;
        }
    };

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
        if (lastTime <= 0L) return "";
        return String.format("%s / %s",
                millisToString(lastTime, true, false, false),
                millisToString(media.getLength(), true, false, false));
    }

    /**
     * Convert time to a string
     * @param millis e.g.time/length from file
     * @return formated string (hh:)mm:ss
     */
    public static String millisToString(long millis) {
        return millisToString(millis, false, true, false);
    }

    /**
     * Convert time to a string
     * @param millis e.g.time/length from file
     * @return formated string "[hh]h[mm]min" / "[mm]min[s]s"
     */
    public static String millisToText(long millis) {
        return millisToString(millis, true, true, false);
    }

    /**
     * Convert time to a string with large formatting
     *
     * @param millis e.g.time/length from file
     * @return formated string "[hh]h [mm]min " / "[mm]min [s]s"
     */
    public static String millisToTextLarge(long millis) {
        return millisToString(millis, true, true, true);
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
                boolean hasprogress = !TextUtils.isEmpty(progress);
                final StringBuilder sb = new StringBuilder();
                if (hasprogress) sb.append(progress); else sb.append(Tools.millisToString(mw.getLength()));
                item.setDescription(sb.toString());
            } else if (mw.getType() == MediaWrapper.TYPE_AUDIO) {
                final String artist = mw.getReferenceArtist(), album = mw.getAlbum();
                final StringBuilder sb = new StringBuilder();
                boolean hasArtist = !TextUtils.isEmpty(artist), hasAlbum = !TextUtils.isEmpty(album);
                if (hasArtist && hasAlbum) sb.append(artist).append(" - ").append(album);
                else if (hasArtist) sb.append(artist);
                else sb.append(album);
                item.setDescription(sb.toString());
            }
        }
    }

    public static String millisToString(long millis, boolean text, boolean seconds, boolean large) {
        StringBuilder sb = new StringBuilder();
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
                sb.append(hours).append('h').append(large ? " " : "");
            if (min > 0)
                sb.append(min).append("min").append(large ? " " : "");
            if ((seconds || sb.length() == 0) && sec > 0)
                sb.append(sec).append("s").append(large ? " " : "");
        } else {
            if (hours > 0)
                sb.append(hours).append(':').append(large ? " " : "").append(TWO_DIGITS.get().format(min)).append(':').append(large ? " " : "").append(TWO_DIGITS.get().format(sec));
            else
                sb.append(min).append(':').append(large ? " " : "").append(TWO_DIGITS.get().format(sec));
        }
        return sb.toString();
    }

    public static String mlEncodeMrl(String mrl) {
        if (mrl.startsWith("/")) mrl = "file://"+mrl;
        mrl = mrl.replace(" ", "%20");
        mrl = mrl.replace("+", "%2B");
        return VLCUtil.encodeVLCString(mrl);
    }

    public static String encodeVLCMrl(String mrl) {
        if (mrl.startsWith("/")) mrl = "file://"+mrl;
        return VLCUtil.encodeVLCString(mrl);
    }

    /**
     * Search in a case insensitive manner for a substring in a source string
     * @param source Source string in which to look for the substring
     * @param substring substring to search in the source string
     * @return presence of the substring as a boolean
     */
    public static Boolean hasSubString(String source, String substring) {
        return Pattern.compile(Pattern.quote(substring), Pattern.CASE_INSENSITIVE).matcher(source).find();
    }
}
