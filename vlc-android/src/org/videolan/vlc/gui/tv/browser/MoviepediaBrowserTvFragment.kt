/*
 * ************************************************************************
 *  MoviepediaBrowserTvFragment.kt
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

package org.videolan.vlc.gui.tv.browser

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.android.synthetic.main.song_browser.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.R
import org.videolan.vlc.database.models.DisplayableMediaMetadata
import org.videolan.vlc.database.models.MediaMetadataWithImages
import org.videolan.vlc.database.models.MediaTvshow
import org.videolan.vlc.gui.tv.MoviepediaTvItemAdapter
import org.videolan.vlc.gui.tv.TvItemAdapter
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.providers.MoviepediaProvider
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.tv.MoviepediaBrowserViewModel
import org.videolan.vlc.viewmodels.tv.getMoviepediaBrowserModel
import java.util.*

@UseExperimental(ObsoleteCoroutinesApi::class)
@ExperimentalCoroutinesApi
class MoviepediaBrowserTvFragment : BaseBrowserTvFragment<DisplayableMediaMetadata>() {
    override fun provideAdapter(eventsHandler: IEventsHandler<DisplayableMediaMetadata>, itemSize: Int): TvItemAdapter {
        return MoviepediaTvItemAdapter((viewModel as MoviepediaBrowserViewModel).category, this, itemSize)
    }

    override lateinit var adapter: TvItemAdapter

    override fun getTitle() = when ((viewModel as MoviepediaBrowserViewModel).category) {
        HEADER_TV_SHOW -> getString(R.string.header_tvshows)
        HEADER_MOVIES -> getString(R.string.header_movies)
        else -> getString(R.string.video)
    }

    override fun getCategory(): Long = (viewModel as MoviepediaBrowserViewModel).category

    override fun getColumnNumber() = when ((viewModel as MoviepediaBrowserViewModel).category) {
        CATEGORY_VIDEOS -> resources.getInteger(R.integer.tv_videos_col_count)
        else -> resources.getInteger(R.integer.tv_songs_col_count)
    }

    companion object {
        fun newInstance(type: Long) =
                MoviepediaBrowserTvFragment().apply {
                    arguments = Bundle().apply {
                        this.putLong(CATEGORY, type)
                    }
                }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = getMoviepediaBrowserModel(arguments?.getLong(CATEGORY, HEADER_MOVIES)
                ?: HEADER_MOVIES)

        (viewModel.provider as MoviepediaProvider<*>).pagedList.observe(this, Observer { items ->
            submitList(items)

            binding.emptyLoading.state = if (items.isEmpty()) EmptyLoadingState.EMPTY else EmptyLoadingState.NONE

            //headers
            val nbColumns = if ((viewModel as MoviepediaBrowserViewModel).sort == AbstractMedialibrary.SORT_ALPHA || (viewModel as MoviepediaBrowserViewModel).sort == AbstractMedialibrary.SORT_DEFAULT) 9 else 1

            headerList.layoutManager = GridLayoutManager(requireActivity(), nbColumns)
            headerAdapter.sortType = (viewModel as MoviepediaBrowserViewModel).sort
            val headerItems = ArrayList<String>()
            viewModel.provider.headers.run {
                for (i in 0 until size()) {
                    headerItems.add(valueAt(i))
                }
            }
            headerAdapter.items = headerItems
            headerAdapter.notifyDataSetChanged()
        })
        (viewModel.provider as MoviepediaProvider<*>).loading.observe(this, Observer {
            if (it) binding.emptyLoading.state = EmptyLoadingState.LOADING
        })
    }

    override fun onClick(v: View, position: Int, item: DisplayableMediaMetadata) {
        when (item) {
            is MediaMetadataWithImages -> {
                item.metadata.mlId?.let {
                    launch {
                        val media = requireActivity().getFromMl { getMedia(it) }
                        TvUtil.showMediaDetail(requireActivity(), media)
                    }
                }
            }
            is MediaTvshow -> {
                //todo moviepedia we have to create a new DetailFragment that display the TV show info + a line by season
            }
        }
    }

    override fun onLongClick(v: View, position: Int, item: DisplayableMediaMetadata): Boolean {
        when (item) {
            is MediaMetadataWithImages -> {
                item.metadata.mlId?.let {
                    launch {
                        val media = requireActivity().getFromMl { getMedia(it) }
                        TvUtil.showMediaDetail(requireActivity(), media)
                    }
                }
            }
            is MediaTvshow -> {
                //todo moviepedia we have to create a new DetailFragment that display the TV show info + a line by season
            }
        }

        return true
    }
}
