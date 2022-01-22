/*
 * ************************************************************************
 * DrawableCache.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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
 * **************************************************************************
 *
 *
 */
package org.videolan.tools

import android.content.Context
import android.util.LruCache
import androidx.annotation.DrawableRes

/**
 * Cache results from {@link android.content.res.Resources#getIdentifier} which uses reflection to
 * perform lookups by string name. Adjust the max-size as application use increases.
 */
object DrawableCache {
    private const val TAG = "VLC/DrawableCache"
    private val memCache = LruCache<String, Int>(8)

    fun getDrawableFromMemCache(ctx: Context, name: String, @DrawableRes defaultDrawable: Int): Int {
        return getOrPutDrawable(name) { ctx.resources.getDrawableOrDefault(name, ctx.packageName, defaultDrawable) }
    }

    @Synchronized
    private fun getOrPutDrawable(key: String, defaultValue: () -> Int): Int {
        val value = memCache.get(key)
        return if (value == null) {
            val answer = defaultValue()
            memCache.put(key, defaultValue())
            answer
        } else {
            value
        }
    }

    @Synchronized
    fun clear() {
        memCache.evictAll()
    }
}
