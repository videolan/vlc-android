/*****************************************************************************
 * AudioAlbumsSongsFragment.java
 *
 * Copyright © 2011-2016 VLC authors and VideoLAN
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
 */

package org.videolan.vlc.gui.audio

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.media.Album
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.PlaylistActivity
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.gui.view.RecyclerSectionItemDecoration
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.Util
import org.videolan.vlc.viewmodels.paged.MLPagedModel
import org.videolan.vlc.viewmodels.paged.PagedAlbumsModel
import org.videolan.vlc.viewmodels.paged.PagedTracksModel

/* All subclasses of Fragment must include a public empty constructor. */
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class AudioAlbumsSongsFragment : BaseAudioBrowser(), androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener {

    private var handler = Handler(Looper.getMainLooper())
    private lateinit var albumModel: PagedAlbumsModel
    private lateinit var tracksModel: PagedTracksModel

    private lateinit var lists: Array<RecyclerView>
    private lateinit var audioModels: Array<MLPagedModel<MediaLibraryItem>>
    private lateinit var songsAdapter: AudioBrowserAdapter
    private lateinit var albumsAdapter: AudioBrowserAdapter
    private lateinit var fastScroller: FastScroller

    private lateinit var mItem: MediaLibraryItem

    /*
     * Disable Swipe Refresh while scrolling horizontally
     */
    private val mSwipeFilter = View.OnTouchListener { v, event ->
        swipeRefreshLayout?.isEnabled = event.action == MotionEvent.ACTION_UP
        false
    }

    override fun hasTabs(): Boolean {
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mItem = if (savedInstanceState != null)
            savedInstanceState.getParcelable<Parcelable>(AudioBrowserFragment.TAG_ITEM) as MediaLibraryItem
        else
            arguments!!.getParcelable<Parcelable>(AudioBrowserFragment.TAG_ITEM) as MediaLibraryItem
        albumModel = ViewModelProviders.of(this, PagedAlbumsModel.Factory(requireContext(), mItem)).get(PagedAlbumsModel::class.java)
        tracksModel = ViewModelProviders.of(this, PagedTracksModel.Factory(requireContext(), mItem)).get(PagedTracksModel::class.java)
        audioModels = arrayOf(albumModel as MLPagedModel<MediaLibraryItem>, tracksModel as MLPagedModel<MediaLibraryItem>)
    }

    override fun getTitle(): String = mItem.title



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.audio_albums_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val albumsList = viewPager!!.getChildAt(MODE_ALBUM) as RecyclerView
        val songsList = viewPager!!.getChildAt(MODE_SONG) as RecyclerView

        lists = arrayOf(albumsList, songsList)
        val titles = arrayOf(getString(R.string.albums), getString(R.string.songs))
        albumsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_ALBUM, this)
        songsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this)
        adapters = arrayOf<AudioBrowserAdapter>(albumsAdapter, songsAdapter)

        albumsList.addItemDecoration(RecyclerSectionItemDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_height), true, albumModel))
        songsList.addItemDecoration(RecyclerSectionItemDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_height), true, tracksModel))

        songsList.adapter = songsAdapter
        albumsList.adapter = albumsAdapter
        viewPager!!.offscreenPageLimit = MODE_TOTAL - 1
        viewPager!!.adapter = AudioPagerAdapter(lists as Array<View>, titles)

        fastScroller = view.rootView.findViewById<View>(R.id.songs_fast_scroller) as FastScroller
        fastScroller.attachToCoordinator(view.rootView.findViewById<View>(R.id.appbar) as AppBarLayout, view.rootView.findViewById<View>(R.id.coordinator) as CoordinatorLayout, view.rootView.findViewById<View>(R.id.fab) as FloatingActionButton)

        viewPager!!.setOnTouchListener(mSwipeFilter)
        viewModel = audioModels[viewPager!!.currentItem]
        viewPager!!.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                viewModel = audioModels[viewPager!!.currentItem]
            }

        })



        swipeRefreshLayout = view.findViewById(R.id.swipeLayout)
        swipeRefreshLayout!!.setOnRefreshListener(this)
        for (rv in lists) {
            rv.layoutManager = LinearLayoutManager(view.context)
            val llm = LinearLayoutManager(activity)
            llm.recycleChildrenOnDetach = true
            rv.layoutManager = llm
            rv.addOnScrollListener(scrollListener)

        }
        fabPlay?.setImageResource(R.drawable.ic_fab_play)
        albumModel.pagedList.observe(this, Observer { albums -> if (albums != null) albumsAdapter.submitList(albums as PagedList<MediaLibraryItem>) })
        tracksModel.pagedList.observe(this, Observer { tracks ->
            if (tracks != null) {
                if (tracks.isEmpty() && !tracksModel.isFiltering()) {
                    val activity = activity
                    activity?.finish()
                } else
                    songsAdapter.submitList(tracks as PagedList<MediaLibraryItem>)
            }
        })
    }

    override fun onRefresh() {
        (requireActivity() as ContentActivity).closeSearchView()
        albumModel.refresh()
        tracksModel.refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(AudioBrowserFragment.TAG_ITEM, mItem)
        super.onSaveInstanceState(outState)
    }

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {
        super.onUpdateFinished(adapter)
        handler.post {
            swipeRefreshLayout?.isRefreshing = false
            val albums = albumModel.pagedList.value
            if (Util.isListEmpty(albums) && !viewModel.isFiltering())
                viewPager!!.currentItem = 1
            fastScroller.setRecyclerView(getCurrentRV(), viewModel)
        }
    }

    override fun clear() {
        albumsAdapter.clear()
        songsAdapter.clear()
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            super.onClick(v, position, item)
            return
        }
        if (item is Album) {
            val i = Intent(activity, PlaylistActivity::class.java)
            i.putExtra(AudioBrowserFragment.TAG_ITEM, item)
            startActivity(i)
        } else
            MediaUtils.openMedia(v.context, item as MediaWrapper)
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        super.onTabUnselected(tab)
        audioModels[tab.position].restore()
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
        lists[tab.position].smoothScrollToPosition(0)
        fastScroller.setRecyclerView(lists[tab.position], audioModels[tab.position])
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        super.onTabSelected(tab)
        fastScroller.setRecyclerView(lists[tab.position], audioModels[tab.position])
    }

    override fun getCurrentRV(): RecyclerView {
        return lists[viewPager!!.currentItem]
    }

    override fun setFabPlayVisibility(enable: Boolean) {
        super.setFabPlayVisibility(enable)
    }

    override fun onFabPlayClick(view: View) {
        if (viewPager!!.currentItem == 0)
            MediaUtils.playAlbums(activity, albumModel.provider, 0, false)
        else
            MediaUtils.playAll(view.context, tracksModel.provider, 0, false)
    }

    companion object {

        private val TAG = "VLC/AudioAlbumsSongsFragment"

        private val MODE_ALBUM = 0
        private val MODE_SONG = 1
        private val MODE_TOTAL = 2 // Number of audio mProvider modes
    }
}
