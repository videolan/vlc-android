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
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.CTX_PLAY_ALL
import org.videolan.resources.util.parcelable
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.HeaderMediaListActivity
import org.videolan.vlc.gui.dialogs.*
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addFavoritesIcon
import org.videolan.vlc.gui.helpers.UiTools.removeDrawables
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
class AudioAlbumsSongsFragment : BaseAudioBrowser<AlbumSongsViewModel>(), SwipeRefreshLayout.OnRefreshListener {

    private var spacing: Int = 0

    private lateinit var lists: Array<RecyclerView>
    private lateinit var songsAdapter: AudioBrowserAdapter
    private lateinit var albumsAdapter: AudioBrowserAdapter
    private lateinit var fastScroller: FastScroller
    private lateinit var audioPagerAdapter: AudioPagerAdapter

    override val hasTabs = true
    private var fromAlbums = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val item = savedInstanceState?.parcelable(AudioBrowserFragment.TAG_ITEM)
                ?: arguments?.parcelable<MediaLibraryItem>(AudioBrowserFragment.TAG_ITEM)
        viewModel = getViewModel(item!!)
        fromAlbums = savedInstanceState?.getBoolean(HeaderMediaListActivity.ARTIST_FROM_ALBUM) ?: arguments?.getBoolean(HeaderMediaListActivity.ARTIST_FROM_ALBUM, false) ?: false
    }

    override fun getTitle(): String = viewModel.parent.title

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.audio_albums_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spacing = resources.getDimension(R.dimen.kl_small).toInt()
        val itemSize = RecyclerSectionItemGridDecoration.getItemSize(requireActivity().getScreenWidth(), nbColumns, spacing, 16.dp)

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
        audioPagerAdapter = AudioPagerAdapter(arrayOf(viewPager.getChildAt(MODE_ALBUM), viewPager.getChildAt(MODE_SONG)), titles)
        @Suppress("UNCHECKED_CAST")
        viewPager.adapter = audioPagerAdapter

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
        fabPlay?.contentDescription = getString(R.string.play)
        viewModel.albumsProvider.pagedList.observe(requireActivity()) { albums ->
            @Suppress("UNCHECKED_CAST")
            (albums as? PagedList<MediaLibraryItem>)?.let { albumsAdapter.submitList(it) }
            if (viewModel.albumsProvider.loading.value == false && empty && !viewModel.isFiltering()) currentTab = 1
        }
        viewModel.tracksProvider.pagedList.observe(requireActivity()) { tracks ->
            @Suppress("UNCHECKED_CAST")
            (tracks as? PagedList<MediaLibraryItem>)?.let { songsAdapter.submitList(it) }
        }
        @Suppress("UNCHECKED_CAST")
        for (i in 0..1) setupLayoutManager(viewModel.providersInCard[i], lists[i], viewModel.providers[i] as MedialibraryProvider<MediaLibraryItem>, adapters[i], spacing)
        viewModel.albumsProvider.loading.observe(requireActivity()) { loading ->
            if (!loading) {
                fastScroller.setRecyclerView(getCurrentRV(), viewModel.providers[currentTab])
            }
            setRefreshing(loading)
        }

        viewModel.albumsProvider.liveHeaders.observe(viewLifecycleOwner) {
            lists[0].invalidateItemDecorations()
        }
        viewModel.tracksProvider.liveHeaders.observe(viewLifecycleOwner) {
            lists[1].invalidateItemDecorations()
        }
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
        menu.findItem(R.id.ml_menu_display_options).isVisible = true
        menu.findItem(R.id.ml_menu_sortby).isVisible = false
        sortMenuTitles()
        reopenSearchIfNeeded()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ml_menu_display_list, R.id.ml_menu_display_grid -> {
                viewModel.providersInCard[currentTab] = item.itemId == R.id.ml_menu_display_grid
                @Suppress("UNCHECKED_CAST")
                setupLayoutManager(viewModel.providersInCard[currentTab], lists[currentTab], viewModel.providers[currentTab] as MedialibraryProvider<MediaLibraryItem>, adapters[currentTab], spacing)
                lists[currentTab].adapter = adapters[currentTab]
                activity?.invalidateOptionsMenu()
                Settings.getInstance(requireActivity()).putSingle(viewModel.displayModeKeys[currentTab], item.itemId == R.id.ml_menu_display_grid)
                true
            }
            R.id.play_all -> {
                onFabPlayClick(fastScroller)
                true
            }
            R.id.ml_menu_display_options -> {
                //filter all sorts and keep only applicable ones
                val sorts = arrayListOf(Medialibrary.SORT_ALPHA, Medialibrary.SORT_FILENAME, Medialibrary.SORT_ARTIST, Medialibrary.SORT_ALBUM, Medialibrary.SORT_DURATION, Medialibrary.SORT_RELEASEDATE, Medialibrary.SORT_LASTMODIFICATIONDATE, Medialibrary.SORT_FILESIZE, Medialibrary.NbMedia).filter {
                    viewModel.providers[currentTab].canSortBy(it)
                }
                //Open the display settings Bottom sheet
                DisplaySettingsDialog.newInstance(
                        displayInCards = viewModel.providersInCard[currentTab],
                        onlyFavs = viewModel.providers[currentTab].onlyFavorites,
                        sorts = sorts,
                        currentSort = viewModel.providers[currentTab].sort,
                        currentSortDesc = viewModel.providers[currentTab].desc
                )
                        .show(requireActivity().supportFragmentManager, "DisplaySettingsDialog")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDisplaySettingChanged(key: String, value: Any) {
        when (key) {
            DISPLAY_IN_CARDS -> {
                viewModel.providersInCard[currentTab] = value as Boolean
                @Suppress("UNCHECKED_CAST")
                setupLayoutManager(viewModel.providersInCard[currentTab], lists[currentTab], viewModel.providers[currentTab] as MedialibraryProvider<MediaLibraryItem>, adapters[currentTab], spacing)
                lists[currentTab].adapter = adapters[currentTab]
                activity?.invalidateOptionsMenu()
                Settings.getInstance(requireActivity()).putSingle(viewModel.displayModeKeys[currentTab], value)
            }
            ONLY_FAVS -> {
                viewModel.providers[currentTab].showOnlyFavs(value as Boolean)
                viewModel.refresh()
                updateTabs()
            }
            CURRENT_SORT -> {
                @Suppress("UNCHECKED_CAST") val sort = value as Pair<Int, Boolean>
                viewModel.providers[currentTab].sort = sort.first
                viewModel.providers[currentTab].desc = sort.second
                viewModel.providers[currentTab].saveSort()
                viewModel.refresh()
            }
        }
    }

    /**
     * Setup the tabs custom views
     *
     */
    private fun updateTabs() {
        for (i in 0 until tabLayout!!.tabCount) {
            val tab = tabLayout!!.getTabAt(i)
            val view = tab?.customView ?: View.inflate(requireActivity(), R.layout.audio_tab, null)
            val title = view.findViewById<TextView>(R.id.tab_title)
            title.text = audioPagerAdapter.getPageTitle(i)
            if (viewModel.providers[i].onlyFavorites) title.addFavoritesIcon() else title.removeDrawables()
            tab?.customView = view
        }
    }

    override fun setupTabLayout() {
        super.setupTabLayout()
        updateTabs()
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
            val i = Intent(activity, HeaderMediaListActivity::class.java)
            i.putExtra(AudioBrowserFragment.TAG_ITEM, item)
            if (fromAlbums) i.flags = i.flags or Intent.FLAG_ACTIVITY_NO_HISTORY
            startActivity(i)
        } else {
            if (inSearchMode()) UiTools.setKeyboardVisibility(v, false)
            if (Settings.getInstance(requireContext()).getBoolean(FORCE_PLAY_ALL_AUDIO, false))
                MediaUtils.playAll(activity, viewModel.tracksProvider, position, false)
            else
                MediaUtils.openMedia(v.context, item as MediaWrapper)
        }
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
