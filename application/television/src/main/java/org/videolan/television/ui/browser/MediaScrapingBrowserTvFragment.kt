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

package org.videolan.television.ui.browser

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.moviepedia.database.models.MediaMetadataType
import org.videolan.moviepedia.database.models.MediaMetadataWithImages
import org.videolan.moviepedia.provider.MediaScrapingProvider
import org.videolan.resources.CATEGORY
import org.videolan.resources.CATEGORY_VIDEOS
import org.videolan.resources.HEADER_MOVIES
import org.videolan.resources.HEADER_TV_SHOW
import org.videolan.resources.util.getFromMl
import org.videolan.television.ui.*
import org.videolan.television.viewmodel.MediaScrapingBrowserViewModel
import org.videolan.television.viewmodel.getMoviepediaBrowserModel
import org.videolan.vlc.R
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.interfaces.IEventsHandler

class MediaScrapingBrowserTvFragment : BaseBrowserTvFragment<MediaMetadataWithImages>() {
    override fun provideAdapter(eventsHandler: IEventsHandler<MediaMetadataWithImages>, itemSize: Int): TvItemAdapter {
        return MediaScrapingTvItemAdapter((viewModel as MediaScrapingBrowserViewModel).category, this, itemSize)
    }

    override fun getDisplayPrefId() = "display_tv_moviepedia_${(viewModel as MediaScrapingBrowserViewModel).category}"

    override lateinit var adapter: TvItemAdapter

    override fun getTitle() = when ((viewModel as MediaScrapingBrowserViewModel).category) {
        HEADER_TV_SHOW -> getString(R.string.header_tvshows)
        HEADER_MOVIES -> getString(R.string.header_movies)
        else -> getString(R.string.video)
    }

    override fun getCategory(): Long = (viewModel as MediaScrapingBrowserViewModel).category

    override fun getColumnNumber() = when ((viewModel as MediaScrapingBrowserViewModel).category) {
        CATEGORY_VIDEOS -> resources.getInteger(R.integer.tv_videos_col_count)
        else -> resources.getInteger(R.integer.tv_songs_col_count)
    }

    companion object {
        fun newInstance(type: Long) =
                MediaScrapingBrowserTvFragment().apply {
                    arguments = bundleOf(CATEGORY to type)
                }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = getMoviepediaBrowserModel(arguments?.getLong(CATEGORY, HEADER_MOVIES)
                ?: HEADER_MOVIES)

        (viewModel.provider as MediaScrapingProvider).pagedList.observe(this) { items ->
            binding.emptyLoading.post {
                submitList(items)

                binding.emptyLoading.state = if (items.isEmpty()) EmptyLoadingState.EMPTY else EmptyLoadingState.NONE

                //headers
                val nbColumns = if ((viewModel as MediaScrapingBrowserViewModel).sort == Medialibrary.SORT_ALPHA || (viewModel as MediaScrapingBrowserViewModel).sort == Medialibrary.SORT_DEFAULT) 9 else 1

                binding.headerList.layoutManager = GridLayoutManager(requireActivity(), nbColumns)
                headerAdapter.sortType = (viewModel as MediaScrapingBrowserViewModel).sort
            }
        }

        viewModel.provider.liveHeaders.observe(this) {
            updateHeaders(it)
            binding.list.invalidateItemDecorations()
        }

        (viewModel.provider as MediaScrapingProvider).loading.observe(this) {
            if (it) binding.emptyLoading.state = EmptyLoadingState.LOADING
        }
    }

    override fun onClick(v: View, position: Int, item: MediaMetadataWithImages) {
        when (item.metadata.type) {
            MediaMetadataType.TV_SHOW -> {
                val intent = Intent(activity, MediaScrapingTvshowDetailsActivity::class.java)
                intent.putExtra(TV_SHOW_ID, item.metadata.moviepediaId)
                requireActivity().startActivity(intent)
            }
            else -> {
                item.metadata.mlId?.let {
                    lifecycleScope.launchWhenStarted {
                        val media = requireActivity().getFromMl { getMedia(it) }
                        TvUtil.showMediaDetail(requireActivity(), media)
                    }
                }
            }
        }
    }

    override fun onLongClick(v: View, position: Int, item: MediaMetadataWithImages): Boolean {
        when (item.metadata.type) {
            MediaMetadataType.TV_SHOW -> {
                val intent = Intent(activity, MediaScrapingTvshowDetailsActivity::class.java)
                intent.putExtra(TV_SHOW_ID, item.metadata.moviepediaId)
                requireActivity().startActivity(intent)
            }
            else -> {
                item.metadata.mlId?.let {
                    lifecycleScope.launchWhenStarted {
                        val media = requireActivity().getFromMl { getMedia(it) }
                        TvUtil.showMediaDetail(requireActivity(), media)
                    }
                }
            }
        }

        return true
    }
}
