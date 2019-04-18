/*****************************************************************************
 * BitmapCache.java
 *
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
 */

package org.videolan.vlc.gui.helpers

import android.annotation.TargetApi
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.collection.LruCache
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.util.readableSize

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
object BitmapCache {
    private val mMemCache: LruCache<String, Bitmap>
    private val TAG = "VLC/BitmapCache"

    init {

        // Use 20% of the available memory for this memory cache.
        val cacheSize = Runtime.getRuntime().maxMemory() / 5

        if (BuildConfig.DEBUG)
            Log.i(TAG, "LRUCache size set to " + cacheSize.readableSize())

        mMemCache = object : LruCache<String, Bitmap>(cacheSize.toInt()) {

            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.rowBytes * value.height
            }
        }
    }

    @Synchronized
    fun getBitmapFromMemCache(key: String?): Bitmap? {
        if (key == null) return null
        val b = mMemCache.get(key)
        if (b == null) {
            mMemCache.remove(key)
            return null
        }
        return b
    }

    @Synchronized
    fun addBitmapToMemCache(key: String?, bitmap: Bitmap?) {
        if (key != null && bitmap != null && getBitmapFromMemCache(key) == null) {
            mMemCache.put(key, bitmap)
        }
    }

    private fun getBitmapFromMemCache(resId: Int): Bitmap? {
        return getBitmapFromMemCache("res:$resId")
    }

    private fun addBitmapToMemCache(resId: Int, bitmap: Bitmap?) {
        addBitmapToMemCache("res:$resId", bitmap)
    }

    @Synchronized
    fun clear() {
        mMemCache.evictAll()
    }


    fun getFromResource(res: Resources, resId: Int): Bitmap? {
        var bitmap = getBitmapFromMemCache(resId)
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeResource(res, resId)
            addBitmapToMemCache(resId, bitmap)
        }
        return bitmap
    }
}
