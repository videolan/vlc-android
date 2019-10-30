/*
 * ************************************************************************
 *  MediaPersonJoinDao.kt
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

package org.videolan.vlc.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.videolan.vlc.database.models.MediaPersonJoin
import org.videolan.vlc.database.models.Person
import org.videolan.vlc.database.models.PersonType

@Dao
interface MediaPersonJoinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPerson(person: MediaPersonJoin)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPersons(persons: List<MediaPersonJoin>)

    @Query("DELETE FROM media_person_join WHERE mediaId = :mlId")
    fun removeAllFor(mlId: Long)

    @Query("SELECT * FROM media_person_join")
    fun getAll(): List<MediaPersonJoin>

    @Query("""
           SELECT * FROM media_metadata_person
           INNER JOIN media_person_join
           ON media_metadata_person.next_id=media_person_join.personId
           WHERE media_person_join.mediaId=:mediaId AND media_person_join.type=:type
           """)
    fun getActorsForMediaLive(mediaId: Long, type: PersonType): LiveData<List<Person>>

    @Query("""
           SELECT * FROM media_metadata_person
           INNER JOIN media_person_join
           ON media_metadata_person.next_id=media_person_join.personId
           WHERE media_person_join.mediaId=:mediaId AND media_person_join.type=:type
           """)
    fun getActorsForMedia(mediaId: Long, type: PersonType): List<Person>
}