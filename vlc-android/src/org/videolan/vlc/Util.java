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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.Properties;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class Util {
    public final static String TAG = "VLC/Util";
    /** A set of utility functions for the VLC application */

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
        int sep = URI.lastIndexOf('/');
        int dot = URI.lastIndexOf('.');
        return Uri.decode(URI.substring(sep + 1, dot));
    }

    public static String PathToURI(String path) {
        String URI;
        try {
            URI = LibVLC.getInstance().nativeToURI(path);
        } catch (LibVlcException e) {
            URI = "";
        }
        return URI;
    }

    public static String stripTrailingSlash(String _s) {
        String s = _s;
        if( s.endsWith("/") && s.length() > 1 )
            s = s.substring(0,s.length()-1);
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
        DecimalFormat format = new DecimalFormat("00");
        if (millis > 0) {
            time = (negative ? "-" : "") + hours + ":" + format.format(min) + ":" + format.format(sec);
        } else {
            time = (negative ? "-" : "") + min + ":" + format.format(sec);
        }
        return time;
    }

    public static Bitmap scaleDownBitmap(Context context, Bitmap bitmap, int width) {
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

    private static String errorMsg = null;
    private static boolean isCompatible = false;
    public static String getErrorMsg() {
        return errorMsg;
    }
    public static boolean hasCompatibleCPU()
    {
        // If already checked return cached result
        if(errorMsg != null) return isCompatible;

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

        String ANDROID_ABI = properties.getProperty("ANDROID_ABI");
        boolean NO_NEON = properties.getProperty("NO_NEON").equals("1");
        boolean NO_FPU = properties.getProperty("NO_FPU").equals("1");
        boolean NO_ARMV6 = properties.getProperty("NO_ARMV6").equals("1");
        boolean hasNeon = false, hasFpu = false, hasArmV6 = false, hasArmV7 = false;
        ProcessBuilder cmd;

        try {
            String[] args = {"/system/bin/cat", "/proc/cpuinfo"};
            cmd = new ProcessBuilder(args);

            Process process = cmd.start();
            InputStream in = process.getInputStream();
            byte[] re = new byte[1024];
            while(in.read(re) != -1){
                if(!hasNeon && new String(re).contains("neon"))
                    hasNeon = true;
                if(!hasArmV7 && new String(re).contains("ARMv7"))
                    hasArmV7 = true;
                if(!hasArmV7 && !hasArmV6 && new String(re).contains("ARMv6"))
                    hasArmV6 = true;
                if(!hasFpu && new String(re).contains("vfp"))
                    hasFpu = true;
            }
            in.close();
        } catch(IOException ex){
            ex.printStackTrace();
            errorMsg = "IOException whilst reading cpuinfo flags";
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
        if(!NO_NEON && !hasNeon) {
            errorMsg = "NEON build on non-NEON device";
            isCompatible = false;
            return false;
        }
        errorMsg = null;
        isCompatible = true;
        return true;
    }
}
