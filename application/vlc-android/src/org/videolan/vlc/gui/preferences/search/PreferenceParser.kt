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
import android.content.ContextWrapper
import android.content.res.XmlResourceParser
import android.os.Parcelable
import android.util.Log
import kotlinx.android.parcel.Parcelize
import org.videolan.tools.Settings
import org.videolan.tools.wrap
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R

object PreferenceParser {

    fun parsePreferences(context: Context): ArrayList<PreferenceItem> {
        val result = ArrayList<PreferenceItem>()
        arrayOf(R.xml.preferences, R.xml.preferences_adv, R.xml.preferences_audio, R.xml.preferences_casting, R.xml.preferences_perf, R.xml.preferences_subtitles, R.xml.preferences_ui, R.xml.preferences_video).forEach {
            result.addAll(parsePreferences(context, it))
        }
        return result
    }

    private fun parsePreferences(context: Context, id: Int): ArrayList<PreferenceItem> {
        var category = ""
        var categoryEng = ""
        val result = ArrayList<PreferenceItem>()
        val parser = context.resources.getXml(id)
        var eventType = -1
        val namespace = "http://schemas.android.com/apk/res/android"
        var firstPrefScreeFound = false
        val englishContext = ContextWrapper(context).wrap("en")
        while (eventType != XmlResourceParser.END_DOCUMENT) {
            if (eventType == XmlResourceParser.START_TAG) {
                val element = parser.name

                if (element != "PreferenceScreen" && !firstPrefScreeFound) {
                    firstPrefScreeFound = true
                    category = getValue(context, parser, namespace, "title")
                    categoryEng = getValue(englishContext, parser, namespace, "title")
                }
                if (element != "PreferenceCategory" && element != "Preference") {
                    val key = getValue(context, parser, namespace, "key")
                    val title = getValue(context, parser, namespace, "title")
                    val titleEng = getValue(englishContext, parser, namespace, "title")
                    var summary = getValue(context, parser, namespace, "summary")
                    var summaryEng = getValue(englishContext, parser, namespace, "summary")
                    if (summary.contains("%s") && element == "ListPreference") {
                        //get the current value for the string substitution
                        try {
                            val defaultValue = getValue(context, parser, namespace, "defaultValue")
                            val rawValue = Settings.getInstance(context).getString(key, defaultValue) ?: ""
                            val entriesId = parser.getAttributeResourceValue(namespace, "entries", -1)
                            val entryValuesId = parser.getAttributeResourceValue(namespace, "entryValues", -1)
                            val index = context.resources.getStringArray(entryValuesId).indexOf(rawValue)
                            summary = summary.replace("%s", context.resources.getStringArray(entriesId)[index])
                            summaryEng = summaryEng.replace("%s", englishContext.resources.getStringArray(entriesId)[index])
                        } catch (e: Exception) {
                        }
                    }
                    if (key.isNotBlank()) result.add(PreferenceItem(key, id, title, summary, titleEng, summaryEng, category, categoryEng))
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
                parser.getAttributeValue(namespace, node)
            } else {
                context.resources.getString(titleResId)
            }
        } catch (e: Exception) {
        }
        return ""
    }

    private fun getSummary (context: Context, parser: XmlResourceParser, namespace: String, node: String, defaultValue:String, key:String):String {

        val value = getValue(context, parser, namespace, node)
        if (value.contains("%s")) if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Found string replacement for $key")
        return if (value.contains("%s"))
            value.replace("%s", Settings.getInstance(context).getString(key, defaultValue) ?: "")
        else value

    }
}

@Parcelize
data class PreferenceItem(val key: String, val parentScreen: Int, val title: String, val summary: String, val titleEng:String, val summaryEng: String, val category: String, val categoryEng: String) : Parcelable