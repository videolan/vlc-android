package org.videolan.television.ui.browser

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.television.ui.MediaTvItemAdapter
import org.videolan.television.ui.TvItemAdapter
import org.videolan.television.ui.TvUtil
import org.videolan.television.viewmodel.MediaBrowserViewModel
import org.videolan.television.viewmodel.getMediaBrowserModel
import org.videolan.tools.FORCE_PLAY_ALL
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import java.util.*

@UseExperimental(ObsoleteCoroutinesApi::class)
@ExperimentalCoroutinesApi
class MediaBrowserTvFragment : BaseBrowserTvFragment<MediaLibraryItem>() {
    override fun provideAdapter(eventsHandler: IEventsHandler<MediaLibraryItem>, itemSize: Int): TvItemAdapter {
        return MediaTvItemAdapter(when ((viewModel as MediaBrowserViewModel).category) {
            CATEGORY_SONGS -> MediaWrapper.TYPE_AUDIO
            CATEGORY_ALBUMS -> MediaWrapper.TYPE_ALBUM
            CATEGORY_ARTISTS -> MediaWrapper.TYPE_ARTIST
            CATEGORY_GENRES -> MediaWrapper.TYPE_GENRE
            else -> MediaWrapper.TYPE_VIDEO
        }, this, itemSize)
    }

    override fun getDisplayPrefId() = "display_tv_media_${(viewModel as MediaBrowserViewModel).category}"


    override lateinit var adapter: TvItemAdapter

    override fun getTitle() = when ((viewModel as MediaBrowserViewModel).category) {
        CATEGORY_SONGS -> getString(R.string.tracks)
        CATEGORY_ALBUMS -> getString(R.string.albums)
        CATEGORY_ARTISTS -> getString(R.string.artists)
        CATEGORY_GENRES -> getString(R.string.genres)
        else -> getString(R.string.video)
    }

    override fun getCategory(): Long = (viewModel as MediaBrowserViewModel).category

    override fun getColumnNumber() = when ((viewModel as MediaBrowserViewModel).category) {
        CATEGORY_VIDEOS -> resources.getInteger(R.integer.tv_videos_col_count)
        else -> resources.getInteger(R.integer.tv_songs_col_count)
    }

    companion object {
        fun newInstance(type: Long, item: MediaLibraryItem?) =
                MediaBrowserTvFragment().apply {
                    arguments = Bundle().apply {
                        this.putLong(CATEGORY, type)
                        this.putParcelable(ITEM, item)
                    }
                }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val currentItem = if (savedInstanceState != null) savedInstanceState.getParcelable<Parcelable>(ITEM) as? MediaLibraryItem
        else requireActivity().intent.getParcelableExtra<Parcelable>(ITEM) as? MediaLibraryItem

        viewModel = getMediaBrowserModel(arguments?.getLong(CATEGORY, CATEGORY_SONGS) ?: CATEGORY_SONGS, currentItem)

        (viewModel.provider as MedialibraryProvider<*>).pagedList.observe(this, Observer { items ->
            submitList(items)

            binding.emptyLoading.state = if (items.isEmpty()) EmptyLoadingState.EMPTY else EmptyLoadingState.NONE

            //headers
            val nbColumns = if ((viewModel as MediaBrowserViewModel).sort == Medialibrary.SORT_ALPHA || (viewModel as MediaBrowserViewModel).sort == Medialibrary.SORT_DEFAULT) 9 else 1

            binding.headerList.layoutManager = GridLayoutManager(requireActivity(), nbColumns)
            headerAdapter.sortType = (viewModel as MediaBrowserViewModel).sort
            val headerItems = ArrayList<String>()
            viewModel.provider.headers.run {
                for (i in 0 until size()) {
                    headerItems.add(valueAt(i))
                }
            }
            headerAdapter.items = headerItems
            headerAdapter.notifyDataSetChanged()
        })
        (viewModel.provider as MedialibraryProvider<*>).loading.observe(this, Observer {
            binding.emptyLoading.state = when {
                it -> EmptyLoadingState.LOADING
                viewModel.isEmpty() && adapter.isEmpty() -> EmptyLoadingState.EMPTY
                else -> EmptyLoadingState.NONE
            }
        })
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        lifecycleScope.launchWhenStarted {
            if ((viewModel as MediaBrowserViewModel).category == CATEGORY_VIDEOS && !Settings.getInstance(requireContext()).getBoolean(FORCE_PLAY_ALL, true)) {
                TvUtil.playMedia(requireActivity(), item as MediaWrapper)
            } else {
                TvUtil.openMediaFromPaged(requireActivity(), item, viewModel.provider as MedialibraryProvider<out MediaLibraryItem>)
            }
        }
    }
}
