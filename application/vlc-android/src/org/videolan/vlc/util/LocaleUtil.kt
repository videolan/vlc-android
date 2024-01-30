/*
 * ************************************************************************
 *  LocaleUtil.kt
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

package org.videolan.vlc.util

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.resources.AppContextProvider
import org.videolan.tools.firstLetterUppercase
import org.videolan.vlc.R
import java.util.Locale

/**
 * This is a temporary workaround to translate [IMedia.Track.language] sent by libvlc as a string to a real locale.
 * libvlc 4 should expose the ISO 639 code of the track and therefore [LocaleUtil] and vlc_locales.json could be removed then
 *
 * ⚠️ Current limitation: as the language is only exposed in [IMedia.Track.language] that can be retrieved from a media,
 * external tracks (as subtitle from files) won't be checked during the playback. This is because the tracks retrieved from the
 * [MediaPlayer] only have a description and no language. So we have to compare them to the tracks from the media that doesn't
 * include the external tracks.
 *
 * FIXME Remove this when libvlc exposes a track locale ISO code
 */
object LocaleUtil {

    /**
     * Get a locale name from a locale extracted from libvlc
     * @param from the track language string from libvlc
     *
     * @return a localized string for this language.
     * Fallback on VLC's language name if Android doesn't know the locale and falls back on returning [from] if nothing is found
     */
    fun getLocaleName(from: String): String {
        vlcLocaleList.forEach {
            if (it.language == from) return toTranslatedLanguage(it)
            it.values.forEach { sub ->
                if (sub == from) return toTranslatedLanguage(it)
            }
        }
        return from
    }

    /**
     * Get an ISO-639-1 locale string from a Track language sent by libvlc
     * @param from the track language string from libvlc
     *
     * @return an ISO-639-1 locale string. Returns null if nothing is found
     */
    fun getLocaleFromVLC(from: String): String? {
        vlcLocaleList.forEach {
            if (it.language == from) return it.values[0]
            it.values.forEach { sub ->
                if (sub == from) return it.values[0]
            }
        }
        return null
    }

    /**
     * Get a translated language name from a [VLCLocale]
     * @param entry: the [VLCLocale] to get the language from
     *
     * @return a language string in the user's locale if found by Android.
     * Falls back on VLC's language name if not found
     */
    private fun toTranslatedLanguage(entry: VLCLocale): String {
        val it = entry.values[0]
        val androidLocale = Locale(it).displayLanguage
        if (androidLocale != it) return androidLocale.firstLetterUppercase()
        return entry.language
    }

    /**
     * The VLC locale list extracted from the assets
     */
    private val vlcLocaleList by lazy {

        val jsonData = AppContextProvider.appResources.openRawResource(R.raw.vlc_locales).bufferedReader().use {
            it.readText()
        }

        val moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(MutableList::class.java, VLCLocale::class.java)

        val jsonAdapter: JsonAdapter<List<VLCLocale>> = moshi.adapter(type)

        jsonAdapter.fromJson(jsonData)!!

    }

    fun String.localeEquivalent():Array<String> = when (this.lowercase()) {
        "in" -> arrayOf("in", "id")
        else -> arrayOf(this)
    }

    /**
     * VLC locale object
     * @param language: the language VLC uses
     * @param values: the ISO values (ISO-639-1, ISO-639-2T, ISO-639-2B) when applicable
     *
     * @see [https://code.videolan.org/videolan/vlc/-/blob/master/src/text/iso-639_def.h]
     */
    data class VLCLocale(
            @field:Json(name = "language")
            val language: String,
            @field:Json(name = "values")
            val values: List<String>
    )

}