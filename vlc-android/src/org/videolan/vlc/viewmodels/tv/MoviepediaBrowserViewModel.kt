/*
 * ************************************************************************
 *  MoviepediaBrowserViewModel.kt
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

package org.videolan.vlc.viewmodels.tv

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.videolan.vlc.database.models.DisplayableMediaMetadata
import org.videolan.vlc.providers.MoviepediaMovieProvider
import org.videolan.vlc.providers.MoviepediaTvshowProvider
import org.videolan.vlc.util.HEADER_MOVIES
import org.videolan.vlc.viewmodels.SortableModel

@ExperimentalCoroutinesApi
class MoviepediaBrowserViewModel(context: Context, val category: Long) : SortableModel(context), TvBrowserModel<DisplayableMediaMetadata> {
    override fun restore() {
    }

    override fun filter(query: String?) {
    }

    override fun refresh() {
        provider.refresh()
    }

    override var currentItem: DisplayableMediaMetadata? = null

    override var nbColumns = 0

    override val provider = when (category) {
        HEADER_MOVIES -> MoviepediaMovieProvider(context)
        else -> MoviepediaTvshowProvider(context)
    }

    override fun sort(sort: Int) {
        provider.sort(sort)
    }

    override fun canSortByReleaseDate() = true
    //todo moviepedia add more sort options. See [MoviepediaProvider.sort] and [MovieDataSourceFactory] for sort implementation and [ModelsHelper.getHeaderMoviepedia] for header implementation

    class Factory(private val context: Context, private val category: Long) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MoviepediaBrowserViewModel(context.applicationContext, category) as T
        }
    }
}

@ExperimentalCoroutinesApi
fun Fragment.getMoviepediaBrowserModel(category: Long) = ViewModelProviders.of(requireActivity(), MoviepediaBrowserViewModel.Factory(requireContext(), category)).get(MoviepediaBrowserViewModel::class.java)
