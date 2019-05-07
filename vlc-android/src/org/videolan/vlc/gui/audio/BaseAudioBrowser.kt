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

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.paged.MLPagedModel
import java.util.*

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
abstract class BaseAudioBrowser : MediaBrowserFragment<MLPagedModel<*>>(), IEventsHandler, CtxActionReceiver, ViewPager.OnPageChangeListener, TabLayout.OnTabSelectedListener {

    internal lateinit var adapters: Array<AudioBrowserAdapter>

    private var tabLayout: TabLayout? = null
    var viewPager: ViewPager? = null

    private val tcl = TabLayout.TabLayoutOnPageChangeListener(tabLayout)

    protected abstract fun getCurrentRV(): RecyclerView
    protected var adapter: AudioBrowserAdapter? = null

    open fun getCurrentAdapter(): AudioBrowserAdapter? {
        return adapter
    }

    private lateinit var layoutOnPageChangeListener: TabLayout.TabLayoutOnPageChangeListener

    internal val scrollListener: RecyclerView.OnScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                swipeRefreshLayout?.isEnabled = false
                return
            }
            val llm = getCurrentRV().layoutManager as LinearLayoutManager? ?: return
            swipeRefreshLayout?.isEnabled = llm.findFirstVisibleItemPosition() <= 0
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {}
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewPager = view.findViewById(R.id.pager)
        tabLayout = requireActivity().findViewById(R.id.sliding_tabs)
    }

    private fun setupTabLayout() {
        if (tabLayout == null || viewPager == null) return
        tabLayout?.setupWithViewPager(viewPager)
        if (!::layoutOnPageChangeListener.isInitialized) layoutOnPageChangeListener = TabLayout.TabLayoutOnPageChangeListener(tabLayout)
        viewPager?.addOnPageChangeListener(layoutOnPageChangeListener)
        tabLayout?.addOnTabSelectedListener(this)
        viewPager?.addOnPageChangeListener(this)
    }

    private fun unSetTabLayout() {
        if (tabLayout != null || viewPager == null) return
        viewPager?.removeOnPageChangeListener(layoutOnPageChangeListener)
        tabLayout?.removeOnTabSelectedListener(this)
        viewPager?.removeOnPageChangeListener(this)
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
        (requireActivity() as ContentActivity).closeSearchView()
    }

    override fun onTabReselected(tab: TabLayout.Tab) {}

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        tcl.onPageScrolled(position, positionOffset, positionOffsetPixels)
    }

    override fun onPageScrollStateChanged(state: Int) {
        tcl.onPageScrollStateChanged(state)
    }

    override fun onPageSelected(position: Int) {}

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
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
        val count = selection?.size
        if (count == 0) {
            stopActionMode()
            return false
        }
        val isSong = count == 1 && selection[0].itemType == MediaLibraryItem.TYPE_MEDIA
        menu.findItem(R.id.action_mode_audio_set_song).isVisible = isSong && AndroidDevices.isPhone
        menu.findItem(R.id.action_mode_audio_info).isVisible = count == 1
        menu.findItem(R.id.action_mode_audio_append).isVisible = PlaylistManager.hasMedia()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val list = getCurrentAdapter()?.multiSelectHelper?.getSelection()
        stopActionMode()
        if (list != null && list.isNotEmpty())
            runIO(Runnable {
                val tracks = ArrayList<MediaWrapper>()
                for (mediaItem in list)
                    tracks.addAll(Arrays.asList(*mediaItem.tracks))
                runOnMainThread(Runnable {
                    when (item.itemId) {
                        R.id.action_mode_audio_play -> MediaUtils.openList(activity, tracks, 0)
                        R.id.action_mode_audio_append -> MediaUtils.appendMedia(activity, tracks)
                        R.id.action_mode_audio_add_playlist -> UiTools.addToPlaylist(requireActivity(), tracks)
                        R.id.action_mode_audio_info -> showInfoDialog(list[0])
                        R.id.action_mode_audio_set_song -> AudioUtil.setRingtone(list[0] as MediaWrapper, requireActivity())
                    }
                })
            })
        return true
    }

    override fun onDestroyActionMode(actionMode: ActionMode) {
        onDestroyActionMode(getCurrentAdapter())
    }

    internal fun onDestroyActionMode(adapter: AudioBrowserAdapter?) {
        setFabPlayVisibility(true)
        actionMode = null
        adapter?.multiSelectHelper?.clearSelection()
    }

    override fun sortBy(sort: Int) {
        viewModel.canSortBy(sort)
        super.sortBy(sort)
    }

    override fun onRefresh() {}

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            getCurrentAdapter()?.multiSelectHelper?.toggleSelection(position)
            invalidateActionMode()
        }
    }

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
        if (actionMode != null) return false
        getCurrentAdapter()?.multiSelectHelper?.toggleSelection(position)
        startActionMode()
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
        val flags: Int = when (item.itemType) {
            MediaLibraryItem.TYPE_MEDIA -> CTX_TRACK_FLAGS
            MediaLibraryItem.TYPE_PLAYLIST -> CTX_PLAYLIST_FLAGS
            else -> CTX_AUDIO_FLAGS
        }
        if (actionMode == null) showContext(requireActivity(), this, position, item.title, flags)
    }


    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {
        MediaUtils.openList(activity, Arrays.asList(*item.tracks), 0)
    }

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {
        UiTools.updateSortTitles(this)
    }

    override fun onItemFocused(v: View, item: MediaLibraryItem) {

    }

    override fun onCtxAction(position: Int, option: Int) {
        val adapter = getCurrentAdapter()
        if (position >= adapter?.itemCount ?: 0) return
        val media = adapter?.getItem(position) ?: return
        when (option) {
            CTX_PLAY -> MediaUtils.playTracks(requireActivity(), media, 0)
            CTX_PLAY_ALL -> MediaUtils.playAll(requireContext(), viewModel as MLPagedModel<MediaWrapper>, position, false)
            CTX_INFORMATION -> showInfoDialog(media)
            CTX_DELETE -> removeItem(media)
            CTX_APPEND -> MediaUtils.appendMedia(requireActivity(), media.tracks)
            CTX_PLAY_NEXT -> MediaUtils.insertNext(requireActivity(), media.tracks)
            CTX_ADD_TO_PLAYLIST -> UiTools.addToPlaylist(requireActivity(), media.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            CTX_SET_RINGTONE -> AudioUtil.setRingtone(media as MediaWrapper, requireActivity())
        }
    }

}
