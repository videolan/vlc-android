/*****************************************************************************
 * AndroidUtil.java
 *****************************************************************************
 * Copyright © 2015 VLC authors, VideoLAN and VideoLabs
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.libvlc.util;

import android.net.Uri;
import android.os.Build;

import java.io.File;

public class AndroidUtil {

    public static final boolean isOOrLater = android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    public static final boolean isNougatOrLater = isOOrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    public static final boolean isMarshMallowOrLater = isNougatOrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    public static final boolean isLolliPopOrLater = isMarshMallowOrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    public static final boolean isKitKatOrLater = isLolliPopOrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    public static final boolean isJellyBeanMR2OrLater = isKitKatOrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    public static final boolean isJellyBeanMR1OrLater = isJellyBeanMR2OrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    public static final boolean isJellyBeanOrLater = isJellyBeanMR1OrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    public static final boolean isICSOrLater = isJellyBeanOrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    public static final boolean isHoneycombMr2OrLater = isICSOrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2;
    public static final boolean isHoneycombMr1OrLater = isHoneycombMr2OrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
    public static final boolean isHoneycombOrLater = isHoneycombMr1OrLater || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB;

    public static File UriToFile(Uri uri) {
        return new File(uri.getPath().replaceFirst("file://", ""));
    }

    /**
     * Quickly converts path to URIs, which are mandatory in libVLC.
     *
     * @param path The path to be converted.
     * @return A URI representation of path
     */
    public static Uri PathToUri(String path) {
        return Uri.fromFile(new File(path));
    }

    public static Uri LocationToUri(String location) {
        Uri uri = Uri.parse(location);
        if (uri.getScheme() == null)
            throw new IllegalArgumentException("location has no scheme");
        return uri;
    }

    public static Uri FileToUri(File file) {
        return Uri.fromFile(file);
    }
}