/*******************************************************************************
 *  Converters.kt
 * ****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
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
 ******************************************************************************/

package org.videolan.moviepedia.database

import android.net.Uri
import androidx.core.net.toUri
import androidx.room.TypeConverter
import org.videolan.moviepedia.database.models.MediaImageType
import org.videolan.moviepedia.database.models.MediaMetadataType
import org.videolan.moviepedia.database.models.PersonType
import java.util.*

class Converters {
    @TypeConverter fun uriToString(uri: Uri): String = uri.toString()
    @TypeConverter fun stringToUri(value: String): Uri = value.toUri()
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time?.toLong()
    }

    //Person type
    @TypeConverter
    fun personTypeToKey(personType: PersonType): Int {
        return personType.key
    }

    @TypeConverter
    fun personTypeFromKey(key: Int): PersonType {
        return PersonType.fromKey(key)
    }

    //Media image type
    @TypeConverter
    fun mediaImageTypeToKey(mediaImageType: MediaImageType): Int {
        return mediaImageType.key
    }

    @TypeConverter
    fun mediaImageTypeFromKey(key: Int): MediaImageType {
        return MediaImageType.fromKey(key)
    }

    //Media type
    @TypeConverter
    fun mediaTypeToKey(mediaType: MediaMetadataType): Int {
        return mediaType.key
    }

    @TypeConverter
    fun mediaTypeFromKey(key: Int): MediaMetadataType {
        return MediaMetadataType.fromKey(key)
    }
}