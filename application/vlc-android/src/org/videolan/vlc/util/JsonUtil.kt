/*
 * ************************************************************************
 *  JsonUtil.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package org.videolan.vlc.util

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.videolan.vlc.mediadb.models.EqualizerWithBands

object JsonUtil {
    fun convertToJson(data: Any?): String {
        if (data == null) return "{}"
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter<Any>(data::class.java)
        return adapter.toJson(data)
    }

    fun convertToJson(data: EqualizerExport): String {
        val moshi = Moshi
            .Builder()
            .build()
        val type = Types.newParameterizedType(EqualizerExport::class.java, EqualizerWithBands::class.java, EqualizerState::class.java)
        val adapter = moshi.adapter<EqualizerExport>(type)
        return adapter.toJson(data)
    }

    inline fun <reified K, reified V> convertToJson(data: Map<K,V>?): String {
        val moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(MutableMap::class.java, K::class.java, V::class.java)
        val adapter = moshi.adapter<Map<K,V>>(type).nullSafe()
        return adapter.toJson(data)
    }

    inline fun <reified T> convertToJson(data: List<T>?): String {
        val moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(MutableList::class.java, Any::class.java)
        val adapter = moshi.adapter<List<T>>(type).nullSafe()
        return adapter.toJson(data)
    }

    fun getEqualizerFromJson(string: String): EqualizerWithBands? {
        val moshi: Moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(EqualizerWithBands::class.java)

        val adapter: JsonAdapter<EqualizerWithBands> = moshi.adapter(type)
        adapter.fromJson(string)?.let {
            return it
        }
        return null
    }

    /**
     * Get equalizers export from json
     *
     * @param string the json string
     * @return the equalizers export
     */
    fun getEqualizersFromJson(string: String): EqualizerExport {
        val moshi = Moshi
            .Builder()
            .build()
        val type = Types.newParameterizedType(EqualizerExport::class.java, EqualizerWithBands::class.java, EqualizerState::class.java)
        val adapter = moshi.adapter<EqualizerExport>(type)
        adapter.fromJson(string)?.let {
            return it
        }
        throw IllegalStateException("Invalid json")
    }
}

