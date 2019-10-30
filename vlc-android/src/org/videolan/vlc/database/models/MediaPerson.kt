/*
 * ************************************************************************
 *  MediaPerson.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.vlc.database.models

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(tableName = "media_person_join",
        primaryKeys = arrayOf("mediaId", "personId", "type"),
        foreignKeys = arrayOf(
                ForeignKey(entity = MediaMetadata::class,
                        parentColumns = arrayOf("ml_id"),
                        childColumns = arrayOf("mediaId")),
                ForeignKey(entity = Person::class,
                        parentColumns = arrayOf("next_id"),
                        childColumns = arrayOf("personId"))
        )
)
data class MediaPersonJoin(
        val mediaId: Long,
        val personId: String,
        val type: PersonType
)

enum class PersonType(val key: Int) {
    ACTOR(0), DIRECTOR(1), MUSICIAN(2), PRODUCER(3), WRITER(4);

    companion object {
        fun fromKey(key: Int): PersonType {
            values().forEach { if (it.key == key) return it }
            return ACTOR
        }
    }
}