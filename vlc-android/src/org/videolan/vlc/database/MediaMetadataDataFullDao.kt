/*
 * ************************************************************************
 *  MediaMetadataDataFullDao.kt
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
import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import org.videolan.vlc.database.models.MediaMetadataWithImages

@Dao
interface MediaMetadataDataFullDao {

    @Query("select * from media_metadata where ml_id = :id")
    fun getMetadataLiveByML(id: Long): LiveData<MediaMetadataWithImages?>

    @Query("select * from media_metadata where moviepedia_id = :id")
    fun getMediaLive(id: String): LiveData<MediaMetadataWithImages?>

    @Query("select * from media_metadata where show_id = :showId")
    fun getEpisodesLive(showId: String): LiveData<List<MediaMetadataWithImages>>

    @Query("select * from media_metadata where ml_id = :id")
    fun getMedia(id: Long): MediaMetadataWithImages?

    @Query("select * from media_metadata where moviepedia_id = :id")
    fun getMediaById(id: String): MediaMetadataWithImages?

    @Query("select * from media_metadata where moviepedia_id = :id")
    fun getMediaByIdLive(id: String): LiveData<MediaMetadataWithImages>

    @Query("select count(moviepedia_id) from media_metadata where type = 0")
    fun getMovieCount(): Int

    @Query("select count(moviepedia_id) from media_metadata where type = 2")
    fun getTvshowsCount(): Int

    @RawQuery(observedEntities = [MediaMetadataWithImages::class])
    fun getAllPaged(query: SupportSQLiteQuery): DataSource.Factory<Int, MediaMetadataWithImages>

    @Query("select * from media_metadata")
    fun getAllLive(): LiveData<List<MediaMetadataWithImages>>

//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    fun insert(mediaMetadataFull: MediaMetadataFull)

}