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

package org.videolan.vlc.gui.preferences.search

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.XmlResourceParser
import android.os.Parcelable
import androidx.annotation.XmlRes
import kotlinx.parcelize.Parcelize
import org.videolan.tools.Settings
import org.videolan.tools.wrap
import org.videolan.vlc.R

object PreferenceParser {

    /**
     * Parses all the preferences available in the app.
     * @param context the context to be used to retrieve the preferences
     *
     * @return a list of [PreferenceItem]
     */
    fun parsePreferences(context: Context): ArrayList<PreferenceItem> {
        val result = ArrayList<PreferenceItem>()
        arrayOf(R.xml.preferences, R.xml.preferences_adv, R.xml.preferences_audio, R.xml.preferences_casting, R.xml.preferences_subtitles, R.xml.preferences_ui, R.xml.preferences_video, R.xml.preferences_remote_access).forEach {
            result.addAll(parsePreferences(context, it))
        }
        return result
    }

    /**
     * Compares the preference list with the set settings to get the list of the changed settings by the user
     * @param context the context to be used to retrieve the preferences
     *
     * @return a list of changed settings in the form a of pair of the key and the value
     */
    private fun getAllChangedPrefs(context: Context): ArrayList<Pair<String, Any>> {
        val allPrefs = parsePreferences(context)
        val allSettings = Settings.getInstance(context).all
        val changedSettings = ArrayList<Pair<String, Any>>()
        allPrefs.forEach { pref ->
            allSettings.forEach { setting ->
                if (pref.key == setting.key) {
                    setting.value?.let {
                        if (!isSame(it, pref.defaultValue)) changedSettings.add(Pair(pref.key, it))
                    }
                }
            }
        }
        return changedSettings
    }

    /**
     * Compares a [SharedPreferences] item value to a retrieved String from the preference parsing
     * @param settingValue the found preference value
     * @param defaultValue the defaultValue [String] found by parsing the pref xml
     *
     * @return true if values are considered to be the same
     */
    private fun isSame(settingValue: Any, defaultValue: String?) = when {
        defaultValue == null -> false
        settingValue is Boolean -> settingValue.toString() == defaultValue
        else -> settingValue == defaultValue
    }

    /**
     * Get a string describing the preferences changed by the user
     * @param context the context to be used to retrieve the preferences
     *
     * @return a string of all the changed preferences
     */
    fun getChangedPrefsString(context: Context) = buildString {
        getAllChangedPrefs(context).forEach { append("\t* ${it.first} -> ${it.second}\r\n") }
    }

    /**
     * Parse a preference xml resource to get a list of [PreferenceItem]
     * @param context the context to be used to retrieve the preferences
     * @param id the xml resource id to parse
     *
     * @return all the parsed items in the form of a [PreferenceItem] list
     */
    private fun parsePreferences(context: Context, @XmlRes id: Int): ArrayList<PreferenceItem> {
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
                    val defaultValue = getValue(context, parser, namespace, "defaultValue")
                    if (summary.contains("%s") && element == "ListPreference") {
                        //get the current value for the string substitution
                        try {
                            val rawValue = Settings.getInstance(context).getString(key, defaultValue) ?: ""
                            val entriesId = parser.getAttributeResourceValue(namespace, "entries", -1)
                            val entryValuesId = parser.getAttributeResourceValue(namespace, "entryValues", -1)
                            val index = context.resources.getStringArray(entryValuesId).indexOf(rawValue)
                            summary = summary.replace("%s", context.resources.getStringArray(entriesId)[index])
                            summaryEng = summaryEng.replace("%s", englishContext.resources.getStringArray(entriesId)[index])
                        } catch (e: Exception) {
                        }
                    }
                    if (key.isNotBlank()) result.add(PreferenceItem(key, id, title, summary, titleEng, summaryEng, category, categoryEng, defaultValue))
                }
            }
            eventType = parser.next()
        }
        return result
    }

    /**
     * Get the value of an xml node
     * @param context the context to be used to retrieve the value. This context can be localized in English to retrieve the strings
     * @param parser the [XmlResourceParser] to use to parse the attributes
     * @param namespace the namespace to use to parse the attributes
     * @param node the node to be parsed
     *
     * @return the parsed value
     */
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
}

/**
 * Object describing a [androidx.preference.Preference] with useful values to search / display them
 */
@Parcelize
data class PreferenceItem(val key: String, val parentScreen: Int, val title: String, val summary: String, val titleEng:String, val summaryEng: String, val category: String, val categoryEng: String, val defaultValue:String?) : Parcelable