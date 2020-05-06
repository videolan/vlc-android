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
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.CTX_PLAY_ALL
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.PlaylistActivity
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.gui.view.RecyclerSectionItemGridDecoration
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
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

        val item = savedInstanceState?.getParcelable<MediaLibraryItem>(AudioBrowserFragment.TAG_ITEM)
                ?: arguments?.getParcelable<MediaLibraryItem>(AudioBrowserFragment.TAG_ITEM)
        viewModel = getViewModel(item!!)
    }

    override fun getTitle(): String = viewModel.parent.title

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.audio_albums_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spacing = resources.getDimension(R.dimen.kl_small).toInt()
        val itemSize = RecyclerSectionItemGridDecoration.getItemSize(requireActivity().getScreenWidth(), nbColumns, spacing)

        val albumsList = viewPager.getChildAt(MODE_ALBUM).findViewById(R.id.audio_list) as RecyclerView
        val songsList = viewPager.getChildAt(MODE_SONG).findViewById(R.id.audio_list) as RecyclerView

        lists = arrayOf(albumsList, songsList)
        val titles = arrayOf(getString(R.string.albums), getString(R.string.songs))
        albumsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_ALBUM, this, cardSize = if (viewModel.providersInCard[0]) itemSize else -1)
        songsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, cardSize = if (viewModel.providersInCard[1]) itemSize else -1)
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
        viewModel.albumsProvider.pagedList.observe(requireActivity(), Observer { albums ->
            @Suppress("UNCHECKED_CAST")
            (albums as? PagedList<MediaLibraryItem>)?.let { albumsAdapter.submitList(it) }
            if (viewModel.albumsProvider.loading.value == false && empty && !viewModel.isFiltering()) currentTab = 1
        })
        viewModel.tracksProvider.pagedList.observe(requireActivity(), Observer { tracks ->
            @Suppress("UNCHECKED_CAST")
            (tracks as? PagedList<MediaLibraryItem>)?.let { songsAdapter.submitList(it) }
        })
        for (i in 0..1) setupLayoutManager(viewModel.providersInCard[i], lists[i], viewModel.providers[i] as MedialibraryProvider<MediaLibraryItem>, adapters[i], spacing)
        viewModel.albumsProvider.loading.observe(requireActivity(), Observer { loading ->
            if (!loading) {
                fastScroller.setRecyclerView(getCurrentRV(), viewModel.providers[currentTab])
            }
            setRefreshing(loading)
        })
    }

    override fun sortBy(sort: Int) {
        viewModel.providers[currentTab].sort(sort)
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
                setupLayoutManager(viewModel.providersInCard[currentTab], lists[currentTab], viewModel.providers[currentTab] as MedialibraryProvider<MediaLibraryItem>, adapters[currentTab], spacing)
                lists[currentTab].adapter = adapters[currentTab]
                activity?.invalidateOptionsMenu()
                Settings.getInstance(requireActivity()).putSingle(viewModel.displayModeKeys[currentTab], item.itemId == R.id.ml_menu_display_grid)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun clear() {
        adapters.forEach { it.clear() }
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

    override fun onCtxAction(position: Int, option: Long) {
        if (option == CTX_PLAY_ALL) MediaUtils.playAll(activity, viewModel.tracksProvider, position, false)
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
            MediaUtils.playAll(activity, viewModel.tracksProvider, 0, false)
    }
}
