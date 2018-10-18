/*****************************************************************************
 * BitmapCache.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.helpers;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import androidx.collection.LruCache;
import android.util.Log;

import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.util.Strings;

public class BitmapCache {

    private final static String TAG = "VLC/BitmapCache";

    private static BitmapCache mInstance;
    private final LruCache<String, Bitmap> mMemCache;

    public synchronized static BitmapCache getInstance() {
        if (mInstance == null)
            mInstance = new BitmapCache();
        return mInstance;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private BitmapCache() {

        // Use 20% of the available memory for this memory cache.
        final long cacheSize = Runtime.getRuntime().maxMemory() / 5;

        if (BuildConfig.DEBUG)
            Log.i(TAG, "LRUCache size set to " +  Strings.readableSize(cacheSize));

        mMemCache = new LruCache<String, Bitmap>((int) cacheSize) {

            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };
    }

    public synchronized Bitmap getBitmapFromMemCache(String key) {
        final Bitmap b = mMemCache.get(key);
        if (b == null){
            mMemCache.remove(key);
            return null;
        }
        return b;
    }

    public synchronized void addBitmapToMemCache(String key, Bitmap bitmap) {
        if (key != null && bitmap != null && getBitmapFromMemCache(key) == null) {
            mMemCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromMemCache(int resId) {
        return getBitmapFromMemCache("res:" + resId);
    }

    private void addBitmapToMemCache(int resId, Bitmap bitmap) {
        addBitmapToMemCache("res:" + resId, bitmap);
    }

    public synchronized void clear() {
        mMemCache.evictAll();
    }

    public static Bitmap getFromResource(Resources res, int resId) {
        BitmapCache cache = BitmapCache.getInstance();
        Bitmap bitmap = cache.getBitmapFromMemCache(resId);
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeResource(res, resId);
            cache.addBitmapToMemCache(resId, bitmap);
        }
        return bitmap;
    }
}
