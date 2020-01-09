/*
 * ************************************************************************
 *  LiveDataMap.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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

package org.videolan.tools.livedata

import androidx.lifecycle.MutableLiveData

class LiveDataMap<K, V> : MutableLiveData<MutableMap<K, V>>() {

    private val emptyMap = mutableMapOf<K, V>()

    override fun getValue(): MutableMap<K, V> {
        return super.getValue() ?: emptyMap
    }

    fun clear() {
        value = value.apply { clear() }
    }

    fun add(key: K, item: V) {
        value = value.apply { put(key, item) }
    }

    fun remove(key: K) {
        value = value.apply { remove(key) }
    }

    fun get(key: K): V? = value[key]
}
