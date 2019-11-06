/*
 * ************************************************************************
 *  TvshowDataSourceFactory.kt
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

package org.videolan.vlc.providers.datasources

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.database.models.MediaTvshow
import org.videolan.vlc.repository.MediaMetadataRepository

class TvshowDataSourceFactory(private val context: Context, private val sort: Pair<Int, Boolean>) : DataSource.Factory<Int, MediaTvshow>() {
    private val dataSource = MutableLiveData<DataSource<Int, MediaTvshow>>()
    override fun create(): DataSource<Int, MediaTvshow> {
        val sortField = when (sort.first) {
            AbstractMedialibrary.SORT_DEFAULT -> "name"
            AbstractMedialibrary.SORT_RELEASEDATE -> "date"
            else -> "name"
        }
        val sortType = if (sort.second) "DESC" else "ASC"

        val newDataSource = MediaMetadataRepository.getInstance(context).getTvshowPagedList(sortField, sortType).create()
        dataSource.postValue(newDataSource)
        return newDataSource
    }
}