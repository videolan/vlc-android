/*
 * ************************************************************************
 *  MovieDataSource.kt
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

package org.videolan.moviepedia.provider.datasources

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.moviepedia.database.models.MediaMetadataType
import org.videolan.moviepedia.database.models.MediaMetadataWithImages
import org.videolan.moviepedia.repository.MediaMetadataRepository

class MovieDataSourceFactory(private val context: Context, private val sort: Pair<Int, Boolean>, private val metadataType: MediaMetadataType) : DataSource.Factory<Int, MediaMetadataWithImages>() {
    private val dataSource = MutableLiveData<DataSource<Int, MediaMetadataWithImages>>()
    override fun create(): DataSource<Int, MediaMetadataWithImages> {
        val sortField = when (sort.first) {
            Medialibrary.SORT_DEFAULT -> "title"
            Medialibrary.SORT_RELEASEDATE -> "releaseDate"
            else -> "title"
        }
        val sortType = if (sort.second) "DESC" else "ASC"

        val newDataSource = MediaMetadataRepository.getInstance(context).getMoviePagedList(sortField, sortType, metadataType).mapByPage {
            //Inject ML medias to results
            val medialibrary = Medialibrary.getInstance()
            if (medialibrary.isStarted) it.forEach { episode ->
                if (episode.media == null) {
                    episode.metadata.mlId?.let { mlId ->
                        episode.media = medialibrary.getMedia(mlId)
                    }
                }
            }
            it
        }.create()
        dataSource.postValue(newDataSource)
        return newDataSource
    }
}