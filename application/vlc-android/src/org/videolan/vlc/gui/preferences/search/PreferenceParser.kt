/*
 * ************************************************************************
 *  PreferenceParser.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.preferences

import android.content.Context
import android.content.res.XmlResourceParser
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.videolan.vlc.R

object PreferenceParser {

    fun parsePreferences(context: Context): ArrayList<PreferenceItem> {
        val result = ArrayList<PreferenceItem>()
        arrayOf(R.xml.preferences, R.xml.preferences_adv, R.xml.preferences_audio, R.xml.preferences_casting, R.xml.preferences_dev, R.xml.preferences_perf, R.xml.preferences_subtitles, R.xml.preferences_ui, R.xml.preferences_video).forEach {
            result.addAll(parsePreferences(context, it))
        }
        return result
    }

    private fun parsePreferences(context: Context, id: Int): ArrayList<PreferenceItem> {
        var category = ""
        val result = ArrayList<PreferenceItem>()
        val parser = context.resources.getXml(id)
        var eventType = -1
        val namespace = "http://schemas.android.com/apk/res/android"
        var firstPrefScreeFound = false
        while (eventType != XmlResourceParser.END_DOCUMENT) {
            if (eventType == XmlResourceParser.START_TAG) {
                val element = parser.name

                if (element != "PreferenceScreen" && !firstPrefScreeFound) {
                    firstPrefScreeFound = true
                    category = getValue(context, parser, namespace, "title")
                }
                if (element != "PreferenceCategory" && element != "Preference") {
                    val title = getValue(context, parser, namespace, "title")
                    val summary = getValue(context, parser, namespace, "summary")
                    val key = getValue(context, parser, namespace, "key")
                    if (key != null) result.add(PreferenceItem(key, id, title, summary, category))
                }
            }
            eventType = parser.next()
        }
        return result
    }

    private fun getValue(context: Context, parser: XmlResourceParser, namespace: String, node: String): String {
        try {
            val titleResId = parser.getAttributeResourceValue(namespace, node, -1)
            return if (titleResId == -1) {
                // Read just a string.
                parser.getAttributeValue(namespace, node)
            } else {
                // Get a string with a resource id
                context.resources.getString(titleResId)
            }
        } catch (e: Exception) {
        }
        return ""
    }
}

@Parcelize
data class PreferenceItem(val key: String, val parentScreen: Int, val title: String, val summary: String, val category: String) : Parcelable