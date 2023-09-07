/*
 * ************************************************************************
 *  MoviepediaMovieProvider.kt
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

package org.videolan.moviepedia.provider

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.switchMap
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import org.videolan.moviepedia.database.models.MediaMetadataType
import org.videolan.moviepedia.database.models.MediaMetadataWithImages
import org.videolan.moviepedia.provider.datasources.MovieDataSourceFactory

class MediaScrapingMovieProvider(private val context: Context, private val mediaType: MediaMetadataType) : MediaScrapingProvider(context) {

    override var pagedList: LiveData<PagedList<MediaMetadataWithImages>> = sortQuery.switchMap { input ->
        val movieDataSourceFactory = MovieDataSourceFactory(context, input, mediaType)
        //todo moviepedia set the right values
        val pagedListConfig = PagedList.Config.Builder()
                .setInitialLoadSizeHint(1)
                .setPageSize(20).build()
        LivePagedListBuilder(movieDataSourceFactory, pagedListConfig)
                .build()
    }.also {
        it.observeForever {
            //todo moviepedia find a better way to generate the headers. Typically, this implementation reloads the headers for the whole list instead of doing it only for the diff
            completeHeaders(it.toTypedArray(), 0)
            //todo moviepedia find a better way to generate laoding value
            loading.postValue(false)
        }
    }
}