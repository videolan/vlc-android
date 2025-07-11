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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.XmlResourceParser
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.annotation.XmlRes
import androidx.fragment.app.FragmentActivity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.tools.ALBUMS_SHOW_TRACK_NUMBER
import org.videolan.tools.AUDIO_DELAY_GLOBAL
import org.videolan.tools.AUDIO_PLAY_PROGRESS_MODE
import org.videolan.tools.BROWSER_SHOW_HIDDEN_FILES
import org.videolan.tools.BROWSER_SHOW_ONLY_MULTIMEDIA
import org.videolan.tools.CloseableUtils
import org.videolan.tools.DISPLAY_UNDER_NOTCH
import org.videolan.tools.KEY_ARTISTS_SHOW_ALL
import org.videolan.tools.KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE
import org.videolan.tools.KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE
import org.videolan.tools.KEY_PLAYBACK_SPEED_AUDIO_GLOBAL
import org.videolan.tools.KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE
import org.videolan.tools.KEY_PLAYBACK_SPEED_VIDEO_GLOBAL
import org.videolan.tools.KEY_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE
import org.videolan.tools.KEY_SHOW_WHATS_NEW
import org.videolan.tools.PREF_RESTORE_VIDEO_TIPS_SHOWN
import org.videolan.tools.PREF_SHOW_VIDEO_SETTINGS_DISCLAIMER
import org.videolan.tools.PREF_TIPS_SHOWN
import org.videolan.tools.PREF_WIDGETS_TIPS_SHOWN
import org.videolan.tools.SCREEN_ORIENTATION
import org.videolan.tools.Settings
import org.videolan.tools.getContextWithLocale
import org.videolan.tools.putSingle
import org.videolan.tools.wrap
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.DefaultPlaybackAction
import org.videolan.vlc.gui.helpers.DefaultPlaybackActionMediaType
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.providers.medialibrary.AlbumsProvider
import org.videolan.vlc.providers.medialibrary.ArtistsProvider
import org.videolan.vlc.providers.medialibrary.FoldersProvider
import org.videolan.vlc.providers.medialibrary.GenresProvider
import org.videolan.vlc.providers.medialibrary.PlaylistsProvider
import org.videolan.vlc.providers.medialibrary.TracksProvider
import org.videolan.vlc.providers.medialibrary.VideoGroupsProvider
import org.videolan.vlc.providers.medialibrary.VideosProvider
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.share
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

object PreferenceParser {

    // Other settings that should be backed up and restored
    val additionalSettings = arrayOf(
        KEY_SHOW_WHATS_NEW,
        AUDIO_DELAY_GLOBAL,
        AUDIO_PLAY_PROGRESS_MODE,
        KEY_PLAYBACK_SPEED_VIDEO_GLOBAL,
        KEY_PLAYBACK_SPEED_AUDIO_GLOBAL,
        KEY_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE,
        KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE,
        KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE,
        KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE,
        SCREEN_ORIENTATION,
        PREF_TIPS_SHOWN,
        PREF_WIDGETS_TIPS_SHOWN,
        PREF_RESTORE_VIDEO_TIPS_SHOWN,
        PREF_SHOW_VIDEO_SETTINGS_DISCLAIMER,
        DISPLAY_UNDER_NOTCH,
        "equalizer_enabled",
        "equalizer_set",
        "equalizer_values",
        "equalizer_saved"
        )

    /**
     * Parses all the preferences available in the app.
     * @param context the context to be used to retrieve the preferences
     * @param parseUIPrefs whether to parse the UI preferences or not
     *
     * @return a list of [PreferenceItem]
     */
    fun parsePreferences(context: Context, parseUIPrefs: Boolean = false): ArrayList<PreferenceItem> {
        val result = ArrayList<PreferenceItem>()
        arrayListOf(R.xml.preferences, R.xml.preferences_adv, R.xml.preferences_audio, R.xml.preferences_parental_control, R.xml.preferences_casting, R.xml.preferences_subtitles, R.xml.preferences_ui, R.xml.preferences_video, R.xml.preferences_remote_access)
            .apply {
                if (parseUIPrefs) {
                    this.add(R.xml.preferences_video_controls)
                    this.add(R.xml.preferences_audio_controls)
                }
            }
        .forEach {
            result.addAll(parsePreferences(context, it))
        }
        return result
    }

    /**
     * Parses all the preferences available in the app.
     * @param context the context to be used to retrieve the preferences
     * @param forVideo if true, parses the video controls else the audio controls
     *
     * @return a list of [PreferenceItem]
     */
    fun parseControlPreferences(context: Context, forVideo: Boolean): ArrayList<PreferenceItem> {
        val result = ArrayList<PreferenceItem>()
        arrayListOf<Int>()
            .apply {
                if (forVideo)
                    this.add(R.xml.preferences_video_controls)
                else
                    this.add(R.xml.preferences_audio_controls)
            }
        .forEach {
            result.addAll(parsePreferences(context, it))
        }
        return result
    }

    /**
     * Compares the preference list with the set settings to get the list of the changed settings by the user
     * @param context the context to be used to retrieve the preferences
     * @param parseUIPrefs whether to parse the UI preferences or not
     * @param showTitle whether to show the title of the preference or not
     *
     * @return a list of changed settings in the form a of pair of the key and the value
     */
    private fun getAllChangedPrefs(context: Context, parseUIPrefs: Boolean = true, showTitle: Boolean = false): ArrayList<Pair<String, Any>> {
        val allPrefs = parsePreferences(context, parseUIPrefs = parseUIPrefs)
        val allSettings = Settings.getInstance(context).all
        val changedSettings = ArrayList<Pair<String, Any>>()
        allPrefs.forEach { pref ->
            allSettings.forEach { setting ->
                if (pref.key == setting.key && pref.key != "custom_libvlc_options") {
                    setting.value?.let {
                        if (!isSame(it, pref.defaultValue)) {
                            val first = if (showTitle) "${pref.key} (${pref.titleEng})" else pref.key
                            changedSettings.add(Pair(first, it))
                        }
                    }
                }
            }
        }
        return changedSettings
    }

    /**
     * Get all changed control prefs
     *
     * @param context the context to be used to retrieve the preferences
     * @param forVideo if true, returns the video controls else the audio controls
     * @param showTitle whether to show the title of the preference or not
     * @return a list of changed settings in the form a of pair of the key and the value
     */
    private fun getAllChangedControlPrefs(context: Context, forVideo: Boolean = false, showTitle: Boolean = false): ArrayList<Pair<String, Any>> {
        val allPrefs = parseControlPreferences(context, forVideo = forVideo)
        val allSettings = Settings.getInstance(context).all
        val changedSettings = ArrayList<Pair<String, Any>>()
        allPrefs.forEach { pref ->
            allSettings.forEach { setting ->
                if (pref.key == setting.key && pref.key != "custom_libvlc_options") {
                    setting.value?.let {
                        val first = if (showTitle) "${pref.key} (${pref.titleEng})" else pref.key
                        if (!isSame(it, pref.defaultValue)) changedSettings.add(Pair(first, it))
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
        append("\r\nMain settings:\r\n")
        getAllChangedPrefs(context, parseUIPrefs = false, showTitle = true).forEach { append("* ${it.first} -> ${it.second}\r\n") }
        val videoControls = buildString {
            getAllChangedControlPrefs(context, forVideo = true, showTitle = true).forEach { append("* ${it.first} -> ${it.second}\r\n") }
        }
        if (videoControls.isNotBlank()) {
            append("\r\nVideo controls:\r\n")
            append(videoControls)
        }
        val audioControls = buildString {
            getAllChangedControlPrefs(context, forVideo = false, showTitle = true).forEach { append("* ${it.first} -> ${it.second}\r\n") }
        }
        if (audioControls.isNotBlank()) {
            append("\r\nAudio controls:\r\n")
            append(audioControls)
        }
        //display settings
        val displaySettings = buildString {
            val settings = Settings.getInstance(context)
            val englishContext = context.applicationContext.getContextWithLocale("en")
            DefaultPlaybackActionMediaType.entries.forEach {
                val currentPlaybackAction = it.getCurrentPlaybackAction(settings)
                if (currentPlaybackAction != DefaultPlaybackAction.PLAY) {
                    append("* ${it.defaultActionKey} -> ${englishContext.getString(currentPlaybackAction.title)}\r\n")
                }
            }

            for ((key) in settings.all) {
                if (key.startsWith("display_mode_")) {
                    append("* $key -> ${settings.getBoolean(key, false)}\r\n")
                }
            }

            for ((key) in settings.all) {
                if (key.endsWith("_only_favs")) {
                    append("* $key -> ${settings.getBoolean(key, false)}\r\n")
                }
            }

            if (settings.getBoolean(KEY_ARTISTS_SHOW_ALL, false))
                append("* $KEY_ARTISTS_SHOW_ALL -> true\r\n")

            if (settings.getBoolean(BROWSER_SHOW_ONLY_MULTIMEDIA, false))
                append("* $BROWSER_SHOW_ONLY_MULTIMEDIA -> true\r\n")

            if (!Settings.showTrackNumber)
                append("* $ALBUMS_SHOW_TRACK_NUMBER -> false\r\n")

            if (!settings.getBoolean(BROWSER_SHOW_HIDDEN_FILES, true))
                append("* $BROWSER_SHOW_HIDDEN_FILES -> false\r\n")

            //sorts
            arrayOf(
                AlbumsProvider::class.java, ArtistsProvider::class.java, FoldersProvider::class.java, GenresProvider::class.java, PlaylistsProvider::class.java, TracksProvider::class.java,
                VideoGroupsProvider::class.java, VideosProvider::class.java
            ).forEach {
                for ((key) in settings.all) {
                    if (key.startsWith(it.simpleName) && !key.endsWith("_desc") && !key.endsWith("_only_favs")) {
                        append("* Sort ${it.simpleName} -> ${settings.getInt(key, 0)} (${getSortName(settings.getInt(key, 0))}) - ${if (settings.getBoolean("${key}_desc", false)) "DESC" else "ASC"}\r\n")
                    }
                }
            }

        }
        if (displaySettings.isNotBlank()) {
            append("\r\nDisplay settings:\r\n")
            append(displaySettings)
        }
    }

    fun getSortName(index:Int) = when(index) {
        Medialibrary.SORT_DEFAULT -> "Default"
        Medialibrary.SORT_ALPHA -> "Alphabetical"
        Medialibrary.SORT_DURATION -> "Duration"
        Medialibrary.SORT_INSERTIONDATE -> "Insertion date"
        Medialibrary.SORT_LASTMODIFICATIONDATE -> "Last modification date"
        Medialibrary.SORT_RELEASEDATE -> "Release date"
        Medialibrary.SORT_FILESIZE -> "File size"
        Medialibrary.SORT_ARTIST -> "Artist"
        Medialibrary.SORT_PLAYCOUNT -> "Play count"
        Medialibrary.SORT_ALBUM -> "Album"
        Medialibrary.SORT_FILENAME -> "Filename"
        else -> "Unknown"
    }

    /**
     * Get a string describing the preferences changed by the user in json format
     * @param context the context to be used to retrieve the preferences
     *
     * @return a string of all the changed preferences
     */
    fun getChangedPrefsJson(context: Context) = buildString {
        append("{")
        val allChangedPrefs = getAllChangedPrefs(context)
        addAllOtherPrefs(context, allChangedPrefs)
        for (allChangedPref in allChangedPrefs) {
            when {
                allChangedPref.second is Float || allChangedPref.second is Boolean || allChangedPref.second is Int || allChangedPref.second is Long -> append("\"${allChangedPref.first}\": ${allChangedPref.second}")
                else -> append("\"${allChangedPref.first}\": \"${allChangedPref.second}\"")
            }
            if (allChangedPref != allChangedPrefs.last()) append(", ")

        }
        append("}")
    }

    private fun addAllOtherPrefs(context: Context, pairs: ArrayList<Pair<String, Any>>) {
        for ((key, value) in Settings.getInstance(context).all) {
            if (key.startsWith("custom_equalizer_")) {
                value?.let {
                    pairs.add(Pair(key, it))
                }
            }
            if (key in additionalSettings) {
                value?.let {
                    pairs.add(Pair(key, it))
                }
            }
        }
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
        val appNamespace = "http://schemas.android.com/apk/res-auto"
        var firstPrefScreeFound = false
        val englishContext = context.applicationContext.getContextWithLocale("en")
        while (eventType != XmlResourceParser.END_DOCUMENT) {
            if (eventType == XmlResourceParser.START_TAG) {
                val element = parser.name

                if (element != "PreferenceScreen" && !firstPrefScreeFound) {
                    firstPrefScreeFound = true
                    category = getValue(context, parser, namespace, "title")
                    categoryEng = getValue(englishContext, parser, namespace, "title")
                }
                if (element != "PreferenceCategory" && element != "Preference") {
                    var key = getValue(context, parser, namespace, "key")
                    if (key.isBlank()) key = getValue(context, parser, appNamespace, "key")
                    var title = getValue(context, parser, namespace, "title")
                    if (title.isBlank()) title = getValue(context, parser, appNamespace, "title")
                    var titleEng = getValue(englishContext, parser, namespace, "title")
                    if (titleEng.isBlank()) titleEng = getValue(englishContext, parser, appNamespace, "title")
                    var summary = getValue(context, parser, namespace, "summary")
                    if (summary.isBlank()) summary = getValue(context, parser, appNamespace, "summary")
                    var summaryEng = getValue(englishContext, parser, namespace, "summary")
                    if (summaryEng.isBlank()) summaryEng = getValue(englishContext, parser, appNamespace, "summary")
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

    /**
     * Export the preferences to a file
     *
     * @param activity the activity to use to export the preferences
     * @param dst the destination file
     */
    suspend fun exportPreferences(activity: Activity, dst: File) = withContext(Dispatchers.IO) {
        val changedPrefs = getChangedPrefsJson(activity)
        var success = false
        val stream: FileOutputStream
        try {
            stream = FileOutputStream(dst)
            val output = OutputStreamWriter(stream)
            val bw = BufferedWriter(output)
            try {
                bw.write(changedPrefs)
                success = true
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                CloseableUtils.close(bw)
                CloseableUtils.close(output)
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        withContext(Dispatchers.Main) {
            if (success)
                if (activity is FragmentActivity)
                    UiTools.snackerConfirm(activity, activity.getString(R.string.export_settings_success), confirmMessage = R.string.share, overAudioPlayer = false) {
                        activity.share(dst)
                    }
                else Toast.makeText(activity, R.string.export_settings_success, Toast.LENGTH_LONG).show()
            else
                Toast.makeText(activity, R.string.export_settings_failure, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Restore the preferences from a file
     *
     * @param activity the activity to use to restore the preferences
     */
    suspend fun restoreSettings(activity: Activity, file: Uri) = withContext(Dispatchers.IO) {
        file.path?.let {
            val changedPrefs = FileUtils.getStringFromFile(it)
            val moshi = Moshi.Builder().build()
            val adapter = moshi.adapter<Map<String, Any>>(
                Types.newParameterizedType(
                    Map::class.java,
                    String::class.javaObjectType,
                    Object::class.java
                )
            )
            val savedSettings =  adapter.fromJson(changedPrefs)
            val newPrefs = Settings.getInstance(activity)
            val allPrefs = parsePreferences(activity, parseUIPrefs = true)
            savedSettings?.forEach { entry ->
                allPrefs.forEach {
                    if (it.key == entry.key  && it.key != "custom_libvlc_options") {
                        Log.i("PrefParser", "Restored: ${entry.key} -> ${entry.value}")
                        newPrefs.putSingle(entry.key, if (entry.value is Double) (entry.value as Double).toInt() else entry.value)
                    }
                }
            }
            //restore other settings
            savedSettings?.forEach {
                if (it.key.startsWith("custom_equalizer_")) {
                    newPrefs.putSingle(it.key, it.value)
                }
            }

            savedSettings?.forEach { saved ->
                additionalSettings.forEach {
                    if (it == saved.key) {
                        val value = if (saved.value is Double) (saved.value as Double).toFloat() else saved.value
                        newPrefs.putSingle(it, value)
                    }
                }
            }
        }
    }
}

/**
 * Object describing a [androidx.preference.Preference] with useful values to search / display them
 */
@Parcelize
data class PreferenceItem(val key: String, val parentScreen: Int, val title: String, val summary: String, val titleEng:String, val summaryEng: String, val category: String, val categoryEng: String, val defaultValue:String?) : Parcelable