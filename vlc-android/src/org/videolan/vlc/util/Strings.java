/*****************************************************************************
 * Strings.java
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

package org.videolan.vlc.util;

import org.videolan.vlc.BuildConfig;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class Strings {
    public final static String TAG = "VLC/UiTools/Strings";

    public static String stripTrailingSlash(String s) {
        if( s.endsWith("/") && s.length() > 1 )
            return s.substring(0, s.length() - 1);
        return s;
    }

    static boolean startsWith(String[] array, String text) {
        for (String item : array)
            if (text.startsWith(item))
                return true;
        return false;
    }

    static int containsName(List<String> array, String text) {
        for (int i = array.size()-1 ; i >= 0 ; --i)
            if (array.get(i).endsWith(text))
                return i;
        return -1;
    }

    /**
     * Convert time to a string
     * @param millis e.g.time/length from file
     * @return formated string (hh:)mm:ss
     */
    public static String millisToString(long millis)
    {
        return Strings.millisToString(millis, false);
    }

    /**
     * Convert time to a string
     * @param millis e.g.time/length from file
     * @return formated string "[hh]h[mm]min" / "[mm]min[s]s"
     */
    public static String millisToText(long millis)
    {
        return Strings.millisToString(millis, true);
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

    /**
     * equals() with two strings where either could be null
     */
    public static boolean nullEquals(String s1, String s2) {
        return (s1 == null ? s2 == null : s1.equals(s2));
    }

    /**
     * Get the formatted current playback speed in the form of 1.00x
     */
    public static String formatRateString(float rate) {
        return String.format(java.util.Locale.US, "%.2fx", rate);
    }

    public static String readableFileSize(long size) {
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "KiB", "MiB", "GiB", "TiB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String readableSize(long size) {
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1000));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1000, digitGroups)) + " " + units[digitGroups];
    }

    public static String removeFileProtocole(String path){
        if (path == null)
            return null;
        if (path.startsWith("file://"))
            return path.substring(7);
        else
            return path;
    }

    public static String buildPkgString(String string) {
        return BuildConfig.APPLICATION_ID + "." + string;
    }
}
