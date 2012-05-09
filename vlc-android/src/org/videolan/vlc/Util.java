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

import java.io.File;
import java.lang.reflect.Field;
import java.text.DecimalFormat;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

public class Util {
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

    private static int apiLevel = 0;

    /**
     * Returns the current Android SDK version
     * This function is called by the native code.
     * This is used to know if we should use the native audio output,
     * or the amem as a fallback.
     */
    public static int getApiLevel() {
        if (apiLevel > 0)
            return apiLevel;
        if (android.os.Build.VERSION.SDK.equalsIgnoreCase("3")) {
            apiLevel = 3;
        } else {
            try {
                final Field f = android.os.Build.VERSION.class.getDeclaredField("SDK_INT");
                apiLevel = (Integer) f.get(null);
            } catch (final Exception e) {
                return 0;
            }
        }
        return apiLevel;
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

    public static String getValue(Context context, String string, int defaultId)
    {
        return (string != null && string.length() > 0) ? string : context.getString(defaultId);
    }

    public static void setItemBackground(View v, int position) {
        v.setBackgroundResource(position % 2 == 0
                ? R.drawable.background_item1
                : R.drawable.background_item2);
    }

    public static boolean isGingerbread()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD;
    }

    public static boolean isHoneycomb()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean isICS()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }
}
