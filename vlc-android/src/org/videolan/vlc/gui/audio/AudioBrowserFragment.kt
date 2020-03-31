/*****************************************************************************
 * AudioBrowserFragment.java
 *
 * Copyright © 2011-2017 VLC authors and VideoLAN
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
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.os.Message
import android.util.SparseArray
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager.widget.ViewPager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.gui.*
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.gui.view.RecyclerSectionItemDecoration
import org.videolan.vlc.gui.view.RecyclerSectionItemGridDecoration
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.mobile.AudioBrowserViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class AudioBrowserFragment : BaseAudioBrowser<AudioBrowserViewModel>(), SwipeRefreshLayout.OnRefreshListener {

    private lateinit var songsAdapter: AudioBrowserAdapter
    private lateinit var artistsAdapter: AudioBrowserAdapter
    private lateinit var albumsAdapter: AudioBrowserAdapter
    private lateinit var genresAdapter: AudioBrowserAdapter

    private lateinit var emptyView: TextView
    private lateinit var medialibrarySettingsBtn: Button
    private val lists = mutableListOf<RecyclerView>()
    private lateinit var settings: SharedPreferences
    private lateinit var fastScroller: FastScroller
    override val hasTabs = true
    private var spacing = 0
    private var restorePositions: SparseArray<Int> = SparseArray()

    /**
     * Handle changes on the list
     */
    private val handler = AudioBrowserHandler(this)

    /*
     * Disable Swipe Refresh while scrolling horizontally
     */
    private val swipeFilter = View.OnTouchListener { _, event ->
        swipeRefreshLayout.isEnabled = event.action == MotionEvent.ACTION_UP
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        spacing = requireActivity().resources.getDimension(R.dimen.kl_small).toInt()


        if (!::settings.isInitialized) settings = Settings.getInstance(requireContext())
        if (!::songsAdapter.isInitialized) setupModels()
        if (viewModel.showResumeCard) {
            (requireActivity() as AudioPlayerContainerActivity).proposeCard()
            viewModel.showResumeCard = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.audio_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView = view.findViewById(R.id.no_media)
        medialibrarySettingsBtn = view.findViewById(R.id.button_nomedia)
        fastScroller = view.rootView.findViewById(R.id.songs_fast_scroller)
        fastScroller.attachToCoordinator(view.rootView.findViewById<View>(R.id.appbar) as AppBarLayout, view.rootView.findViewById<View>(R.id.coordinator) as CoordinatorLayout, view.rootView.findViewById<View>(R.id.fab) as FloatingActionButton)
        medialibrarySettingsBtn.setOnClickListener {
            activity?.run {
                val intent = Intent(applicationContext, SecondaryActivity::class.java)
                intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
                startActivity(intent)
                setResult(RESULT_RESTART)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewPager.let { viewPager ->
            val views = ArrayList<View>(MODE_TOTAL)
            for (i in 0 until MODE_TOTAL) {
                views.add(viewPager.getChildAt(i))
                lists.add(viewPager.getChildAt(i).findViewById(R.id.audio_list))
            }
            val titles = arrayOf(getString(R.string.artists), getString(R.string.albums), getString(R.string.tracks), getString(R.string.genres))
            viewPager.offscreenPageLimit = MODE_TOTAL - 1
            val audioPagerAdapter = AudioPagerAdapter(views.toTypedArray(), titles)
            @Suppress("UNCHECKED_CAST")
            viewPager.adapter = audioPagerAdapter
            val tabPosition = settings.getInt(KEY_AUDIO_CURRENT_TAB, 0)
            currentTab = tabPosition
            val position = savedInstanceState?.getIntegerArrayList(KEY_LISTS_POSITIONS)
            position?.withIndex()?.forEach {
                restorePositions.put(it.index, it.value)
            }
            for (i in 0 until MODE_TOTAL) {
                setupLayoutManager(i)
                (lists[i].layoutManager as LinearLayoutManager).recycleChildrenOnDetach = true
                val list = lists[i]
                list.adapter = adapters[i]
                list.addOnScrollListener(scrollListener)
                if (viewModel.providersInCard[i]) {
                    val lp = list.layoutParams
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                    list.layoutParams = lp
                }
            }
            viewPager.setOnTouchListener(swipeFilter)
            swipeRefreshLayout.setOnRefreshListener(this)
            viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(state: Int) {}

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {
                    activity?.invalidateOptionsMenu()
                }
            })
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val itemSize = RecyclerSectionItemGridDecoration.getItemSize(requireActivity().getScreenWidth(), nbColumns, spacing)
        for (i in 0 until MODE_TOTAL) {
            if (lists[i].layoutManager is GridLayoutManager) {
                val gridLayoutManager = lists[i].layoutManager as GridLayoutManager
                gridLayoutManager.spanCount = nbColumns
                lists[i].layoutManager = gridLayoutManager
                adapters[i].cardSize = itemSize
                adapters[i].notifyItemRangeChanged(gridLayoutManager.findFirstVisibleItemPosition(), gridLayoutManager.findLastVisibleItemPosition() - gridLayoutManager.findFirstVisibleItemPosition())
            }
        }
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

    override fun onSaveInstanceState(outState: Bundle) {
        val positions = ArrayList<Int>(MODE_TOTAL)
        for (i in 0 until MODE_TOTAL) {
            positions.add((lists[i].layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition())
        }
        outState.putIntegerArrayList(KEY_LISTS_POSITIONS, positions)
        super.onSaveInstanceState(outState)
    }

    private fun setupModels() {
        viewModel = getViewModel()

        val itemSize = RecyclerSectionItemGridDecoration.getItemSize(requireActivity().getScreenWidth(), nbColumns, spacing)

        artistsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_ARTIST, this, cardSize = if (viewModel.providersInCard[0]) itemSize else -1)
        albumsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_ALBUM, this, cardSize = if (viewModel.providersInCard[1]) itemSize else -1)
        songsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, cardSize = if (viewModel.providersInCard[2]) itemSize else -1)
        genresAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_GENRE, this)
        adapters = arrayOf(artistsAdapter, albumsAdapter, songsAdapter, genresAdapter)
        for ((index, provider) in viewModel.providers.withIndex()) {
            provider.pagedList.observe(this, Observer { items ->
                @Suppress("UNCHECKED_CAST")
                if (items != null) adapters[index].submitList(items as PagedList<MediaLibraryItem>?)
                restorePositions.get(index)?.let {
                    lists[index].scrollToPosition(it)
                    restorePositions.delete(index)
                }
            })
            provider.loading.observe(this, Observer { loading ->
                if (loading == null || currentTab != index) return@Observer
                if (loading)
                    handler.sendEmptyMessageDelayed(SET_REFRESHING, 300)
                else
                    handler.sendEmptyMessage(UNSET_REFRESHING)

                (activity as? MainActivity)?.refreshing = loading
                updateEmptyView()
            })
        }
    }

    override fun onStart() {
        super.onStart()
        setFabPlayShuffleAllVisibility()
        fabPlay?.setImageResource(R.drawable.ic_fab_shuffle)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.ml_menu_last_playlist)?.isVisible = true
        (viewModel.providers[currentTab]).run {
            menu.findItem(R.id.ml_menu_sortby).isVisible = canSortByName()
            menu.findItem(R.id.ml_menu_sortby_filename).isVisible = canSortByFileNameName()
            menu.findItem(R.id.ml_menu_sortby_artist_name).isVisible = canSortByArtist()
            menu.findItem(R.id.ml_menu_sortby_album_name).isVisible = canSortByAlbum()
            menu.findItem(R.id.ml_menu_sortby_length).isVisible = canSortByDuration()
            menu.findItem(R.id.ml_menu_sortby_date).isVisible = canSortByReleaseDate()
            menu.findItem(R.id.ml_menu_sortby_last_modified).isVisible = canSortByLastModified()
            menu.findItem(R.id.ml_menu_sortby_number).isVisible = false
            menu.findItem(R.id.ml_menu_display_grid).isVisible = currentTab in 0..2 && !viewModel.providersInCard[currentTab]
            menu.findItem(R.id.ml_menu_display_list).isVisible = currentTab in 0..2 && viewModel.providersInCard[currentTab]
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

    override fun sortBy(sort: Int) {
        viewModel.providers[currentTab].sort(sort)
    }

    override fun onFabPlayClick(view: View) {
        MediaUtils.playAll(view.context, viewModel.tracksProvider, 0, true)
    }

    private fun setFabPlayShuffleAllVisibility() {
        setFabPlayVisibility(songsAdapter.itemCount > 2)
    }

    override fun onRefresh() {
        (requireActivity() as ContentActivity).closeSearchView()
        requireContext().reloadLibrary()
        viewModel.setLoading()
    }

    override fun getTitle(): String = getString(R.string.audio)

    override fun enableSearchOption(): Boolean {
        return true
    }

    private fun updateEmptyView() {
        val emptyVisibility = getViewModel().providers[currentTab].isEmpty() && getViewModel().providers[currentTab].loading.value == false
        emptyView.visibility = if (emptyVisibility) View.VISIBLE else View.GONE
        medialibrarySettingsBtn.visibility = if (emptyVisibility) View.VISIBLE else View.GONE
        setFabPlayShuffleAllVisibility()
    }

    override fun onPageSelected(position: Int) {
        updateEmptyView()
        setFabPlayShuffleAllVisibility()
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        super.onTabSelected(tab)
        fastScroller.setRecyclerView(lists[tab.position], viewModel.providers[tab.position])
        settings.edit().putInt(KEY_AUDIO_CURRENT_TAB, tab.position).apply()
        if (viewModel.providers[currentTab].isRefreshing)
            handler.sendEmptyMessage(SET_REFRESHING)
        else
            handler.sendEmptyMessage(UNSET_REFRESHING)
        activity?.invalidateOptionsMenu()
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        super.onTabUnselected(tab)
        onDestroyActionMode(lists[tab.position].adapter as AudioBrowserAdapter?)
        viewModel.restore()
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
        lists[tab.position].smoothScrollToPosition(0)
    }

    override fun onCtxAction(position: Int, option: Int) {
        @Suppress("UNCHECKED_CAST")
        if (option == CTX_PLAY_ALL) MediaUtils.playAll(requireContext(), viewModel.providers[currentTab] as MedialibraryProvider<AbstractMediaWrapper>, position, false)
        else super.onCtxAction(position, option)
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            super.onClick(v, position, item)
            return
        }
        if (item.itemType == MediaLibraryItem.TYPE_MEDIA) {
            MediaUtils.openMedia(activity, item as AbstractMediaWrapper)
            return
        }
        val i: Intent
        when (item.itemType) {
            MediaLibraryItem.TYPE_ARTIST, MediaLibraryItem.TYPE_GENRE -> {
                i = Intent(activity, SecondaryActivity::class.java)
                i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUMS_SONGS)
                i.putExtra(TAG_ITEM, item)
            }
            MediaLibraryItem.TYPE_ALBUM -> {
                i = Intent(activity, PlaylistActivity::class.java)
                i.putExtra(TAG_ITEM, item)
            }
            else -> return
        }
        startActivity(i)
    }

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {
        super.onUpdateFinished(adapter)
        if (adapter === getCurrentAdapter()) {
            swipeRefreshLayout.isEnabled = (getCurrentRV().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() <= 0
            updateEmptyView()
            fastScroller.setRecyclerView(getCurrentRV(), viewModel.providers[currentTab])
        } else
            setFabPlayShuffleAllVisibility()
    }

    override fun getCurrentRV() = lists[currentTab]

    override fun getCurrentAdapter() = adapters[currentTab]

    private class AudioBrowserHandler internal constructor(owner: AudioBrowserFragment) : WeakHandler<AudioBrowserFragment>(owner) {
        override fun handleMessage(msg: Message) {
            val fragment = owner ?: return
            when (msg.what) {
                SET_REFRESHING -> fragment.swipeRefreshLayout.isRefreshing = true
                UNSET_REFRESHING -> {
                    removeMessages(SET_REFRESHING)
                    fragment.swipeRefreshLayout.isRefreshing = false
                }
                UPDATE_EMPTY_VIEW -> fragment.updateEmptyView()
            }
        }
    }

    override fun allowedToExpand() = getCurrentRV().scrollState == RecyclerView.SCROLL_STATE_IDLE

    companion object {
        val TAG = "VLC/AudioBrowserFragment"

        private const val KEY_LISTS_POSITIONS = "key_lists_position"
        private const val SET_REFRESHING = 103
        private const val UNSET_REFRESHING = 104
        private const val UPDATE_EMPTY_VIEW = 105
        private const val MODE_TOTAL = 4 // Number of audio lists

        const val TAG_ITEM = "ML_ITEM"
    }
}
