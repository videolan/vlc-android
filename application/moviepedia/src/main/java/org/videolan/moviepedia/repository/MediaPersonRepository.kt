/*
 * ************************************************************************
 *  MediaPersonRepository.kt
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

/*******************************************************************************
 *  BrowserFavRepository.kt
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

package org.videolan.moviepedia.repository

import android.content.Context
import androidx.lifecycle.LiveData
import org.videolan.tools.IOScopedObject
import org.videolan.tools.SingletonHolder
import org.videolan.moviepedia.database.MoviePediaDatabase
import org.videolan.moviepedia.database.MediaPersonJoinDao
import org.videolan.moviepedia.database.models.MediaPersonJoin
import org.videolan.moviepedia.database.models.Person
import org.videolan.moviepedia.database.models.PersonType

class MediaPersonRepository(private val mediaPersonActorJoinDao: MediaPersonJoinDao) : IOScopedObject() {

    fun addPersons(mediaPersons: List<MediaPersonJoin>) = mediaPersonActorJoinDao.insertPersons(mediaPersons)
    fun removeAllFor(moviepediaId: String) = mediaPersonActorJoinDao.removeAllFor(moviepediaId)

    fun getAll() = mediaPersonActorJoinDao.getAll()

    fun getPersonsByType(moviepediaId: String, personType: PersonType): LiveData<List<Person>> {
        return mediaPersonActorJoinDao.getActorsForMediaLive(moviepediaId, personType)
    }

    companion object : SingletonHolder<MediaPersonRepository, Context>({ MediaPersonRepository(MoviePediaDatabase.getInstance(it).mediaPersonActorJoinDao()) })
}