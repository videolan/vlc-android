/*
 * *************************************************************************
 *  HeaderMediaListActivity.kt
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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.tools.copy
import org.videolan.tools.isStarted
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.HeaderMediaListActivityBinding
import org.videolan.vlc.gui.audio.AudioAlbumTracksAdapter
import org.videolan.vlc.gui.audio.AudioBrowserAdapter
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.dialogs.*
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.AudioUtil.setRingtone
import org.videolan.vlc.gui.helpers.ExpandStateAppBarLayoutBehavior
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.view.RecyclerSectionItemDecoration
import org.videolan.vlc.interfaces.Filterable
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.interfaces.IListEventsHandler
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.mobile.PlaylistViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel
import java.security.SecureRandom
import java.util.*
import kotlin.math.min

open class HeaderMediaListActivity : AudioPlayerContainerActivity(), IEventsHandler<MediaLibraryItem>, IListEventsHandler, ActionMode.Callback, View.OnClickListener, CtxActionReceiver, Filterable, SearchView.OnQueryTextListener, MenuItem.OnActionExpandListener {

    private var lastDismissedPosition: Int = -1
    private lateinit var searchView: SearchView
    private lateinit var itemTouchHelperCallback: SwipeDragItemTouchHelperCallback
    private lateinit var audioBrowserAdapter: AudioBrowserAdapter
    private val mediaLibrary = Medialibrary.getInstance()
    private lateinit var binding: HeaderMediaListActivityBinding
    private var actionMode: ActionMode? = null
    private var isPlaylist: Boolean = false
    private lateinit var viewModel: PlaylistViewModel
    private var itemTouchHelper: ItemTouchHelper? = null
    override fun isTransparent() = true

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.header_media_list_activity)

        initAudioPlayerContainerActivity()
        fragmentContainer = binding.songs
        originalBottomPadding = fragmentContainer.paddingBottom
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

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
        viewModel.tracksProvider.pagedList.observe(this) { tracks ->
            @Suppress("UNCHECKED_CAST")
            (tracks as? PagedList<MediaLibraryItem>)?.let { audioBrowserAdapter.submitList(it) }
            menu.let { UiTools.updateSortTitles(it, viewModel.tracksProvider) }
            if (::itemTouchHelperCallback.isInitialized) itemTouchHelperCallback.swipeEnabled = true
        }

        viewModel.tracksProvider.liveHeaders.observe(this) {
            binding.songs.invalidateItemDecorations()
        }
        audioBrowserAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, this, isPlaylist)
        var totalDuration = 0L
        for (item in viewModel.playlist.tracks)
            totalDuration += item.length
        binding.totalDuration = totalDuration
        if (isPlaylist) {
            audioBrowserAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, this, isPlaylist)
            itemTouchHelperCallback = SwipeDragItemTouchHelperCallback(audioBrowserAdapter)
            itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
            itemTouchHelper!!.attachToRecyclerView(binding.songs)
            binding.releaseDate.visibility = View.GONE
        } else {
            audioBrowserAdapter = AudioAlbumTracksAdapter(MediaLibraryItem.TYPE_MEDIA, this, this)
            binding.songs.addItemDecoration(RecyclerSectionItemDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_height), true, viewModel.tracksProvider))
            if (viewModel.playlist is Album) {
                val releaseYear = (viewModel.playlist as Album).releaseYear
                binding.releaseYear =  if (releaseYear > 0) releaseYear.toString() else ""
                if (releaseYear <= 0) binding.releaseDate.visibility = View.GONE
            }
        }
        binding.btnShuffle.setOnClickListener {
            MediaUtils.playTracks(this, viewModel.playlist, SecureRandom().nextInt(min(playlist.tracksCount, MEDIALIBRARY_PAGE_SIZE)), true)
        }
        binding.btnAddPlaylist.setOnClickListener {
            addToPlaylist(viewModel.playlist.tracks.toList())
        }

        binding.songs.layoutManager = LinearLayoutManager(this)
        binding.songs.adapter = audioBrowserAdapter

        val context = this
        lifecycleScope.launch {
            val cover = withContext(Dispatchers.IO) {
                val width = if (binding.backgroundView.width > 0) binding.backgroundView.width else context.getScreenWidth()
                if (!playlist.artworkMrl.isNullOrEmpty()) {
                    AudioUtil.fetchCoverBitmap(Uri.decode(playlist.artworkMrl), width)
                } else {
                    ThumbnailsProvider.getPlaylistOrGenreImage("playlist:${playlist.id}_$width", playlist.tracks.toList(), width)
                }
            }
            if (cover != null) {
                binding.cover = BitmapDrawable(this@HeaderMediaListActivity.resources, cover)
                binding.appbar.setExpanded(true, true)
                val radius = if (isPlaylist) 25f else 15f
                val blurredCover = UiTools.blurBitmap(cover, radius)
                withContext(Dispatchers.Main) {
                    binding.backgroundView.setColorFilter(UiTools.getColorFromAttribute(context, R.attr.audio_player_background_tint))
                    binding.backgroundView.setImageBitmap(blurredCover)
                }
            }
        }

        binding.playBtn.setOnClickListener(this)
    }

    override fun onStop() {
        super.onStop()
        stopActionMode()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(AudioBrowserFragment.TAG_ITEM, viewModel.playlist)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.playlist_option, menu)
        if (!isPlaylist) menu.findItem(R.id.ml_menu_sortby).isVisible = true
        menu.findItem(R.id.ml_menu_sortby).isVisible = viewModel.canSortByName()
        menu.findItem(R.id.ml_menu_sortby_filename).isVisible = viewModel.canSortByFileNameName()
        menu.findItem(R.id.ml_menu_sortby_artist_name).isVisible = viewModel.canSortByArtist()
        menu.findItem(R.id.ml_menu_sortby_length).isVisible = viewModel.canSortByDuration()
        menu.findItem(R.id.ml_menu_sortby_date).isVisible = viewModel.canSortByReleaseDate()
        menu.findItem(R.id.ml_menu_sortby_last_modified).isVisible = viewModel.canSortByLastModified()
        val searchItem = menu.findItem(R.id.ml_menu_filter)
        searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_in_list_hint)
        searchView.setOnQueryTextListener(this)
        val query = getFilterQuery()
        if (!query.isNullOrEmpty()) {
            handler.post {
                searchItem.expandActionView()
                searchView.clearFocus()
                UiTools.setKeyboardVisibility(searchView, false)
                searchView.setQuery(query, false)
            }
        }
        searchItem.setOnActionExpandListener(this)
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
        } else {
            if (searchView.visibility == View.VISIBLE) UiTools.setKeyboardVisibility(v, false)
            MediaUtils.playTracks(this, viewModel.tracksProvider, position)
        }
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
        if (actionMode == null) {
            var flags = CTX_PLAYLIST_ITEM_FLAGS
            (item as? MediaWrapper)?.let { media ->
                if (media.type == MediaWrapper.TYPE_STREAM || (media.type == MediaWrapper.TYPE_ALL && isSchemeHttpOrHttps(media.uri.scheme))) flags = flags or CTX_RENAME or CTX_COPY
                else  flags = flags or CTX_SHARE
                showContext(this, this, position, media, flags)
            }
        }
    }

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {}

    override fun onItemFocused(v: View, item: MediaLibraryItem) {}

    override fun onRemove(position: Int, item: MediaLibraryItem) {
        lastDismissedPosition = position
        val tracks = ArrayList(listOf(*item.tracks))
        removeFromPlaylist(tracks, ArrayList(listOf(position)))
    }

    override fun onMove(oldPosition: Int, newPosition: Int) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Moving item from $oldPosition to $newPosition")
        (viewModel.playlist as Playlist).move(oldPosition, newPosition)

    }

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {
        MediaUtils.openList(this, listOf(*item.tracks), 0)
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper!!.startDrag(viewHolder)
    }

    private fun startActionMode() {
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
        audioBrowserAdapter.multiSelectHelper.toggleActionMode(true, audioBrowserAdapter.itemCount)
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
        val indexes = audioBrowserAdapter.multiSelectHelper.selectionMap

        when (item.itemId) {
            R.id.action_mode_audio_play -> MediaUtils.openList(this, tracks, 0)
            R.id.action_mode_audio_append -> MediaUtils.appendMedia(this, tracks)
            R.id.action_mode_audio_add_playlist -> addToPlaylist(tracks)
            R.id.action_mode_audio_info -> showInfoDialog(list[0] as MediaWrapper)
            R.id.action_mode_audio_share -> lifecycleScope.launch { share(list.map { it as MediaWrapper }) }
            R.id.action_mode_audio_set_song -> setRingtone(list[0] as MediaWrapper)
            R.id.action_mode_audio_delete -> if (isPlaylist) removeFromPlaylist(tracks, indexes.toMutableList()) else removeItems(tracks)
            else -> return false
        }
        stopActionMode()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        audioBrowserAdapter.multiSelectHelper.toggleActionMode(false, audioBrowserAdapter.itemCount)
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
            CTX_RENAME -> {
                val dialog = RenameDialog.newInstance(media)
                dialog.show(this.supportFragmentManager, RenameDialog::class.simpleName)
                dialog.setListener { item, name ->
                    lifecycleScope.launch {
                       viewModel.rename(item as MediaWrapper, name)
                    }
                }
            }
            CTX_COPY -> {
                copy(media.title, media.location)
                Snackbar.make(window.decorView.findViewById(android.R.id.content), R.string.url_copied_to_clipboard, Snackbar.LENGTH_LONG).show()
            }
        }

    }

    private fun removeItem(position: Int, media: MediaWrapper) {
        if (isPlaylist) {
            removeFromPlaylist(listOf(media), listOf(position))
        } else {
            removeItems(listOf(media))
        }
    }

    private fun removeItems(items: List<MediaWrapper>) {
        val dialog = ConfirmDeleteDialog.newInstance(ArrayList(items))
        dialog.show(supportFragmentManager, ConfirmDeleteDialog::class.simpleName)
        dialog.setListener {
            lifecycleScope.launch {
                for (item in items) {
                    val deleteAction = kotlinx.coroutines.Runnable {
                        lifecycleScope.launch {
                            MediaUtils.deleteItem(this@HeaderMediaListActivity, item) {
                                UiTools.snacker(this@HeaderMediaListActivity, getString(R.string.msg_delete_failed, it.title))
                            }
                            if (isStarted()) viewModel.refresh()
                        }
                    }
                    if (Permissions.checkWritePermission(this@HeaderMediaListActivity, item, deleteAction)) deleteAction.run()
                }
            }
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
                UiTools.snacker(this@HeaderMediaListActivity, getString(R.string.msg_delete_failed, media.title))
        }
        for (folder in foldersToReload) mediaLibrary.reload(folder)
    }

    override fun onClick(v: View) {
        MediaUtils.playTracks(this, viewModel.tracksProvider, 0)
    }

    private fun removeFromPlaylist(list: List<MediaWrapper>, indexes: List<Int>) {
        val itemsRemoved = HashMap<Int, Long>()
        val playlist = viewModel.playlist as? Playlist
                ?: return

        itemTouchHelperCallback.swipeEnabled = false
        lifecycleScope.launchWhenStarted {
            val tracks = withContext(Dispatchers.IO) { playlist.tracks }
            withContext(Dispatchers.IO) {
                for ((index, playlistIndex) in indexes.sortedBy { it }.withIndex()) {
                    val trueIndex = viewModel.playlist.tracks.indexOf(list[index])
                    itemsRemoved[trueIndex] = tracks[playlistIndex].id
                    playlist.remove(trueIndex)
                }
            }
            var removedMessage = if (indexes.size>1) getString(R.string.removed_from_playlist_anonymous) else getString(R.string.remove_playlist_item,list.first().title)
            UiTools.snackerWithCancel(this@HeaderMediaListActivity, removedMessage, action = {
                lastDismissedPosition = -1
            }) {
                for ((key, value) in itemsRemoved) {
                    playlist.add(value, key)
                    if (lastDismissedPosition != -1) {
                        audioBrowserAdapter.notifyItemChanged(lastDismissedPosition)
                        lastDismissedPosition = -1
                    }
                }
            }
        }
    }

    companion object {

        const val TAG = "VLC/PlaylistActivity"
    }

    override fun getFilterQuery() = viewModel.filterQuery

    override fun enableSearchOption() = true

    override fun filter(query: String) = viewModel.filter(query)

    override fun restoreList() = viewModel.restore()

    override fun setSearchVisibility(visible: Boolean) {}

    override fun allowedToExpand() = true

    override fun onQueryTextSubmit(query: String?) = false

    override fun onQueryTextChange(newText: String?): Boolean {
        if (newText?.length  == 0)
            restoreList()
        else
            filter(newText ?: "")
        return true
    }

    override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
        binding.appbar.setExpanded(false, true)
        audioBrowserAdapter.stopReorder = true
        audioBrowserAdapter.notifyItemRangeChanged(0, audioBrowserAdapter.itemCount, UPDATE_REORDER)
        ((binding.appbar.layoutParams as CoordinatorLayout.LayoutParams).behavior as ExpandStateAppBarLayoutBehavior).scrollEnabled = false
        return true
    }

    override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
        audioBrowserAdapter.stopReorder = false
        audioBrowserAdapter.notifyItemRangeChanged(0, audioBrowserAdapter.itemCount, UPDATE_REORDER)
        ((binding.appbar.layoutParams as CoordinatorLayout.LayoutParams).behavior as ExpandStateAppBarLayoutBehavior).scrollEnabled = true
        return true
    }
}
