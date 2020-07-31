/*
 * *************************************************************************
 *  BaseAudioBrowser.java
 * **************************************************************************
 *  Copyright © 2016-2017 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.audio

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.tools.MultiSelectHelper
import org.videolan.tools.isStarted
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.AudioUtil.setRingtone
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.view.RecyclerSectionItemDecoration
import org.videolan.vlc.gui.view.RecyclerSectionItemGridDecoration
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.util.share
import org.videolan.vlc.viewmodels.MedialibraryViewModel
import java.util.*

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
abstract class BaseAudioBrowser<T : MedialibraryViewModel> : MediaBrowserFragment<T>(), IEventsHandler<MediaLibraryItem>, CtxActionReceiver, ViewPager.OnPageChangeListener, TabLayout.OnTabSelectedListener {

    var backgroundColor: Int = -1
    var listColor: Int = -1
    internal lateinit var adapters: Array<AudioBrowserAdapter>

    private var tabLayout: TabLayout? = null
    lateinit var viewPager: ViewPager

    var nbColumns = 2

    private val tcl = TabLayout.TabLayoutOnPageChangeListener(tabLayout)

    protected abstract fun getCurrentRV(): RecyclerView
    protected var adapter: AudioBrowserAdapter? = null

    open fun getCurrentAdapter() = adapter

    protected var currentTab
        get() = if (::viewPager.isInitialized) viewPager.currentItem else 0
        set(value) {
            if (::viewPager.isInitialized) viewPager.currentItem = value
        }

    private lateinit var layoutOnPageChangeListener: TabLayout.TabLayoutOnPageChangeListener

    internal val scrollListener: RecyclerView.OnScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                swipeRefreshLayout.isEnabled = false
                return
            }
            val llm = getCurrentRV().layoutManager as LinearLayoutManager? ?: return
            swipeRefreshLayout.isEnabled = llm.findFirstVisibleItemPosition() <= 0
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {}
    }

    fun displayListInGrid(list: RecyclerView, adapter: AudioBrowserAdapter, provider: MedialibraryProvider<MediaLibraryItem>, spacing: Int) {
        val gridLayoutManager = GridLayoutManager(requireActivity(), nbColumns)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                if (position == adapter.itemCount - 1) return 1
                if (provider.isFirstInSection(position + 1)) {

                    //calculate how many cell it must take
                    val firstSection = provider.getPositionForSection(position)
                    val nbItems = position - firstSection
                    if (BuildConfig.DEBUG)
                        Log.d("SongsBrowserFragment", "Position: " + position + " nb items: " + nbItems + " span: " + nbItems % nbColumns)

                    return nbColumns - nbItems % nbColumns
                }

                return 1
            }
        }
        list.layoutManager = gridLayoutManager
        list.addItemDecoration(RecyclerSectionItemGridDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_height), spacing, true, nbColumns, provider))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nbColumns = resources.getInteger(R.integer.mobile_card_columns)
        val typedValue = TypedValue()
        val theme: Resources.Theme = requireActivity().theme
        theme.resolveAttribute(R.attr.background_default_darker, typedValue, true)
        backgroundColor = typedValue.data

        theme.resolveAttribute(R.attr.background_default, typedValue, true)
        listColor = typedValue.data
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        nbColumns = resources.getInteger(R.integer.mobile_card_columns)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ViewPager>(R.id.pager)?.let { viewPager = it }
        tabLayout = requireActivity().findViewById(R.id.sliding_tabs)
    }

    fun setupLayoutManager(providerInCard: Boolean, list: RecyclerView, provider: MedialibraryProvider<MediaLibraryItem>, adapter: AudioBrowserAdapter, spacing: Int) {
        if (list.itemDecorationCount > 0) {
            list.removeItemDecorationAt(0)
        }
        when (providerInCard) {
            true -> {
                adapter.cardSize = RecyclerSectionItemGridDecoration.getItemSize(requireActivity().getScreenWidth(), nbColumns, spacing)
                displayListInGrid(list, adapter, provider, spacing)
            }
            else -> {
                adapter.cardSize = -1
                list.addItemDecoration(RecyclerSectionItemDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_height), true, provider))
                list.layoutManager = LinearLayoutManager(activity)
            }
        }
        val lp = list.layoutParams
        val dimension = requireActivity().resources.getDimension(R.dimen.default_content_width)
        lp.width = if (providerInCard) ViewGroup.LayoutParams.MATCH_PARENT else {
            dimension.toInt()
        }

        (list.parent as View).setBackgroundColor(if (!providerInCard && dimension != -1F) backgroundColor else ContextCompat.getColor(requireContext(), R.color.transparent))
        list.setBackgroundColor(if (!providerInCard && dimension != -1F) listColor else ContextCompat.getColor(requireContext(), R.color.transparent))
        list.layoutParams = lp
    }

    private fun setupTabLayout() {
        if (tabLayout == null || !::viewPager.isInitialized) return
        tabLayout?.setupWithViewPager(viewPager)
        if (!::layoutOnPageChangeListener.isInitialized) layoutOnPageChangeListener = TabLayout.TabLayoutOnPageChangeListener(tabLayout)
        viewPager.addOnPageChangeListener(layoutOnPageChangeListener)
        tabLayout?.addOnTabSelectedListener(this)
        viewPager.addOnPageChangeListener(this)
    }

    private fun unSetTabLayout() {
        if (tabLayout != null || !::viewPager.isInitialized) return
        viewPager.removeOnPageChangeListener(layoutOnPageChangeListener)
        tabLayout?.removeOnTabSelectedListener(this)
        viewPager.removeOnPageChangeListener(this)
    }

    override fun onStart() {
        super.onStart()
        setupTabLayout()
    }

    override fun onStop() {
        super.onStop()
        unSetTabLayout()
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        val activity = activity ?: return
        activity.invalidateOptionsMenu()
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        stopActionMode()
        (activity as? ContentActivity)?.closeSearchView()
    }

    override fun onTabReselected(tab: TabLayout.Tab) {}

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        tcl.onPageScrolled(position, positionOffset, positionOffsetPixels)
    }

    override fun onPageScrollStateChanged(state: Int) {
        tcl.onPageScrollStateChanged(state)
    }

    override fun onPageSelected(position: Int) {}

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ml_menu_last_playlist -> {
                MediaUtils.loadlastPlaylist(activity, PLAYLIST_TYPE_AUDIO)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.action_mode_audio_browser, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val selection = getCurrentAdapter()?.multiSelectHelper?.getSelection()
        val count = selection?.size ?: return false
        if (count == 0) {
            stopActionMode()
            return false
        }
        val isMedia = selection[0].itemType == MediaLibraryItem.TYPE_MEDIA
        val isSong = count == 1 && isMedia
        menu.findItem(R.id.action_mode_audio_set_song).isVisible = isSong && AndroidDevices.isPhone
        menu.findItem(R.id.action_mode_audio_info).isVisible = count == 1
        menu.findItem(R.id.action_mode_audio_append).isVisible = PlaylistManager.hasMedia()
        menu.findItem(R.id.action_mode_audio_delete).isVisible = isMedia
        menu.findItem(R.id.action_mode_audio_share).isVisible = isMedia
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (!isStarted()) return false
        val list = getCurrentAdapter()?.multiSelectHelper?.getSelection()
        stopActionMode()
        if (!list.isNullOrEmpty()) lifecycleScope.launch {
            if (isStarted()) when (item.itemId) {
                R.id.action_mode_audio_play -> MediaUtils.openList(activity, list.getTracks(), 0)
                R.id.action_mode_audio_append -> MediaUtils.appendMedia(activity, list.getTracks())
                R.id.action_mode_audio_add_playlist -> requireActivity().addToPlaylist(list.getTracks())
                R.id.action_mode_audio_info -> showInfoDialog(list[0])
                R.id.action_mode_audio_share -> requireActivity().share(list as List<MediaWrapper>)
                R.id.action_mode_audio_set_song -> activity?.setRingtone(list[0] as MediaWrapper)
                R.id.action_mode_audio_delete -> removeItems(list)
            }
        }
        return true
    }

    private suspend fun List<MediaLibraryItem>.getTracks() = withContext(Dispatchers.Default) {
        ArrayList<MediaWrapper>().apply {
            for (mediaItem in this@getTracks) addAll(Arrays.asList(*mediaItem.tracks))
        }
    }

    override fun onDestroyActionMode(actionMode: ActionMode) {
        onDestroyActionMode(getCurrentAdapter())
    }

    internal fun onDestroyActionMode(adapter: AudioBrowserAdapter?) {
        setFabPlayVisibility(true)
        actionMode = null
        adapter?.multiSelectHelper?.clearSelection()
    }

    override fun onRefresh() {}

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            getCurrentAdapter()?.multiSelectHelper?.toggleSelection(position)
            invalidateActionMode()
        }
    }

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
        getCurrentAdapter()?.multiSelectHelper?.toggleSelection(position, true)
        if (actionMode == null) startActionMode()
        return true
    }

    override fun onImageClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            onClick(v, position, item)
            return
        }
        onLongClick(v, position, item)
    }

    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {
        val flags: Long = when (item.itemType) {
            MediaLibraryItem.TYPE_MEDIA -> CTX_TRACK_FLAGS
            MediaLibraryItem.TYPE_PLAYLIST, MediaLibraryItem.TYPE_ALBUM -> CTX_PLAYLIST_ALBUM_FLAGS
            else -> CTX_AUDIO_FLAGS
        }
        if (actionMode == null) showContext(requireActivity(), this, position, item.title, flags)
    }

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {
        MediaUtils.openList(activity, listOf(*item.tracks), 0)
    }

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {
        sortMenuTitles()
        if (adapter == getCurrentAdapter()) {
            restoreMultiSelectHelper()
        }
    }

    override fun onItemFocused(v: View, item: MediaLibraryItem) {}

    override fun onCtxAction(position: Int, option: Long) {
        if (position >= getCurrentAdapter()?.itemCount ?: 0) return
        val media = getCurrentAdapter()?.getItem(position) ?: return
        when (option) {
            CTX_PLAY -> MediaUtils.playTracks(requireActivity(), media, 0)
            CTX_INFORMATION -> showInfoDialog(media)
            CTX_DELETE -> removeItem(media)
            CTX_APPEND -> MediaUtils.appendMedia(requireActivity(), media.tracks)
            CTX_PLAY_NEXT -> MediaUtils.insertNext(requireActivity(), media.tracks)
            CTX_ADD_TO_PLAYLIST -> requireActivity().addToPlaylist(media.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            CTX_SET_RINGTONE -> activity?.setRingtone(media as MediaWrapper)
            CTX_SHARE -> lifecycleScope.launch { (requireActivity() as AppCompatActivity).share(media as MediaWrapper) }
        }
    }

    protected val empty: Boolean
        get() = viewModel.isEmpty() && getCurrentAdapter()?.isEmpty != false

    override fun getMultiHelper(): MultiSelectHelper<T>? = getCurrentAdapter()?.multiSelectHelper as? MultiSelectHelper<T>
}
