/*****************************************************************************
 * Util.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import org.json.JSONArray;
import org.json.JSONException;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.libvlc.Media;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.TypedArray;
import android.database.sqlite.SQLiteFullException;
import android.graphics.Bitmap;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

public class Util {
    public final static String TAG = "VLC/Util";
    private final static boolean hasNavBar;
    /** A set of utility functions for the VLC application */

    static {
        HashSet<String> devicesWithoutNavBar = new HashSet<String>();
        devicesWithoutNavBar.add("HTC One V");
        devicesWithoutNavBar.add("HTC One S");
        devicesWithoutNavBar.add("HTC One X");
        devicesWithoutNavBar.add("HTC One XL");
        hasNavBar = LibVlcUtil.isICSOrLater()
                && !devicesWithoutNavBar.contains(android.os.Build.MODEL);
    }

    public static LibVLC getLibVlcInstance() throws LibVlcException {
        LibVLC instance = LibVLC.getExistingInstance();
        if (instance == null) {
            Thread.setDefaultUncaughtExceptionHandler(new VlcCrashHandler());

            instance = LibVLC.getInstance();
            Context context = VLCApplication.getAppContext();
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            updateLibVlcSettings(pref);
            instance.init(context);
        }
        return instance;
    }

    public static float[] getFloatArray(SharedPreferences pref, String key) {
        float[] array = null;
        String s = pref.getString(key, null);
        if (s != null) {
            try {
                JSONArray json = new JSONArray(s);
                array = new float[json.length()];
                for (int i = 0; i < array.length; i++)
                    array[i] = (float) json.getDouble(i);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return array;
    }

    public static void putFloatArray(Editor editor, String key, float[] array) {
        try {
            JSONArray json = new JSONArray();
            for (float f : array)
                json.put(f);
            editor.putString("equalizer_values", json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void updateLibVlcSettings(SharedPreferences pref) {
        LibVLC instance = LibVLC.getExistingInstance();
        if (instance == null)
            return;

        instance.setSubtitlesEncoding(pref.getString("subtitle_text_encoding", ""));
        instance.setTimeStretching(pref.getBoolean("enable_time_stretching_audio", false));
        instance.setFrameSkip(pref.getBoolean("enable_frame_skip", false));
        instance.setChroma(pref.getString("chroma_format", ""));
        instance.setVerboseMode(pref.getBoolean("enable_verbose_mode", true));

        if (pref.getBoolean("equalizer_enabled", false))
            instance.setEqualizer(getFloatArray(pref, "equalizer_values"));

        int aout;
        try {
            aout = Integer.parseInt(pref.getString("aout", "-1"));
        }
        catch (NumberFormatException nfe) {
            aout = -1;
        }
        int vout;
        try {
        	vout = Integer.parseInt(pref.getString("vout", "-1"));
        }
        catch (NumberFormatException nfe) {
        	vout = -1;
        }
        int deblocking;
        try {
            deblocking = Integer.parseInt(pref.getString("deblocking", "-1"));
        }
        catch(NumberFormatException nfe) {
            deblocking = -1;
        }
        int hardwareAcceleration;
        try {
            hardwareAcceleration = Integer.parseInt(pref.getString("hardware_acceleration", "-1"));
        }
        catch(NumberFormatException nfe) {
            hardwareAcceleration = -1;
        }
        int networkCaching = pref.getInt("network_caching_value", 0);
        if(networkCaching > 60000)
            networkCaching = 60000;
        else if(networkCaching < 0)
            networkCaching = 0;
        instance.setAout(aout);
        instance.setVout(vout);
        instance.setDeblocking(deblocking);
        instance.setNetworkCaching(networkCaching);
        instance.setHardwareAcceleration(hardwareAcceleration);
    }

    /** Print an on-screen message to alert the user */
    public static void toaster(Context context, int stringId, int duration) {
        Toast.makeText(context, stringId, duration).show();
    }

    public static void toaster(Context context, int stringId) {
        toaster(context, stringId, Toast.LENGTH_SHORT);
    }

    public static String stripTrailingSlash(String s) {
        if( s.endsWith("/") && s.length() > 1 )
            return s.substring(0, s.length() - 1);
        return s;
    }

    public static String readAsset(String assetName, String defaultS) {
        try {
            InputStream is = VLCApplication.getAppResources().getAssets().open(assetName);
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF8"));
            StringBuilder sb = new StringBuilder();
            String line = r.readLine();
            if(line != null) {
                sb.append(line);
                line = r.readLine();
                while(line != null) {
                    sb.append('\n');
                    sb.append(line);
                    line = r.readLine();
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return defaultS;
        }
    }

    /**
     * Writes the current app logcat to a file.
     *
     * @param filename The filename to save it as
     * @throws IOException
     */
    public static void writeLogcat(String filename) throws IOException {
        String[] args = { "logcat", "-v", "time", "-d" };

        Process process = Runtime.getRuntime().exec(args);
        InputStreamReader input = new InputStreamReader(
                process.getInputStream());
        OutputStreamWriter output = new OutputStreamWriter(
                new FileOutputStream(filename));
        BufferedReader br = new BufferedReader(input);
        BufferedWriter bw = new BufferedWriter(output);
        String line;

        while ((line = br.readLine()) != null) {
            bw.write(line);
            bw.newLine();
        }

        bw.close();
        output.close();
        br.close();
        input.close();
    }

    /**
     * Convert time to a string
     * @param millis e.g.time/length from file
     * @return formated string (hh:)mm:ss
     */
    public static String millisToString(long millis)
    {
        return millisToString(millis, false);
    }

    /**
     * Convert time to a string
     * @param millis e.g.time/length from file
     * @return formated string "[hh]h[mm]min" / "[mm]min[s]s"
     */
    public static String millisToText(long millis)
    {
        return millisToString(millis, true);
    }

    private static String millisToString(long millis, boolean text) {
        boolean negative = millis < 0;
        millis = java.lang.Math.abs(millis);

        millis /= 1000;
        int sec = (int) (millis % 60);
        millis /= 60;
        int min = (int) (millis % 60);
        millis /= 60;
        int hours = (int) millis;

        String time;
        DecimalFormat format = (DecimalFormat)NumberFormat.getInstance(Locale.US);
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

    public static Bitmap scaleDownBitmap(Context context, Bitmap bitmap, int width) {
        /*
         * This method can lead to OutOfMemoryError!
         * If the source size is more than twice the target size use
         * the optimized version available in AudioUtil::readCoverBitmap
         */
        if (bitmap != null) {
            final float densityMultiplier = context.getResources().getDisplayMetrics().density;
            int w = (int) (width * densityMultiplier);
            int h = (int) (w * bitmap.getHeight() / ((double) bitmap.getWidth()));
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
        return bitmap;
    }

    public static Bitmap cropBorders(Bitmap bitmap, int width, int height)
    {
        int top = 0;
        for (int i = 0; i < height / 2; i++) {
            int pixel1 = bitmap.getPixel(width / 2, i);
            int pixel2 = bitmap.getPixel(width / 2, height - i - 1);
            if ((pixel1 == 0 || pixel1 == -16777216) &&
                (pixel2 == 0 || pixel2 == -16777216)) {
                top = i;
            } else {
                break;
            }
        }

        int left = 0;
        for (int i = 0; i < width / 2; i++) {
            int pixel1 = bitmap.getPixel(i, height / 2);
            int pixel2 = bitmap.getPixel(width - i - 1, height / 2);
            if ((pixel1 == 0 || pixel1 == -16777216) &&
                (pixel2 == 0 || pixel2 == -16777216)) {
                left = i;
            } else {
                break;
            }
        }

        if (left >= width / 2 - 10 || top >= height / 2 - 10)
            return bitmap;

        // Cut off the transparency on the borders
        return Bitmap.createBitmap(bitmap, left, top,
                (width - (2 * left)), (height - (2 * top)));
    }

    public static Bitmap getPictureFromCache(Media media)
    {
        // mPicture is not null only if passed through
        // the ctor which is deprecated by now.
        Bitmap b = media.getPicture();
        if(b == null) {
            BitmapCache cache = BitmapCache.getInstance();
            Bitmap picture = cache.getBitmapFromMemCache(media.getLocation());
            if(picture == null) {
                /* Not in memcache:
                 * serving the file from the database and
                 * adding it to the memcache for later use.
                 */
                Context c = VLCApplication.getAppContext();
                picture = MediaDatabase.getInstance(c).getPicture(c, media.getLocation());
                cache.addBitmapToMemCache(media.getLocation(), picture);
            }
            return picture;
        } else {
            return b;
        }
    }

    public static void setPicture(Context context, Media m, Bitmap p) {
        Log.d(TAG, "Setting new picture for " + m.getTitle());
        try {
            MediaDatabase.getInstance(context).updateMedia(
                m.getLocation(),
                MediaDatabase.mediaColumn.MEDIA_PICTURE,
                p);
        } catch (SQLiteFullException e) {
            Log.d(TAG, "SQLiteFullException while setting picture");
        }
        m.setPictureParsed(true);
    }

    public static String getValue(String string, int defaultId)
    {
        return (string != null && string.length() > 0) ?
                string : VLCApplication.getAppContext().getString(defaultId);
    }

    public static int convertPxToDp(int px) {
        WindowManager wm = (WindowManager)VLCApplication.getAppContext().
                getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        float logicalDensity = metrics.density;
        int dp = Math.round(px / logicalDensity);
        return dp;
    }

    public static int convertDpToPx(int dp) {
        return Math.round(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                VLCApplication.getAppResources().getDisplayMetrics())
                         );
    }

    public static boolean hasExternalStorage() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    public static boolean hasNavBar()
    {
        return hasNavBar;
    }

    /** hasCombBar test if device has Combined Bar : only for tablet with Honeycomb or ICS */
    public static boolean hasCombBar() {
        return (!isPhone()
                && ((android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) &&
                    (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.JELLY_BEAN)));
    }

    public static boolean isPhone(){
        TelephonyManager manager = (TelephonyManager)VLCApplication.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
        if(manager.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE){
            return false;
        }else{
            return true;
        }
    }

    private static boolean StartsWith(String[] array, String text) {
        for (String item : array)
            if (text.startsWith(item))
                return true;
        return false;
    }

    public static String[] getStorageDirectories() {
        String[] dirs = null;
        BufferedReader bufReader = null;
        ArrayList<String> list = new ArrayList<String>();
        list.add(Environment.getExternalStorageDirectory().getPath());

        List<String> typeWL = Arrays.asList("vfat", "exfat", "sdcardfs", "fuse");
        List<String> typeBL = Arrays.asList("tmpfs");
        String[] mountWL = { "/mnt", "/Removable" };
        String[] mountBL = {
                "/mnt/secure",
                "/mnt/shell",
                "/mnt/asec",
                "/mnt/obb",
                "/mnt/media_rw/extSdCard",
                "/mnt/media_rw/sdcard",
                "/storage/emulated" };
        String[] deviceWL = {
                "/dev/block/vold",
                "/dev/fuse",
                "/mnt/media_rw/extSdCard" };

        try {
            bufReader = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            while((line = bufReader.readLine()) != null) {

                StringTokenizer tokens = new StringTokenizer(line, " ");
                String device = tokens.nextToken();
                String mountpoint = tokens.nextToken();
                String type = tokens.nextToken();

                // skip if already in list or if type/mountpoint is blacklisted
                if (list.contains(mountpoint) || typeBL.contains(type) || StartsWith(mountBL, mountpoint))
                    continue;

                // check that device is in whitelist, and either type or mountpoint is in a whitelist
                if (StartsWith(deviceWL, device) && (typeWL.contains(type) || StartsWith(mountWL, mountpoint)))
                    list.add(mountpoint);
            }

            dirs = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                dirs[i] = list.get(i);
            }
        }
        catch (FileNotFoundException e) {}
        catch (IOException e) {}
        finally {
            if (bufReader != null) {
                try {
                    bufReader.close();
                }
                catch (IOException e) {}
            }
        }
        return dirs;
    }

    public static String[] getCustomDirectories() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());
        final String custom_paths = preferences.getString("custom_paths", "");
        if(custom_paths.equals(""))
            return new String[0];
        else
            return custom_paths.split(":");
    }

    public static String[] getMediaDirectories() {
        ArrayList<String> list = new ArrayList<String>();
        list.addAll(Arrays.asList(Util.getStorageDirectories()));
        list.addAll(Arrays.asList(Util.getCustomDirectories()));
        return list.toArray(new String[list.size()]);
    }

    public static void addCustomDirectory(String path) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());

        ArrayList<String> dirs = new ArrayList<String>(
                Arrays.asList(getCustomDirectories()));
        dirs.add(path);
        StringBuilder builder = new StringBuilder();
        builder.append(dirs.remove(0));
        for(String s : dirs) {
            builder.append(":");
            builder.append(s);
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("custom_paths", builder.toString());
        editor.commit();
    }

    public static void removeCustomDirectory(String path) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());
        if(!preferences.getString("custom_paths", "").contains(path))
            return;
        ArrayList<String> dirs = new ArrayList<String>(
                Arrays.asList(preferences.getString("custom_paths", "").split(
                        ":")));
        dirs.remove(path);
        String custom_path;
        if(dirs.size() > 0) {
            StringBuilder builder = new StringBuilder();
            builder.append(dirs.remove(0));
            for(String s : dirs) {
                builder.append(":");
                builder.append(s);
            }
            custom_path = builder.toString();
        } else { // don't do unneeded extra work
            custom_path = "";
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("custom_paths", custom_path);
        editor.commit();
    }

    /**
     * Get the formatted current playback speed in the form of 1.00x
     */
    public static String formatRateString(float rate) {
        return String.format(java.util.Locale.US, "%.2fx", rate);
    }

    /**
     * equals() with two strings where either could be null
     */
    public static boolean nullEquals(String s1, String s2) {
        return (s1 == null ? s2 == null : s1.equals(s2));
    }

    /**
     * Get a resource id from an attribute id.
     * @param context
     * @param attrId
     * @return the resource id
     */
    public static int getResourceFromAttribute(Context context, int attrId) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[] {attrId});
        int resId = a.getResourceId(0, 0);
        a.recycle();
        return resId;
    }
}
