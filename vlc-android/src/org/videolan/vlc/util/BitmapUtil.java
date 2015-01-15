/*****************************************************************************
 * BitmapUtil.java
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

import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.VLCApplication;

import android.content.Context;
import android.graphics.Bitmap;

public class BitmapUtil {
    public final static String TAG = "VLC/Util/BitmapUtil";

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

    public static Bitmap getPictureFromCache(MediaWrapper media)
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
                picture = MediaDatabase.getInstance().getPicture(c, media.getLocation());
                cache.addBitmapToMemCache(media.getLocation(), picture);
            }
            return picture;
        } else {
            return b;
        }
    }
}
