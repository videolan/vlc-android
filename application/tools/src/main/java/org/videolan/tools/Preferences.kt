/*****************************************************************************
 * Preferences.java
 *
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
 */

package org.videolan.tools

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor

import org.json.JSONArray
import org.json.JSONException

object Preferences {
    const val TAG = "VLC/UiTools/Preferences"

    fun getFloatArray(pref: SharedPreferences, key: String): FloatArray? {
        var array: FloatArray? = null
        val s = pref.getString(key, null)
        if (s != null) {
            try {
                val json = JSONArray(s)
                array = FloatArray(json.length())
                for (i in array.indices)
                    array[i] = json.getDouble(i).toFloat()
            } catch (e: JSONException) {
                e.printStackTrace()
            }

        }
        return array
    }

    fun putFloatArray(editor: Editor, key: String, array: FloatArray) {
        try {
            val json = JSONArray()
            for (f in array)
                json.put(f.toDouble())
            editor.putString(key, json.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }

}
