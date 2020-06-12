/*
 * *************************************************************************
 *  PlaylistActivity.java
 * **************************************************************************
 *  Copyright © 2017 VLC authors and VideoLAN
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

package org.videolan.vlc.gui

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.view.ActionMode
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.tools.isStarted
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlaylistActivityBinding
import org.videolan.vlc.gui.audio.AudioBrowserAdapter
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.AudioUtil.setRingtone
import org.videolan.vlc.gui.helpers.FloatingActionButtonBehavior
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.UiTools.snackerConfirm
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getWritePermission
import org.videolan.vlc.gui.view.RecyclerSectionItemDecoration
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.interfaces.IListEventsHandler
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.mobile.PlaylistViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel
import java.lang.Runnable
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
open class PlaylistActivity : AudioPlayerContainerActivity(), IEventsHandler<MediaLibraryItem>, IListEventsHandler, ActionMode.Callback, View.OnClickListener, CtxActionReceiver {

    private lateinit var audioBrowserAdapter: AudioBrowserAdapter
    private val mediaLibrary = Medialibrary.getInstance()
    private lateinit var binding: PlaylistActivityBinding
    private var actionMode: ActionMode? = null
    private var isPlaylist: Boolean = false
    private lateinit var viewModel: PlaylistViewModel
    private var itemTouchHelper: ItemTouchHelper? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.playlist_activity)

        initAudioPlayerContainerActivity()
        fragmentContainer = binding.songs
        originalBottomPadding = fragmentContainer.paddingBottom
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val playlist = if (savedInstanceState != null)
            savedInstanceState.getParcelable<Parcelable>(AudioBrowserFragment.TAG_ITEM) as MediaLibraryItem?
        else
            intent.getParcelableExtra<Parcelable>(AudioBrowserFragment.TAG_ITEM) as MediaLibraryItem?
        if (playlist == null) {
            finish()
            return
        }
        isPlaylist = playlist.itemType == MediaLibraryItem.TYPE_PLAYLIST
        binding.playlist = playlist
        viewModel = getViewModel(playlist)
        viewModel.tracksProvider.pagedList.observe(this, Observer { tracks ->
            @Suppress("UNCHECKED_CAST")
            (tracks as? PagedList<MediaLibraryItem>)?.let { audioBrowserAdapter.submitList(it) }
            menu.let { UiTools.updateSortTitles(it, viewModel.tracksProvider) }
        })
        audioBrowserAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, this, isPlaylist)
        if (isPlaylist) {
            itemTouchHelper = ItemTouchHelper(SwipeDragItemTouchHelperCallback(audioBrowserAdapter))
            itemTouchHelper!!.attachToRecyclerView(binding.songs)
        } else {
            binding.songs.addItemDecoration(RecyclerSectionItemDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_height), true, viewModel.tracksProvider))
        }

        binding.songs.layoutManager = LinearLayoutManager(this)
        binding.songs.adapter = audioBrowserAdapter
        val fabVisibility = savedInstanceState != null && savedInstanceState.getBoolean(TAG_FAB_VISIBILITY)

        lifecycleScope.launch {
            val cover = withContext(Dispatchers.IO) {
                if (!playlist.artworkMrl.isNullOrEmpty()) {
                    AudioUtil.fetchCoverBitmap(Uri.decode(playlist.artworkMrl), getScreenWidth())
                } else {
                    ThumbnailsProvider.getPlaylistImage("playlist:${playlist.id}", playlist.tracks.toList(), getScreenWidth())
                }
            }
            if (cover != null) {
                binding.cover = BitmapDrawable(this@PlaylistActivity.resources, cover)
                binding.appbar.setExpanded(true, true)
                if (savedInstanceState != null) {
                    if (fabVisibility)
                        binding.fab.show()
                    else
                        binding.fab.hide()
                }
            } else fabFallback()
        }

        binding.fab.setOnClickListener(this)
    }

    private fun fabFallback() {
        binding.appbar.setExpanded(false)
        val lp = binding.fab.layoutParams as CoordinatorLayout.LayoutParams
        lp.anchorId = R.id.songs
        lp.anchorGravity = Gravity.BOTTOM or Gravity.END
        lp.behavior = FloatingActionButtonBehavior(this@PlaylistActivity, null)
        binding.fab.layoutParams = lp
        binding.fab.show()
    }

    override fun onStop() {
        super.onStop()
        stopActionMode()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(AudioBrowserFragment.TAG_ITEM, viewModel.playlist)
        outState.putBoolean(TAG_FAB_VISIBILITY, binding.fab.visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.playlist_option, menu)
        if (!isPlaylist) menu.findItem(R.id.ml_menu_sortby).isVisible = true
        menu.findItem(R.id.ml_menu_sortby).isVisible = viewModel.canSortByName()
        menu.findItem(R.id.ml_menu_sortby_filename).isVisible = viewModel.canSortByFileNameName()
        menu.findItem(R.id.ml_menu_sortby_artist_name).isVisible = viewModel.canSortByArtist()
        menu.findItem(R.id.ml_menu_sortby_album_name).isVisible = viewModel.canSortByAlbum()
        menu.findItem(R.id.ml_menu_sortby_length).isVisible = viewModel.canSortByDuration()
        menu.findItem(R.id.ml_menu_sortby_date).isVisible = viewModel.canSortByReleaseDate()
        menu.findItem(R.id.ml_menu_sortby_last_modified).isVisible = viewModel.canSortByLastModified()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ml_menu_sortby_track -> {
                viewModel.sort(Medialibrary.TrackId)
                return true
            }
            R.id.ml_menu_sortby_filename -> {
                viewModel.sort(Medialibrary.SORT_FILENAME)
                return true
            }
            R.id.ml_menu_sortby_length -> {
                viewModel.sort(Medialibrary.SORT_DURATION)
                return true
            }
            R.id.ml_menu_sortby_date -> {
                viewModel.sort(Medialibrary.SORT_RELEASEDATE)
                return true
            }
            R.id.ml_menu_sortby_last_modified -> {
                viewModel.sort(Medialibrary.SORT_LASTMODIFICATIONDATE)
                return true
            }
            R.id.ml_menu_sortby_artist_name -> {
                viewModel.sort(Medialibrary.SORT_ARTIST)
                return true
            }
            R.id.ml_menu_sortby_album_name -> {
                viewModel.sort(Medialibrary.SORT_ALBUM)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            audioBrowserAdapter.multiSelectHelper.toggleSelection(position)
            invalidateActionMode()
        } else
            MediaUtils.playTracks(this, viewModel.playlist, position)
    }

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
        audioBrowserAdapter.multiSelectHelper.toggleSelection(position, true)
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
        if (actionMode == null)
            showContext(this, this, position, item.title, CTX_PLAYLIST_ITEM_FLAGS)
    }

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {}

    override fun onItemFocused(v: View, item: MediaLibraryItem) {}

    override fun onRemove(position: Int, item: MediaLibraryItem) {
        val tracks = ArrayList(listOf(*item.tracks))
        removeFromPlaylist(tracks, ArrayList(Arrays.asList(position)))
    }

    override fun onMove(oldPosition: Int, newPosition: Int) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Moving item from $oldPosition to $newPosition")
        (viewModel.playlist as Playlist).move(oldPosition, newPosition)

    }

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {
        MediaUtils.openList(this, Arrays.asList(*item.tracks), 0)
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper!!.startDrag(viewHolder)
    }

    override fun onPlayerStateChanged(bottomSheet: View, newState: Int) {
        val visibility = binding.fab.visibility
        if (visibility == View.VISIBLE && newState != BottomSheetBehavior.STATE_COLLAPSED && newState != BottomSheetBehavior.STATE_HIDDEN)
            binding.fab.hide()
        else if (visibility == View.INVISIBLE && (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_HIDDEN))
            binding.fab.show()
    }

    fun startActionMode() {
        actionMode = startSupportActionMode(this)
    }

    private fun stopActionMode() = actionMode?.let {
        it.finish()
        onDestroyActionMode(it)
    }

    private fun invalidateActionMode() {
        if (actionMode != null)
            actionMode!!.invalidate()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.action_mode_audio_browser, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = audioBrowserAdapter.multiSelectHelper.getSelectionCount()
        if (count == 0) {
            stopActionMode()
            return false
        }
        val isMedia = audioBrowserAdapter.multiSelectHelper.getSelection()[0].itemType == MediaLibraryItem.TYPE_MEDIA
        val isSong = count == 1 && isMedia
        //menu.findItem(R.id.action_mode_audio_playlist_up).setVisible(isSong && isPlaylist);
        //menu.findItem(R.id.action_mode_audio_playlist_down).setVisible(isSong && isPlaylist);
        menu.findItem(R.id.action_mode_audio_set_song).isVisible = isSong && AndroidDevices.isPhone && !isPlaylist
        menu.findItem(R.id.action_mode_audio_info).isVisible = isSong
        menu.findItem(R.id.action_mode_audio_append).isVisible = PlaylistManager.hasMedia()
        menu.findItem(R.id.action_mode_audio_delete).isVisible = true
        menu.findItem(R.id.action_mode_audio_share).isVisible = isSong
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (!isStarted()) return false
        val list = audioBrowserAdapter.multiSelectHelper.getSelection()
        val tracks = ArrayList<MediaWrapper>()
        list.forEach { tracks.addAll(listOf(*it.tracks)) }

        if (item.itemId == R.id.action_mode_audio_playlist_up) {
            Toast.makeText(this, "UP !", Toast.LENGTH_SHORT).show()
            return true
        }
        if (item.itemId == R.id.action_mode_audio_playlist_down) {
            Toast.makeText(this, "DOWN !", Toast.LENGTH_SHORT).show()
            return true
        }
        val indexes = ArrayList<Int>()
        for (i in 0 until audioBrowserAdapter.multiSelectHelper.selectionMap.size()) {
            indexes.add(audioBrowserAdapter.multiSelectHelper.selectionMap.keyAt(i))
        }

        stopActionMode()
        when (item.itemId) {
            R.id.action_mode_audio_play -> MediaUtils.openList(this, tracks, 0)
            R.id.action_mode_audio_append -> MediaUtils.appendMedia(this, tracks)
            R.id.action_mode_audio_add_playlist -> addToPlaylist(tracks)
            R.id.action_mode_audio_info -> showInfoDialog(list[0] as MediaWrapper)
            R.id.action_mode_audio_share -> lifecycleScope.launch { share(list.map { it as MediaWrapper }) }
            R.id.action_mode_audio_set_song -> setRingtone(list[0] as MediaWrapper)
            R.id.action_mode_audio_delete -> if (isPlaylist) removeFromPlaylist(tracks, indexes) else removeItems(tracks)
            else -> return false
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        audioBrowserAdapter.multiSelectHelper.clearSelection()
    }

    private fun showInfoDialog(media: MediaWrapper) {
        val i = Intent(this, InfoActivity::class.java)
        i.putExtra(TAG_ITEM, media)
        startActivity(i)
    }

    override fun onCtxAction(position: Int, option: Long) {
        if (position >= audioBrowserAdapter.itemCount) return
        val media = audioBrowserAdapter.getItem(position) as MediaWrapper? ?: return
        when (option) {
            CTX_INFORMATION -> showInfoDialog(media)
            CTX_DELETE -> removeItem(position, media)
            CTX_APPEND -> MediaUtils.appendMedia(this, media.tracks)
            CTX_PLAY_NEXT -> MediaUtils.insertNext(this, media.tracks)
            CTX_ADD_TO_PLAYLIST -> addToPlaylist(media.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            CTX_SET_RINGTONE -> setRingtone(media)
            CTX_SHARE -> lifecycleScope.launch { share(media) }
        }

    }

    private fun removeItem(position: Int, media: MediaWrapper) {
        val resId = if (isPlaylist) R.string.confirm_remove_from_playlist else R.string.confirm_delete
        if (isPlaylist) {
            snackerConfirm(binding.root, getString(resId, media.title), Runnable { (viewModel.playlist as Playlist).remove(position) })
        } else {
            val deleteAction = Runnable { deleteMedia(media) }
            snackerConfirm(binding.root, getString(resId, media.title), Runnable { if (Permissions.checkWritePermission(this@PlaylistActivity, media, deleteAction)) deleteAction.run() })
        }
    }

    private fun removeItems(items: List<MediaWrapper>) {
        lifecycleScope.snackerConfirm(binding.root,getString(R.string.confirm_delete_several_media, items.size)) {
            for (item in items) {
                if (!isStarted()) break
                if (getWritePermission(item.uri)) deleteMedia(item)
            }
            if (isStarted()) viewModel.refresh()
        }
    }

    private fun deleteMedia(mw: MediaLibraryItem) = lifecycleScope.launch(Dispatchers.IO) {
        val foldersToReload = LinkedList<String>()
        for (media in mw.tracks) {
            val path = media.uri.path
            val parentPath = FileUtils.getParent(path)
            if (parentPath != null && FileUtils.deleteFile(path) && media.id > 0L && !foldersToReload.contains(parentPath)) {
                foldersToReload.add(parentPath)
            } else
                UiTools.snacker(binding.root, getString(R.string.msg_delete_failed, media.title))
        }
        for (folder in foldersToReload) mediaLibrary.reload(folder)
    }

    override fun onClick(v: View) {
        MediaUtils.playTracks(this, viewModel.playlist, 0)
    }

    private fun removeFromPlaylist(list: List<MediaWrapper>, indexes: List<Int>) {
        val itemsRemoved = HashMap<Int, Long>()
        val playlist = viewModel.playlist as? Playlist
                ?: return
        lifecycleScope.launchWhenStarted {
            val tracks = withContext(Dispatchers.IO) { playlist.tracks }
            for (mediaItem in list) {
                for (i in tracks.indices) {
                    if (tracks[i].id == mediaItem.id) {
                        itemsRemoved[i] = mediaItem.id
                    }
                }
            }
            withContext(Dispatchers.IO) {
                for (index in indexes) playlist.remove(index)
            }
            UiTools.snackerWithCancel(binding.root, getString(R.string.removed_from_playlist_anonymous), null, Runnable {
                for ((key, value) in itemsRemoved) {
                    playlist.add(value, key)
                }
            })
        }
    }

    companion object {

        const val TAG = "VLC/PlaylistActivity"
        const val TAG_FAB_VISIBILITY = "FAB"
    }
}
