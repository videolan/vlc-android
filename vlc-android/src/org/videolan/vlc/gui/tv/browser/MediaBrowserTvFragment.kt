package org.videolan.vlc.gui.tv.browser

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.android.synthetic.main.song_browser.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.gui.tv.MediaTvItemAdapter
import org.videolan.vlc.gui.tv.TvItemAdapter
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.tv.MediaBrowserViewModel
import org.videolan.vlc.viewmodels.tv.getMediaBrowserModel
import java.util.*

@UseExperimental(ObsoleteCoroutinesApi::class)
@ExperimentalCoroutinesApi
class MediaBrowserTvFragment : BaseBrowserTvFragment() {
    override fun provideAdapter(eventsHandler: IEventsHandler, itemSize: Int): TvItemAdapter {
        return MediaTvItemAdapter(when ((viewModel as MediaBrowserViewModel).category) {
            CATEGORY_SONGS -> AbstractMediaWrapper.TYPE_AUDIO
            CATEGORY_ALBUMS -> AbstractMediaWrapper.TYPE_ALBUM
            CATEGORY_ARTISTS -> AbstractMediaWrapper.TYPE_ARTIST
            CATEGORY_GENRES -> AbstractMediaWrapper.TYPE_GENRE
            else -> AbstractMediaWrapper.TYPE_VIDEO
        }, this, itemSize)
    }

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

        viewModel = getMediaBrowserModel(arguments?.getLong(CATEGORY, CATEGORY_SONGS) ?: CATEGORY_SONGS)

        viewModel.currentItem = if (savedInstanceState != null) savedInstanceState.getParcelable<Parcelable>(ITEM) as? MediaLibraryItem
        else requireActivity().intent.getParcelableExtra<Parcelable>(ITEM) as? MediaLibraryItem

        (viewModel.provider as MedialibraryProvider<*>).pagedList.observe(this, Observer { items ->
            submitList(items)

            binding.empty = items.isEmpty()

            //headers
            val nbColumns = if ((viewModel as MediaBrowserViewModel).sort == AbstractMedialibrary.SORT_ALPHA || (viewModel as MediaBrowserViewModel).sort == AbstractMedialibrary.SORT_DEFAULT) 9 else 1

            headerList.layoutManager = GridLayoutManager(requireActivity(), nbColumns)
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
            binding.loading = it
            animationDelegate.setVisibility(binding.loadingBar, if (it) View.VISIBLE else View.GONE)
        })
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        launch {
            if ((viewModel as MediaBrowserViewModel).category == CATEGORY_VIDEOS && !Settings.getInstance(requireContext()).getBoolean(FORCE_PLAY_ALL, true)) {
                TvUtil.playMedia(requireActivity(), item as AbstractMediaWrapper)
            } else {
                TvUtil.openMediaFromPaged(requireActivity(), item, viewModel.provider as MedialibraryProvider<out MediaLibraryItem>)
            }
        }
    }
}
