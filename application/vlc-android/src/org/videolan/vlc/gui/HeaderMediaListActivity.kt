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
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE
import org.videolan.resources.TAG_ITEM
import org.videolan.resources.UPDATE_REORDER
import org.videolan.resources.util.parcelable
import org.videolan.resources.util.parcelableList
import org.videolan.tools.ALBUMS_SHOW_TRACK_NUMBER
import org.videolan.tools.Settings
import org.videolan.tools.copy
import org.videolan.tools.dp
import org.videolan.tools.isStarted
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.HeaderMediaListActivityBinding
import org.videolan.vlc.gui.audio.AudioAlbumTracksAdapter
import org.videolan.vlc.gui.audio.AudioBrowserAdapter
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_MEDIALIST
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.CONFIRM_RENAME_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.CURRENT_SORT
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.DEFAULT_ACTIONS
import org.videolan.vlc.gui.dialogs.DisplaySettingsDialog
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_MEDIA
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_NEW_NAME
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.dialogs.SHOW_TRACK_NUMBER
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.AudioUtil.setRingtone
import org.videolan.vlc.gui.helpers.DefaultPlaybackAction
import org.videolan.vlc.gui.helpers.DefaultPlaybackActionMediaType
import org.videolan.vlc.gui.helpers.ExpandStateAppBarLayoutBehavior
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.UiTools.createShortcut
import org.videolan.vlc.gui.helpers.UiTools.showPinIfNeeded
import org.videolan.vlc.gui.view.RecyclerSectionItemDecoration
import org.videolan.vlc.interfaces.Filterable
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.interfaces.IListEventsHandler
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.ContextOption.CTX_ADD_SHORTCUT
import org.videolan.vlc.util.ContextOption.CTX_ADD_TO_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_APPEND
import org.videolan.vlc.util.ContextOption.CTX_COPY
import org.videolan.vlc.util.ContextOption.CTX_DELETE
import org.videolan.vlc.util.ContextOption.CTX_FAV_ADD
import org.videolan.vlc.util.ContextOption.CTX_FAV_REMOVE
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_ALBUM_ARTIST
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_ARTIST
import org.videolan.vlc.util.ContextOption.CTX_INFORMATION
import org.videolan.vlc.util.ContextOption.CTX_PLAY_ALL
import org.videolan.vlc.util.ContextOption.CTX_PLAY_NEXT
import org.videolan.vlc.util.ContextOption.CTX_RENAME
import org.videolan.vlc.util.ContextOption.CTX_SET_RINGTONE
import org.videolan.vlc.util.ContextOption.CTX_SHARE
import org.videolan.vlc.util.ContextOption.Companion.createCtxPlaylistItemFlags
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.util.isSchemeHttpOrHttps
import org.videolan.vlc.util.launchWhenStarted
import org.videolan.vlc.util.setLayoutMarginTop
import org.videolan.vlc.util.share
import org.videolan.vlc.viewmodels.PlaylistModel
import org.videolan.vlc.viewmodels.mobile.PlaylistViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel
import java.security.SecureRandom
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
    override var isEdgeToEdge = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.header_media_list_activity)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(android.R.id.content)) { v, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )

            setLayoutMarginTop(binding.mainToolbar, bars.top)
            WindowInsetsCompat.CONSUMED
        }

        initAudioPlayerContainerActivity()
        fragmentContainer = binding.songs
        originalBottomPadding = fragmentContainer.paddingBottom
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.topmargin = 86.dp
        toolbar.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            binding.topmargin = bottom + 8.dp
        }

        val playlist = if (savedInstanceState != null)
            savedInstanceState.parcelable<Parcelable>(AudioBrowserFragment.TAG_ITEM) as MediaLibraryItem?
        else
            intent.parcelable<Parcelable>(AudioBrowserFragment.TAG_ITEM) as MediaLibraryItem?
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

        viewModel.playlistLiveData.observe(this) { playlist ->
            binding.btnFavorite.setImageDrawable(
                ContextCompat.getDrawable(this,
                    if (playlist?.isFavorite == true) R.drawable.ic_header_media_favorite else R.drawable.ic_header_media_favorite_outline
                )
            )
            binding.totalDuration = playlist?.tracks?.sumOf { it.length } ?: 0

            if (playlist is Album) {
                val releaseYear = playlist.releaseYear
                binding.releaseYear = if (releaseYear > 0) releaseYear.toString() else ""
                if (releaseYear <= 0) binding.releaseDate.visibility = View.GONE
            }
        }

        viewModel.tracksProvider.liveHeaders.observe(this) {
            binding.songs.invalidateItemDecorations()
        }

        if (isPlaylist) {
            audioBrowserAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, this, isPlaylist)
            itemTouchHelperCallback = SwipeDragItemTouchHelperCallback(audioBrowserAdapter, lockedInSafeMode = Settings.safeMode)
            itemTouchHelperCallback.swipeAttemptListener = {
                lifecycleScope.launch { showPinIfNeeded() }
            }
            itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
            itemTouchHelper!!.attachToRecyclerView(binding.songs)
            binding.releaseDate.visibility = View.GONE
        } else {
            audioBrowserAdapter = AudioAlbumTracksAdapter(MediaLibraryItem.TYPE_MEDIA, this, this)
            binding.songs.addItemDecoration(RecyclerSectionItemDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_height), true, viewModel.tracksProvider))

        }
        binding.btnShuffle.setOnClickListener {
            viewModel.playlist?.let { if (it.tracksCount > 0) MediaUtils.playTracks(this, it, SecureRandom().nextInt(min(playlist.tracksCount, MEDIALIBRARY_PAGE_SIZE)), true) }
        }
        binding.btnAddPlaylist.setOnClickListener {
            viewModel.playlist?.let { addToPlaylist(it.tracks.toList()) }
        }

        binding.btnFavorite.setOnClickListener {
            lifecycleScope.launch {
                viewModel.toggleFavorite()
            }
        }

        binding.headerListArtist.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val artist = (viewModel.playlist as Album).retrieveAlbumArtist()
                    val i = Intent(this@HeaderMediaListActivity, SecondaryActivity::class.java)
                    i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUMS_SONGS)
                    i.putExtra(AudioBrowserFragment.TAG_ITEM, artist)
                    i.putExtra(ARTIST_FROM_ALBUM, true)
                    i.flags = i.flags or Intent.FLAG_ACTIVITY_NO_HISTORY
                    startActivity(i)
                }
            }
        }

        binding.songs.layoutManager = LinearLayoutManager(this)
        binding.songs.adapter = audioBrowserAdapter

        val context = this
        lifecycleScope.launch {
            var showBackground = true
            val cover = withContext(Dispatchers.IO) {
                val width = if (binding.backgroundView.width > 0) binding.backgroundView.width else context.getScreenWidth()
                if (!playlist.artworkMrl.isNullOrEmpty()) {
                    AudioUtil.fetchCoverBitmap(Uri.decode(playlist.artworkMrl), width)
                } else if (playlist is Album) {
                    showBackground = false
                    UiTools.getDefaultAlbumDrawableBig(this@HeaderMediaListActivity).bitmap
                } else {
                    ThumbnailsProvider.getPlaylistOrGenreImage("playlist:${playlist.id}_$width", playlist.tracks.toList(), width)
                }
            }
            if (cover != null) {
                binding.cover = BitmapDrawable(this@HeaderMediaListActivity.resources, cover)
                binding.appbar.setExpanded(true, true)
                if (showBackground) {
                    val radius = if (isPlaylist) 25f else 15f
                    UiTools.blurView(binding.backgroundView, cover, radius, UiTools.getColorFromAttribute(context, R.attr.audio_player_background_tint))
                }
            }
        }

        binding.playBtn.setOnClickListener(this)

        //swipe layout is only here to be able to make the recyclerview dispatch the scroll events
        binding.swipeLayout.isEnabled = false
        audioBrowserAdapter.areSectionsEnabled = false
        binding.browserFastScroller.attachToCoordinator(binding.appbar, binding.coordinator, null)
        binding.browserFastScroller.setRecyclerView(binding.songs, viewModel.tracksProvider)

        supportFragmentManager.setFragmentResultListener(CONFIRM_DELETE_DIALOG_RESULT, this) { key, bundle ->
            // Any type can be passed via to the bundle
            val items: List<MediaWrapper> = bundle.parcelableList(CONFIRM_DELETE_DIALOG_MEDIALIST) ?: listOf()
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
        supportFragmentManager.setFragmentResultListener(CONFIRM_RENAME_DIALOG_RESULT, this) { key, bundle ->
            lifecycleScope.launch {
                val item: MediaWrapper = bundle.parcelable(RENAME_DIALOG_MEDIA) ?: return@launch
                val name: String = bundle.getString(RENAME_DIALOG_NEW_NAME) ?: return@launch
                viewModel.rename(item, name)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val playlistModel = PlaylistModel.get(this)
        PlaylistManager.currentPlayedMedia.observe(this) {
            audioBrowserAdapter.currentMedia = it
        }
        playlistModel.dataset.asFlow().conflate().onEach {
            audioBrowserAdapter.setCurrentlyPlaying(playlistModel.playing)
            delay(50L)
        }.launchWhenStarted(lifecycleScope)
        audioBrowserAdapter.setModel(playlistModel)
    }

    override fun onPause() {
        super.onPause()
        audioBrowserAdapter.setCurrentlyPlaying(false)
    }

    override fun onStop() {
        super.onStop()
        stopActionMode()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        viewModel.playlist?.let {
            outState.putParcelable(AudioBrowserFragment.TAG_ITEM, it)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.playlist_option, menu)
        if (!isPlaylist) {
            menu.findItem(R.id.ml_menu_display_options).isVisible = true
        }
        val searchItem = menu.findItem(R.id.ml_menu_filter)
        searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_in_list_hint)
        searchView.setOnQueryTextListener(this)
        val query = getFilterQuery()
        if (!query.isNullOrEmpty()) {
            searchView.post {
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
            R.id.ml_menu_display_options -> {
                //filter all sorts and keep only applicable ones
                val sorts = arrayListOf(Medialibrary.TrackId, Medialibrary.SORT_ALPHA, Medialibrary.SORT_FILENAME, Medialibrary.SORT_ARTIST, Medialibrary.SORT_ALBUM, Medialibrary.SORT_DURATION, Medialibrary.SORT_RELEASEDATE, Medialibrary.SORT_LASTMODIFICATIONDATE, Medialibrary.SORT_FILESIZE, Medialibrary.NbMedia).filter {
                    viewModel.canSortBy(it)
                }
                //Open the display settings Bottom sheet
                DisplaySettingsDialog.newInstance(
                    displayInCards = null,
                    onlyFavs = null,
                    sorts = sorts,
                    showTrackNumber = Settings.showTrackNumber,
                    currentSort = viewModel.tracksProvider.sort,
                    currentSortDesc = viewModel.tracksProvider.desc,
                    defaultPlaybackActions = DefaultPlaybackActionMediaType.TRACK.getDefaultPlaybackActions(Settings.getInstance(this)),
                    defaultActionType = getString(DefaultPlaybackActionMediaType.TRACK.title)
                )
                    .show(supportFragmentManager, "DisplaySettingsDialog")
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onDisplaySettingChanged(key: String, value: Any) {
        when (key) {
            CURRENT_SORT -> {
                @Suppress("UNCHECKED_CAST") val sort = value as Pair<Int, Boolean>
                viewModel.desc = sort.second
                viewModel.sort(sort.first)
            }
            SHOW_TRACK_NUMBER -> {
                val checked = value as Boolean
                Settings.getInstance(this).putSingle(ALBUMS_SHOW_TRACK_NUMBER, checked)
                Settings.showTrackNumber = checked
                audioBrowserAdapter.notifyDataSetChanged()
                viewModel.refresh()
            }
            DEFAULT_ACTIONS -> {
                Settings.getInstance(this).putSingle(DefaultPlaybackActionMediaType.TRACK.defaultActionKey, (value as DefaultPlaybackAction).name)
            }
        }
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            audioBrowserAdapter.multiSelectHelper.toggleSelection(position)
            invalidateActionMode()
        } else {
            if (searchView.visibility == View.VISIBLE) UiTools.setKeyboardVisibility(v, false)
            if (isPlaylist)
                MediaUtils.playTracks(this, viewModel.tracksProvider, position)
            else
                when(DefaultPlaybackActionMediaType.TRACK.getCurrentPlaybackAction(Settings.getInstance(this))) {
                    DefaultPlaybackAction.PLAY -> MediaUtils.openList(this, listOf(*item.tracks), 0)
                    DefaultPlaybackAction.ADD_TO_QUEUE -> MediaUtils.appendMedia(this, listOf(*item.tracks))
                    DefaultPlaybackAction.INSERT_NEXT -> MediaUtils.insertNext(this, listOf(*item.tracks).toTypedArray())
                    DefaultPlaybackAction.PLAY_ALL -> MediaUtils.playTracks(this, viewModel.tracksProvider, position)
                }
        }
    }

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
        audioBrowserAdapter.multiSelectHelper.toggleSelection(position, true)
        if (actionMode == null) startActionMode() else invalidateActionMode()
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
            (item as? MediaWrapper)?.let { media ->
                val flags = createCtxPlaylistItemFlags().apply {
                    if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                    if (media.type == MediaWrapper.TYPE_STREAM || (media.type == MediaWrapper.TYPE_ALL && isSchemeHttpOrHttps(media.uri.scheme)))
                        addAll(CTX_COPY, CTX_RENAME)
                    if (media.type == MediaWrapper.TYPE_AUDIO) {
                        add(CTX_GO_TO_ARTIST)
                        if (BuildConfig.DEBUG) Log.d("CtxPrep", "Artist id is: ${media.artistId}, album artist is: ${media.albumArtistId}")
                        if (media.artistId != media.albumArtistId) add(CTX_GO_TO_ALBUM_ARTIST)
                    }
                    else add(CTX_SHARE)
                }
                showContext(this, this, position, media, flags)
            }
        }
    }

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {}

    override fun onItemFocused(v: View, item: MediaLibraryItem) {}

    override fun onRemove(position: Int, item: MediaLibraryItem) {
        lastDismissedPosition = position
        val tracks = ArrayList(listOf(*item.tracks))
        lifecycleScope.launch {  removeFromPlaylist(tracks, ArrayList(listOf(position))) }
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
        menu.findItem(R.id.action_mode_audio_set_song).isVisible = isSong && AndroidDevices.isPhone && !isPlaylist
        menu.findItem(R.id.action_mode_audio_info).isVisible = isSong
        menu.findItem(R.id.action_mode_audio_append).isVisible = PlaylistManager.hasMedia()
        menu.findItem(R.id.action_mode_audio_delete).isVisible = true
        menu.findItem(R.id.action_mode_audio_share).isVisible = isSong
        menu.findItem(R.id.action_mode_favorite_add).isVisible = audioBrowserAdapter.multiSelectHelper.getSelection().none { it.isFavorite }
        menu.findItem(R.id.action_mode_favorite_remove).isVisible = audioBrowserAdapter.multiSelectHelper.getSelection().none { !it.isFavorite }
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (!isStarted()) return false
        val list = audioBrowserAdapter.multiSelectHelper.getSelection()
        val tracks = ArrayList<MediaWrapper>()
        list.forEach { tracks.addAll(listOf(*it.tracks)) }

        val indexes = audioBrowserAdapter.multiSelectHelper.selectionMap

        when (item.itemId) {
            R.id.action_mode_audio_play -> MediaUtils.openList(this, tracks, 0)
            R.id.action_mode_audio_append -> MediaUtils.appendMedia(this, tracks)
            R.id.action_mode_audio_add_playlist -> addToPlaylist(tracks)
            R.id.action_mode_audio_info -> showInfoDialog(list[0] as MediaWrapper)
            R.id.action_mode_audio_share -> lifecycleScope.launch { share(list.map { it as MediaWrapper }) }
            R.id.action_mode_audio_set_song -> setRingtone(list[0] as MediaWrapper)
            R.id.action_mode_audio_delete -> lifecycleScope.launch { if (isPlaylist) removeFromPlaylist(tracks, indexes.toMutableList()) else removeItems(tracks) }
            R.id.action_mode_favorite_add -> lifecycleScope.launch { viewModel.changeFavorite(tracks, true) }
            R.id.action_mode_favorite_remove -> lifecycleScope.launch { viewModel.changeFavorite(tracks, false) }
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

    override fun onCtxAction(position: Int, option: ContextOption) {
        if (position >= audioBrowserAdapter.itemCount) return
        val media = audioBrowserAdapter.getItemByPosition(position) as MediaWrapper? ?: return
        when (option) {
            CTX_INFORMATION -> showInfoDialog(media)
            CTX_DELETE -> lifecycleScope.launch { removeItem(position, media) }
            CTX_APPEND -> MediaUtils.appendMedia(this, media.tracks)
            CTX_PLAY_NEXT -> MediaUtils.insertNext(this, media.tracks)
            CTX_PLAY_ALL -> MediaUtils.playTracks(this, viewModel.tracksProvider, position, false)
            CTX_ADD_TO_PLAYLIST -> addToPlaylist(media.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            CTX_SET_RINGTONE -> setRingtone(media)
            CTX_SHARE -> lifecycleScope.launch { share(media) }
            CTX_RENAME -> {
                val dialog = RenameDialog.newInstance(media)
                dialog.show(this.supportFragmentManager, RenameDialog::class.simpleName)
            }
            CTX_COPY -> {
                copy(media.title, media.location)
                Snackbar.make(window.decorView.findViewById(android.R.id.content), R.string.url_copied_to_clipboard, Snackbar.LENGTH_LONG).show()
            }
            CTX_FAV_ADD, CTX_FAV_REMOVE -> lifecycleScope.launch {
                media.isFavorite = option == CTX_FAV_ADD
                withContext(Dispatchers.Main) { audioBrowserAdapter.notifyItemChanged(position) }
            }
            CTX_ADD_SHORTCUT -> lifecycleScope.launch { createShortcut(media) }
            CTX_GO_TO_ARTIST -> lifecycleScope.launch(Dispatchers.IO) {
                val artist = if (media is Album) media.retrieveAlbumArtist() else (media as MediaWrapper).artist
                val i = Intent(this@HeaderMediaListActivity, SecondaryActivity::class.java)
                i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUMS_SONGS)
                i.putExtra(AudioBrowserFragment.TAG_ITEM, artist)
                i.putExtra(ARTIST_FROM_ALBUM, true)
                i.flags = i.flags or Intent.FLAG_ACTIVITY_NO_HISTORY
                startActivity(i)
            }
            CTX_GO_TO_ALBUM_ARTIST -> lifecycleScope.launch(Dispatchers.IO) {
                val artist = (media as MediaWrapper).albumArtist
                val i = Intent(this@HeaderMediaListActivity, SecondaryActivity::class.java)
                i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUMS_SONGS)
                i.putExtra(AudioBrowserFragment.TAG_ITEM, artist)
                i.putExtra(ARTIST_FROM_ALBUM, true)
                i.flags = i.flags or Intent.FLAG_ACTIVITY_NO_HISTORY
                startActivity(i)
            }
            else -> {}
        }

    }

    private suspend fun removeItem(position: Int, media: MediaWrapper) {
        if (isPlaylist) {
            removeFromPlaylist(listOf(media), listOf(position))
        } else {
            removeItems(listOf(media))
        }
    }

    private fun removeItems(items: List<MediaWrapper>) {
        val dialog = ConfirmDeleteDialog.newInstance(ArrayList(items))
        dialog.show(supportFragmentManager, ConfirmDeleteDialog::class.simpleName)
    }

    override fun onClick(v: View) {
        MediaUtils.playTracks(this, viewModel.tracksProvider, 0)
    }

    private fun removeFromPlaylist(list: List<MediaWrapper>, indexes: List<Int>) {
        if (!showPinIfNeeded()) {
            val itemsRemoved = HashMap<Int, Long>()
            val playlist = viewModel.playlist as? Playlist
                    ?: return

            itemTouchHelperCallback.swipeEnabled = false
            lifecycleScope.launchWhenStarted {
                val tracks = withContext(Dispatchers.IO) { playlist.tracks }
                viewModel.playlist?.let { playlist ->
                    withContext(Dispatchers.IO) {
                        for ((index, playlistIndex) in indexes.sortedBy { it }.withIndex()) {
                            val trueIndex = playlist.tracks.indexOf(list[index])
                            itemsRemoved[trueIndex] = tracks[playlistIndex].id
                            (playlist as Playlist).remove(trueIndex)
                        }
                    }
                }
                var removedMessage = if (indexes.size > 1) getString(R.string.removed_from_playlist_anonymous) else getString(R.string.remove_playlist_item, list.first().title)
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
        } else {
            if (lastDismissedPosition != -1) {
                audioBrowserAdapter.notifyItemChanged(lastDismissedPosition)
                lastDismissedPosition = -1
            }
        }
    }

    companion object {

        const val ARTIST_FROM_ALBUM = "ARTIST_FROM_ALBUM"
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

    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
        binding.appbar.setExpanded(false, true)
        audioBrowserAdapter.stopReorder = true
        audioBrowserAdapter.notifyItemRangeChanged(0, audioBrowserAdapter.itemCount, UPDATE_REORDER)
        ((binding.appbar.layoutParams as CoordinatorLayout.LayoutParams).behavior as ExpandStateAppBarLayoutBehavior).scrollEnabled = false
        return true
    }

    override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
        audioBrowserAdapter.stopReorder = false
        audioBrowserAdapter.notifyItemRangeChanged(0, audioBrowserAdapter.itemCount, UPDATE_REORDER)
        ((binding.appbar.layoutParams as CoordinatorLayout.LayoutParams).behavior as ExpandStateAppBarLayoutBehavior).scrollEnabled = true
        return true
    }
}
