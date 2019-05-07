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
import android.os.Bundle
import android.os.Message
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager.widget.ViewPager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.PlaylistActivity
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.gui.view.RecyclerSectionItemDecoration
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.KEY_ARTISTS_SHOW_ALL
import org.videolan.vlc.util.KEY_AUDIO_CURRENT_TAB
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.WeakHandler
import org.videolan.vlc.viewmodels.paged.*
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class AudioBrowserFragment : BaseAudioBrowser(), SwipeRefreshLayout.OnRefreshListener {

    private lateinit var songsAdapter: AudioBrowserAdapter
    private lateinit var artistsAdapter: AudioBrowserAdapter
    private lateinit var albumsAdapter: AudioBrowserAdapter
    private lateinit var genresAdapter: AudioBrowserAdapter

    private lateinit var artistModel: PagedArtistsModel
    private lateinit var albumModel: PagedAlbumsModel
    private lateinit var tracksModel: PagedTracksModel
    private lateinit var genresModel: PagedGenresModel

    private lateinit var emptyView: TextView
    private lateinit var medialibrarySettingsBtn: Button
    private val lists = arrayOfNulls<RecyclerView>(MODE_TOTAL)
    private lateinit var models: Array<MLPagedModel<MediaLibraryItem>>
    private lateinit var settings: SharedPreferences
    private lateinit var fastScroller: FastScroller

    /**
     * Handle changes on the list
     */
    private val mHandler = AudioBrowserHandler(this)

    /*
     * Disable Swipe Refresh while scrolling horizontally
     */
    private val mSwipeFilter = View.OnTouchListener { _, event ->
        swipeRefreshLayout?.isEnabled = event.action == MotionEvent.ACTION_UP
        false
    }

    override fun hasTabs(): Boolean {
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!::settings.isInitialized) settings = Settings.getInstance(requireContext())
        if (!::models.isInitialized) setupModels()
        if (settings.getBoolean("audio_resume_card", true)) (requireActivity() as AudioPlayerContainerActivity).proposeCard()
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
            val activity = requireActivity()
            val intent = Intent(activity.applicationContext, SecondaryActivity::class.java)
            intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
            startActivity(intent)
            activity.setResult(PreferencesActivity.RESULT_RESTART)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        for (i in 0 until MODE_TOTAL) lists[i] = viewPager!!.getChildAt(i) as RecyclerView

        val titles = arrayOf(getString(R.string.artists), getString(R.string.albums), getString(R.string.tracks), getString(R.string.genres))
        viewPager!!.offscreenPageLimit = MODE_TOTAL - 1
        viewPager!!.adapter = AudioPagerAdapter(lists as Array<View>, titles)
        val tabPosition = settings.getInt(KEY_AUDIO_CURRENT_TAB, 0)
        viewPager!!.currentItem = tabPosition
        val positions = savedInstanceState?.getIntegerArrayList(KEY_LISTS_POSITIONS)
        for (i in 0 until MODE_TOTAL) {
            val llm = LinearLayoutManager(activity)
            llm.recycleChildrenOnDetach = true
            val list = lists[i] as RecyclerView
            list.layoutManager = llm
            list.adapter = adapters[i]
            if (positions != null) list.scrollToPosition(positions[i])
            list.addOnScrollListener(scrollListener)
            list.addItemDecoration(RecyclerSectionItemDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_height), true, models[i]))
        }
        viewPager!!.setOnTouchListener(mSwipeFilter)
        swipeRefreshLayout?.setOnRefreshListener(this)
        viewPager!!.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                viewModel = models[viewPager!!.currentItem]
            }

        })
        viewModel = models[viewPager!!.currentItem]
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val positions = ArrayList<Int>(MODE_TOTAL)
        for (i in 0 until MODE_TOTAL) {
            positions.add((lists[i]!!.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition())
        }
        outState.putIntegerArrayList(KEY_LISTS_POSITIONS, positions)
        super.onSaveInstanceState(outState)
    }

    private fun setupModels() {
        val current = settings.getInt(KEY_AUDIO_CURRENT_TAB, 0)
        for (pass in 0..1) {
            if ((pass != 0) xor (current == MODE_ARTIST) && !::artistsAdapter.isInitialized) {
                artistModel = ViewModelProviders.of(requireActivity(), PagedArtistsModel.Factory(requireContext(), settings.getBoolean(KEY_ARTISTS_SHOW_ALL, false))).get(PagedArtistsModel::class.java)
                artistsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_ARTIST, this)
            }
            if ((pass != 0) xor (current == MODE_ALBUM) && !::albumsAdapter.isInitialized) {
                albumModel = ViewModelProviders.of(requireActivity(), PagedAlbumsModel.Factory(requireContext(), null)).get(PagedAlbumsModel::class.java)
                albumsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_ALBUM, this)
            }
            if ((pass != 0) xor (current == MODE_SONG) && !::songsAdapter.isInitialized) {
                tracksModel = ViewModelProviders.of(requireActivity(), PagedTracksModel.Factory(requireContext(), null)).get(PagedTracksModel::class.java)
                songsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this)
            }
            if ((pass != 0) xor (current == MODE_GENRE) && !::genresAdapter.isInitialized) {
                genresModel = ViewModelProviders.of(requireActivity(), PagedGenresModel.Factory(requireContext())).get(PagedGenresModel::class.java)
                genresAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_GENRE, this)
            }
        }
        adapters = arrayOf(artistsAdapter, albumsAdapter, songsAdapter, genresAdapter)
        models = arrayOf(artistModel as MLPagedModel<MediaLibraryItem>, albumModel as MLPagedModel<MediaLibraryItem>, tracksModel as MLPagedModel<MediaLibraryItem>, genresModel as MLPagedModel<MediaLibraryItem>)
        for (pass in 0..1) {
            for (i in models.indices) {
                if ((pass == 0) xor (current == i)) continue
                models[i].pagedList.observe(this, Observer { items -> if (items != null) adapters[i].submitList(items) })
                models[i].loading.observe(this, Observer { loading ->
                    if (loading == null || viewPager!!.currentItem != i) return@Observer
                    if (loading)
                        mHandler.sendEmptyMessageDelayed(SET_REFRESHING, 300)
                    else
                        mHandler.sendEmptyMessage(UNSET_REFRESHING)
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setFabPlayShuffleAllVisibility()
        fabPlay?.setImageResource(R.drawable.ic_fab_shuffle)
    }

    override fun onPrepareOptionsMenu(menu: Menu?) {
        super.onPrepareOptionsMenu(menu)
        menu?.findItem(R.id.ml_menu_last_playlist)?.isVisible = true
    }

    override fun onFabPlayClick(view: View) {
        MediaUtils.playAll(view.context, tracksModel, 0, true)
    }

    private fun setFabPlayShuffleAllVisibility() {
        setFabPlayVisibility(songsAdapter.itemCount > 2)
    }

    override fun onRefresh() {
        (requireActivity() as ContentActivity).closeSearchView()
        requireContext().reloadLibrary()
    }

    override fun getTitle(): String = getString(R.string.audio)

    override fun enableSearchOption(): Boolean {
        return true
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (getCurrentAdapter() != null && getCurrentAdapter()!!.isEmpty) View.VISIBLE else View.GONE
        medialibrarySettingsBtn.visibility = if (getCurrentAdapter() != null && getCurrentAdapter()!!.isEmpty) View.VISIBLE else View.GONE
        setFabPlayShuffleAllVisibility()
    }

    override fun onPageSelected(position: Int) {
        updateEmptyView()
        setFabPlayShuffleAllVisibility()
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        super.onTabSelected(tab)
        fastScroller.setRecyclerView(lists[tab.position]!!, models[tab.position])
        settings.edit().putInt(KEY_AUDIO_CURRENT_TAB, tab.position).apply()
        val loading = viewModel.loading.value
        if (loading == null || !loading)
            mHandler.sendEmptyMessage(UNSET_REFRESHING)
        else
            mHandler.sendEmptyMessage(SET_REFRESHING)
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        super.onTabUnselected(tab)
        onDestroyActionMode(lists[tab.position]!!.adapter as AudioBrowserAdapter?)
        models[tab.position].restore()
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
        lists[tab.position]?.smoothScrollToPosition(0)
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            super.onClick(v, position, item)
            return
        }
        if (item.itemType == MediaLibraryItem.TYPE_MEDIA) {
            MediaUtils.openMedia(activity, item as MediaWrapper)
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
        if (getCurrentAdapter() != null && adapter === getCurrentAdapter()) {
            swipeRefreshLayout?.isEnabled = (getCurrentRV().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() <= 0
            updateEmptyView()
            fastScroller.setRecyclerView(getCurrentRV(), viewModel)
        } else
            setFabPlayShuffleAllVisibility()
    }



    override fun getCurrentRV(): RecyclerView {
        return lists[viewPager!!.currentItem]!!
    }

    override fun getCurrentAdapter(): AudioBrowserAdapter? {
        return adapters[viewPager!!.currentItem]
    }

    private class AudioBrowserHandler internal constructor(owner: AudioBrowserFragment) : WeakHandler<AudioBrowserFragment>(owner) {

        override fun handleMessage(msg: Message) {
            val fragment = owner ?: return
            when (msg.what) {
                SET_REFRESHING -> fragment.swipeRefreshLayout?.isRefreshing = true
                UNSET_REFRESHING -> {
                    removeMessages(SET_REFRESHING)
                    fragment.swipeRefreshLayout?.isRefreshing = false
                }
                UPDATE_EMPTY_VIEW -> fragment.updateEmptyView()
            }
        }
    }

    fun updateArtists() {
        artistModel.showAll(settings.getBoolean(KEY_ARTISTS_SHOW_ALL, false))
        artistModel.refresh()
    }

    override fun allowedToExpand(): Boolean {
        return getCurrentRV().scrollState == RecyclerView.SCROLL_STATE_IDLE
    }

    companion object {
        val TAG = "VLC/AudioBrowserFragment"

        private const val KEY_LISTS_POSITIONS = "key_lists_position"
        private const val SET_REFRESHING = 103
        private const val UNSET_REFRESHING = 104
        private const val UPDATE_EMPTY_VIEW = 105
        private const val MODE_ARTIST = 0
        private const val MODE_ALBUM = 1
        private const val MODE_SONG = 2
        private const val MODE_GENRE = 3
        private const val MODE_TOTAL = 4 // Number of audio mProvider modes

        const val TAG_ITEM = "ML_ITEM"
    }
}
