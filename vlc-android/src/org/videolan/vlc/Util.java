/*****************************************************************************
 * Util.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
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
        hasNavBar = isICSOrLater() && !devicesWithoutNavBar.contains(android.os.Build.MODEL);
    }

    /** Print an on-screen message to alert the user */
    public static void toaster(Context context, int stringId, int duration) {
        Toast.makeText(context, stringId, duration).show();
    }

    public static void toaster(Context context, int stringId) {
        toaster(context, stringId, Toast.LENGTH_SHORT);
    }

    public static File URItoFile(String URI) {
        return new File(Uri.decode(URI).replace("file://",""));
    }

    public static String URItoFileName(String URI) {
        return URItoFile(URI).getName();
    }

    public static String PathToURI(String path) {
        if(path == null) {
            throw new NullPointerException("Cannot convert null path!");
        }
        return LibVLC.nativeToURI(path);
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
     * Convert time to a string
     * @param millis e.g.time/length from file
     * @return formated string (hh:)mm:ss
     */
    public static String millisToString(long millis) {
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
        if (millis > 0) {
            time = (negative ? "-" : "") + hours + ":" + format.format(min) + ":" + format.format(sec);
        } else {
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

    public static String getValue(String string, int defaultId)
    {
        return (string != null && string.length() > 0) ?
                string : VLCApplication.getAppContext().getString(defaultId);
    }

    public static void setItemBackground(View v, int position) {
        v.setBackgroundResource(position % 2 == 0
                ? R.drawable.background_item1
                : R.drawable.background_item2);
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

    public static boolean isFroyoOrLater()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO;
    }

    public static boolean isGingerbreadOrLater()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD;
    }

    public static boolean isHoneycombOrLater()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean isICSOrLater()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    public static boolean isJellyBeanOrLater()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN;
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

    private static String errorMsg = null;
    private static boolean isCompatible = false;
    public static String getErrorMsg() {
        return errorMsg;
    }
    public static boolean hasCompatibleCPU()
    {
        // If already checked return cached result
        if(errorMsg != null) return isCompatible;

        if(VLCApplication.getAppResources() == null) {
            Log.e(TAG, "WARNING: Unable to get app resources; cannot check device ABI!");
            Log.e(TAG, "WARNING: Cannot guarantee correct ABI for this build (may crash)!");
            return true;
        }

        Properties properties = new Properties();
        try {
            properties.load(new ByteArrayInputStream(Util.readAsset("env.txt", "").getBytes("UTF-8")));
        } catch (IOException e) {
            // Shouldn't happen if done correctly
            e.printStackTrace();
            errorMsg = "IOException whilst reading compile flags";
            isCompatible = false;
            return false;
        }

        String CPU_ABI = android.os.Build.CPU_ABI;
        String CPU_ABI2 = "none";
        if(android.os.Build.VERSION.SDK_INT >= 8) { // CPU_ABI2 since 2.2
            try {
                CPU_ABI2 = (String)android.os.Build.class.getDeclaredField("CPU_ABI2").get(null);
            } catch (Exception e) { }
        }

        String ANDROID_ABI = properties.getProperty("ANDROID_ABI");
        boolean NO_FPU = properties.getProperty("NO_FPU","0").equals("1");
        boolean NO_ARMV6 = properties.getProperty("NO_ARMV6","0").equals("1");
        boolean hasNeon = false, hasFpu = false, hasArmV6 = false,
                hasArmV7 = false, hasMips = false;
        boolean hasX86 = false;

        if(CPU_ABI.equals("x86")) {
            hasX86 = true;
        } else if(CPU_ABI.equals("armeabi-v7a") ||
                  CPU_ABI2.equals("armeabi-v7a")) {
            hasArmV7 = true;
            hasArmV6 = true; /* Armv7 is backwards compatible to < v6 */
        } else if(CPU_ABI.equals("armeabi") ||
                  CPU_ABI2.equals("armeabi")) {
            hasArmV6 = true;
        }

        try {
            FileReader fileReader = new FileReader("/proc/cpuinfo");
            BufferedReader br = new BufferedReader(fileReader);
            String line;
            while((line = br.readLine()) != null) {
                if(!hasArmV7 && line.contains("ARMv7")) {
                    hasArmV7 = true;
                    hasArmV6 = true; /* Armv7 is backwards compatible to < v6 */
                }
                if(!hasArmV7 && !hasArmV6 && line.contains("ARMv6"))
                    hasArmV6 = true;
                // "clflush size" is a x86-specific cpuinfo tag.
                // (see kernel sources arch/x86/kernel/cpu/proc.c)
                if(line.contains("clflush size"))
                    hasX86 = true;
                // "microsecond timers" is specific to MIPS.
                // see arch/mips/kernel/proc.c
                if(line.contains("microsecond timers"))
                    hasMips = true;
                if(!hasNeon && line.contains("neon"))
                    hasNeon = true;
                if(!hasFpu && line.contains("vfp"))
                    hasFpu = true;
            }
            fileReader.close();
        } catch(IOException ex){
            ex.printStackTrace();
            errorMsg = "IOException whilst reading cpuinfo flags";
            isCompatible = false;
            return false;
        }

        // Enforce proper architecture to prevent problems
        if(ANDROID_ABI.equals("x86") && !hasX86) {
            errorMsg = "x86 build on non-x86 device";
            isCompatible = false;
            return false;
        } else if(hasX86 && ANDROID_ABI.contains("armeabi")) {
            errorMsg = "ARM build on x86 device";
            isCompatible = false;
            return false;
        }

        if(ANDROID_ABI.equals("mips") && !hasMips) {
            errorMsg = "MIPS build on non-MIPS device";
            isCompatible = false;
            return false;
        } else if(hasMips && ANDROID_ABI.contains("armeabi")) {
            errorMsg = "ARM build on MIPS device";
            isCompatible = false;
            return false;
        }

        if(ANDROID_ABI.equals("armeabi-v7a") && !hasArmV7) {
            errorMsg = "ARMv7 build on non-ARMv7 device";
            isCompatible = false;
            return false;
        }
        if(ANDROID_ABI.equals("armeabi")) {
            if(!NO_ARMV6 && !hasArmV6) {
                errorMsg = "ARMv6 build on non-ARMv6 device";
                isCompatible = false;
                return false;
            } else if(!NO_FPU && !hasFpu) {
                errorMsg = "FPU-enabled build on non-FPU device";
                isCompatible = false;
                return false;
            }
        }
        if(ANDROID_ABI.equals("armeabi") || ANDROID_ABI.equals("armeabi-v7a")) {
            if(!hasNeon) {
                errorMsg = "NEON build on non-NEON device";
            }
        }
        errorMsg = null;
        isCompatible = true;
        return true;
    }

    public static boolean isPhone(){
        TelephonyManager manager = (TelephonyManager)VLCApplication.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
        if(manager.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE){
            return false;
        }else{
            return true;
        }
    }

    public static String[] getStorageDirectories()
    {
        String[] dirs = null;
        BufferedReader bufReader = null;
        try {
            bufReader = new BufferedReader(new FileReader("/proc/mounts"));
            ArrayList<String> list = new ArrayList<String>();
            list.add(Environment.getExternalStorageDirectory().getPath());
            String line;
            while((line = bufReader.readLine()) != null) {
                if(line.contains("vfat") || line.contains("exfat") ||
                   line.contains("/mnt") || line.contains("/Removable")) {
                    StringTokenizer tokens = new StringTokenizer(line, " ");
                    String s = tokens.nextToken();
                    s = tokens.nextToken(); // Take the second token, i.e. mount point

                    if (list.contains(s))
                        continue;

                    if (line.contains("/dev/block/vold")) {
                        if (!line.startsWith("tmpfs") &&
                            !line.startsWith("/dev/mapper") &&
                            !s.startsWith("/mnt/secure") &&
                            !s.startsWith("/mnt/shell") &&
                            !s.startsWith("/mnt/asec") &&
                            !s.startsWith("/mnt/obb")
                            ) {
                            list.add(s);
                        }
                    }
                }
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
}
