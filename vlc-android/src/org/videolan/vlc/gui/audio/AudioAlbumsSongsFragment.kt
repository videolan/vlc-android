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
import android.view.*
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.media.AbstractAlbum
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.PlaylistActivity
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.gui.view.RecyclerSectionItemDecoration
import org.videolan.vlc.gui.view.RecyclerSectionItemGridDecoration
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.util.CTX_PLAY_ALL
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.Util
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.viewmodels.mobile.AlbumSongsViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel

private const val TAG = "VLC/AudioAlbumsSongsFragment"

private const val MODE_ALBUM = 0
private const val MODE_SONG = 1
private const val MODE_TOTAL = 2 // Number of audio mProvider modes

/* All subclasses of Fragment must include a public empty constructor. */
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class AudioAlbumsSongsFragment : BaseAudioBrowser<AlbumSongsViewModel>(), SwipeRefreshLayout.OnRefreshListener {

    private var spacing: Int = 0
    private var handler = Handler(Looper.getMainLooper())

    private lateinit var lists: Array<RecyclerView>
    private lateinit var songsAdapter: AudioBrowserAdapter
    private lateinit var albumsAdapter: AudioBrowserAdapter
    private lateinit var fastScroller: FastScroller

    override val hasTabs = true

    /*
     * Disable Swipe Refresh while scrolling horizontally
     */
    private val swipeFilter = View.OnTouchListener { _, event ->
        swipeRefreshLayout.isEnabled = event.action == MotionEvent.ACTION_UP
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val item = if (savedInstanceState != null)
            savedInstanceState.getParcelable<Parcelable>(AudioBrowserFragment.TAG_ITEM) as MediaLibraryItem
        else
            arguments!!.getParcelable<Parcelable>(AudioBrowserFragment.TAG_ITEM) as MediaLibraryItem
        viewModel = getViewModel(item)
    }

    override fun getTitle(): String = viewModel.parent.title

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.audio_albums_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spacing = resources.getDimension(R.dimen.kl_small).toInt()
        val itemSize = if (viewModel.providersInCard[0]) RecyclerSectionItemGridDecoration.getItemSize(requireActivity().getScreenWidth(), nbColumns, spacing)
        else -1

        val albumsList = viewPager.getChildAt(MODE_ALBUM).findViewById(R.id.audio_list) as RecyclerView
        val songsList = viewPager.getChildAt(MODE_SONG).findViewById(R.id.audio_list) as RecyclerView

        lists = arrayOf(albumsList, songsList)
        val titles = arrayOf(getString(R.string.albums), getString(R.string.songs))
        albumsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_ALBUM, this, cardSize = if (viewModel.providersInCard[0]) itemSize else -1)
        songsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, cardSize = if (viewModel.providersInCard[0]) itemSize else -1)
        adapters = arrayOf(albumsAdapter, songsAdapter)


        songsList.adapter = songsAdapter
        albumsList.adapter = albumsAdapter
        viewPager.offscreenPageLimit = MODE_TOTAL - 1
        @Suppress("UNCHECKED_CAST")
        viewPager.adapter = AudioPagerAdapter(arrayOf(viewPager.getChildAt(MODE_ALBUM), viewPager.getChildAt(MODE_SONG)), titles)

        fastScroller = view.rootView.findViewById<View>(R.id.songs_fast_scroller) as FastScroller
        fastScroller.attachToCoordinator(view.rootView.findViewById<View>(R.id.appbar) as AppBarLayout, view.rootView.findViewById<View>(R.id.coordinator) as CoordinatorLayout, view.rootView.findViewById<View>(R.id.fab) as FloatingActionButton)

        viewPager.setOnTouchListener(swipeFilter)

        swipeRefreshLayout.setOnRefreshListener(this)
        for (rv in lists) {
            rv.layoutManager = LinearLayoutManager(view.context)
            val llm = LinearLayoutManager(activity)
            llm.recycleChildrenOnDetach = true
            rv.layoutManager = llm
            rv.addOnScrollListener(scrollListener)

        }
        fabPlay?.setImageResource(R.drawable.ic_fab_play)
        viewModel.albumsProvider.pagedList.observe(this, Observer { albums -> if (albums != null) albumsAdapter.submitList(albums as PagedList<MediaLibraryItem>) })
        viewModel.tracksProvider.pagedList.observe(this, Observer { tracks ->
            if (tracks != null) {
                @Suppress("UNCHECKED_CAST")
                if (tracks.isEmpty() && !viewModel.isFiltering()) {
                    val activity = activity
                    activity?.finish()
                } else
                    songsAdapter.submitList(tracks as PagedList<MediaLibraryItem>)
            }
        })
        for (i in 0..1) setupLayoutManager(i)
    }

    private fun setupLayoutManager(index: Int) {
        if (lists[index].itemDecorationCount > 0) {
            lists[index].removeItemDecorationAt(0)
        }
        when (viewModel.providersInCard[index]) {
            true -> {
                adapters[index].cardSize = RecyclerSectionItemGridDecoration.getItemSize(requireActivity().getScreenWidth(), nbColumns, spacing)
                displayListInGrid(lists[index], adapters[index], viewModel.providers[index] as MedialibraryProvider<MediaLibraryItem>, spacing)
            }
            else -> {
                adapters[index].cardSize = -1
                lists[index].addItemDecoration(RecyclerSectionItemDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_height), true, viewModel.providers[index]))
                lists[index].layoutManager = LinearLayoutManager(activity)
            }
        }
    }

    override fun getCurrentAdapter() = adapters[currentTab]

    override fun onRefresh() {
        (requireActivity() as ContentActivity).closeSearchView()
        viewModel.refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(AudioBrowserFragment.TAG_ITEM, viewModel.parent)
        super.onSaveInstanceState(outState)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        (viewModel.providers[currentTab]).run {
            menu.findItem(R.id.ml_menu_sortby).isVisible = canSortByName()
            menu.findItem(R.id.ml_menu_sortby_filename).isVisible = canSortByFileNameName()
            menu.findItem(R.id.ml_menu_sortby_artist_name).isVisible = canSortByArtist()
            menu.findItem(R.id.ml_menu_sortby_album_name).isVisible = canSortByAlbum()
            menu.findItem(R.id.ml_menu_sortby_length).isVisible = canSortByDuration()
            menu.findItem(R.id.ml_menu_sortby_date).isVisible = canSortByReleaseDate()
            menu.findItem(R.id.ml_menu_sortby_last_modified).isVisible = canSortByLastModified()
            menu.findItem(R.id.ml_menu_sortby_number).isVisible = false
            menu.findItem(R.id.ml_menu_display_grid).isVisible = !viewModel.providersInCard[currentTab]
            menu.findItem(R.id.ml_menu_display_list).isVisible = viewModel.providersInCard[currentTab]
        }
        sortMenuTitles()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ml_menu_display_list, R.id.ml_menu_display_grid -> {
                viewModel.providersInCard[currentTab] = item.itemId == R.id.ml_menu_display_grid
                setupLayoutManager(currentTab)
                lists[currentTab].adapter = adapters[currentTab]
                activity?.invalidateOptionsMenu()
                Settings.getInstance(requireActivity()).edit().putBoolean(viewModel.displayModeKeys[currentTab], item.itemId == R.id.ml_menu_display_grid).apply()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {
        super.onUpdateFinished(adapter)
        handler.post {
            swipeRefreshLayout.isRefreshing = false
            val albums = viewModel.albumsProvider.pagedList.value
            if (Util.isListEmpty(albums) && !viewModel.isFiltering()) currentTab = 1
            fastScroller.setRecyclerView(getCurrentRV(), viewModel.providers[currentTab])
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
        if (item is AbstractAlbum) {
            val i = Intent(activity, PlaylistActivity::class.java)
            i.putExtra(AudioBrowserFragment.TAG_ITEM, item)
            startActivity(i)
        } else
            MediaUtils.openMedia(v.context, item as AbstractMediaWrapper)
    }

    override fun onCtxAction(position: Int, option: Int) {
        if (option == CTX_PLAY_ALL) MediaUtils.playAll(requireContext(), viewModel.tracksProvider, position, false)
        else super.onCtxAction(position, option)
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        super.onTabUnselected(tab)
        viewModel.restore()
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
        lists[tab.position].smoothScrollToPosition(0)
        fastScroller.setRecyclerView(lists[tab.position], viewModel.providers[tab.position])
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        super.onTabSelected(tab)
        fastScroller.setRecyclerView(lists[tab.position], viewModel.providers[tab.position])
        activity?.invalidateOptionsMenu()
    }

    override fun getCurrentRV() = lists[currentTab]

    override fun onFabPlayClick(view: View) {
        if (currentTab == 0)
            MediaUtils.playAlbums(activity, viewModel.albumsProvider, 0, false)
        else
            MediaUtils.playAll(view.context, viewModel.tracksProvider, 0, false)
    }
}
