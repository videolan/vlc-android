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

package org.videolan.vlc.android;

import java.lang.reflect.Field;
import java.text.DecimalFormat;

import android.content.Context;
import android.graphics.Bitmap;
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

    public static String getValue(Context context, String string, int defaultId)
    {
        return (string != null && string.length() > 0) ? string : context.getString(defaultId);
    }
}
