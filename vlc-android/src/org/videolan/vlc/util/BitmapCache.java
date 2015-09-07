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

package org.videolan.vlc.util;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.util.LruCache;
import android.util.Log;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.VLCApplication;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class BitmapCache {

    public final static String TAG = "VLC/BitmapCache";
    private final static boolean LOG_ENABLED = false;

    private static BitmapCache mInstance;
    private final LruCache<String, SoftReference<Bitmap>> mMemCache;
    Set<SoftReference<Bitmap>> mCachedBitmaps;
    Set<SoftReference<Bitmap>> mReusableBitmaps;

    public static BitmapCache getInstance() {
        if (mInstance == null)
            mInstance = new BitmapCache();
        return mInstance;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private BitmapCache() {

        // Get memory class of this device, exceeding this amount will throw an
        // OutOfMemory exception.
        final ActivityManager am = ((ActivityManager) VLCApplication.getAppContext().getSystemService(
                Context.ACTIVITY_SERVICE));
        final int memClass = AndroidUtil.isHoneycombOrLater() ? am.getLargeMemoryClass() : am.getMemoryClass();

        // Use 1/5th of the available memory for this memory cache.
        final int cacheSize = 1024 * 1024 * memClass / 5;

        Log.i(TAG, "LRUCache size set to " + cacheSize);

        mMemCache = new LruCache<String, SoftReference<Bitmap>>(cacheSize) {

            @Override
            protected int sizeOf(String key, SoftReference<Bitmap> value) {
                if (value.get() == null)
                    return 0;
                return value.get().getRowBytes() * value.get().getHeight();
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, SoftReference<Bitmap> oldValue, SoftReference<Bitmap> newValue) {
                if (evicted) {
                    mCachedBitmaps.remove(oldValue);
                    if (oldValue.get() != null)
                        addReusableBitmapRef(oldValue);
                }
            }
        };

        if (AndroidUtil.isHoneycombOrLater())
            mReusableBitmaps = Collections.synchronizedSet(new HashSet<SoftReference<Bitmap>>());
            mCachedBitmaps = Collections.synchronizedSet(new HashSet<SoftReference<Bitmap>>());
    }

    public Bitmap getBitmapFromMemCache(String key) {
        final SoftReference<Bitmap> ref = mMemCache.get(key);
        if (ref == null)
            return null;
        Bitmap b = ref.get();
        if (b == null){
            mMemCache.remove(key);
            mCachedBitmaps.remove(ref);
            return null;
        }
        if (b.isRecycled()) {
            /* A recycled bitmap cannot be used again */
            addReusableBitmapRef(ref);
            mCachedBitmaps.remove(ref);
            mMemCache.remove(key);
            b = null;
        }
        if (LOG_ENABLED)
            Log.d(TAG, (b == null) ? "Cache miss" : "Cache found");
        return b;
    }

    public void addBitmapToMemCache(String key, Bitmap bitmap) {
        if (key != null && bitmap != null && getBitmapFromMemCache(key) == null) {
            final SoftReference<Bitmap> ref =new SoftReference<Bitmap>(bitmap);
            mMemCache.put(key, ref);
            mCachedBitmaps.add(ref);
        }
    }

    private Bitmap getBitmapFromMemCache(int resId) {
        return getBitmapFromMemCache("res:" + resId);
    }

    private void addBitmapToMemCache(int resId, Bitmap bitmap) {
        addBitmapToMemCache("res:" + resId, bitmap);
    }

    public void clear() {
        mMemCache.evictAll();
        mCachedBitmaps.clear();
    }

    public static Bitmap getFromResource(Resources res, int resId) {
        BitmapCache cache = BitmapCache.getInstance();
        Bitmap bitmap = cache.getBitmapFromMemCache(resId);
        if (bitmap == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            BitmapUtil.setInBitmap(options);
            if (AndroidUtil.isHoneycombOrLater())
                options.inMutable = true;
            bitmap = BitmapFactory.decodeResource(res, resId, options);
            cache.addBitmapToMemCache(resId, bitmap);
        }
        return bitmap;
    }

    public synchronized void addReusableBitmapRef(SoftReference<Bitmap> ref){
        mReusableBitmaps.add(ref);
    }

    public synchronized Bitmap getReusableBitmap(BitmapFactory.Options targetOptions){
        if (mReusableBitmaps == null || mReusableBitmaps.isEmpty())
            return null;
        Bitmap reusable = null;
        LinkedList<SoftReference<Bitmap>> itemsToRemove = new LinkedList<SoftReference<Bitmap>>();
        for(SoftReference<Bitmap> b : mReusableBitmaps){
            reusable = b.get();
            if (reusable == null) {
                itemsToRemove.add(b);
                continue;
            }
//            if (!reusable.isRecycled()) {
//                Log.d(TAG, "not recycled");
//                itemsToRemove.add(b);
//                continue;
//            }
            if (BitmapUtil.canUseForInBitmap(reusable, targetOptions)) {
                itemsToRemove.add(b);
                return reusable;
            }
        }
        if (!itemsToRemove.isEmpty())
            mReusableBitmaps.removeAll(itemsToRemove);
        return null;
    }
}
