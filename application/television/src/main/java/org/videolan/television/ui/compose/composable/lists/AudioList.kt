/*
 * ************************************************************************
 *  AudioList.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.television.ui.compose.composable.lists

import android.app.Application
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE
import org.videolan.resources.PLAYLIST_TYPE_AUDIO
import org.videolan.resources.PLAYLIST_TYPE_VIDEO
import org.videolan.television.R
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.compose.AudioDestination
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.MediaListSidePanel
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelContent
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelListenerKey
import org.videolan.television.ui.compose.composable.components.PaginatedGrid
import org.videolan.television.ui.compose.composable.components.PaginatedList
import org.videolan.television.ui.compose.composable.components.VlcEmptyViewLoader
import org.videolan.television.ui.compose.composable.items.AudioItemCard
import org.videolan.television.ui.compose.composable.items.AudioItemList
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.util.showParent
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.SnackbarContent
import org.videolan.tools.KEY_AUDIO_CURRENT_TAB
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.helpers.DefaultPlaybackAction
import org.videolan.vlc.gui.helpers.DefaultPlaybackActionMediaType
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.util.ContextOption.CTX_ADD_TO_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_APPEND
import org.videolan.vlc.util.ContextOption.CTX_DELETE
import org.videolan.vlc.util.ContextOption.CTX_FAV_ADD
import org.videolan.vlc.util.ContextOption.CTX_FAV_REMOVE
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_ALBUM
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_ARTIST
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_FOLDER
import org.videolan.vlc.util.ContextOption.CTX_INFORMATION
import org.videolan.vlc.util.ContextOption.CTX_PLAY
import org.videolan.vlc.util.ContextOption.CTX_PLAY_ALL
import org.videolan.vlc.util.ContextOption.CTX_PLAY_AS_AUDIO
import org.videolan.vlc.util.ContextOption.CTX_PLAY_NEXT
import org.videolan.vlc.util.ContextOption.CTX_PLAY_SHUFFLE
import org.videolan.vlc.util.ContextOption.CTX_RENAME
import org.videolan.vlc.util.ContextOption.CTX_SHARE
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.share
import org.videolan.vlc.viewmodels.mobile.AudioBrowserViewModel
import org.videolan.vlc.viewmodels.mobile.PlaylistsViewModel
import java.security.SecureRandom
import kotlin.math.min


@Composable
fun AudioListScreen(subDestination: AudioDestination, onFocusExit: () -> Unit, onFocusEnter: () -> Unit) {
    val context = LocalContext.current
    val index = subDestination.ordinal
    Settings.getInstance(context).putSingle(KEY_AUDIO_CURRENT_TAB, index)
    when (subDestination) {
        AudioDestination.Artists -> MediaList(MediaListEntry.ARTISTS, index, onFocusExit = onFocusExit, onFocusEnter = onFocusEnter)
        AudioDestination.Albums -> MediaList(MediaListEntry.ALBUMS, index, onFocusExit = onFocusExit, onFocusEnter = onFocusEnter)
        AudioDestination.Tracks -> MediaList(MediaListEntry.TRACKS, index, onFocusExit = onFocusExit, onFocusEnter = onFocusEnter)
        AudioDestination.Genres -> MediaList(MediaListEntry.GENRES, index, onFocusExit = onFocusExit, onFocusEnter = onFocusEnter)
        AudioDestination.Playlists -> MediaList(MediaListEntry.AUDIO_PLAYLISTS, index, onFocusExit = onFocusExit, onFocusEnter = onFocusEnter)
    }
}

@Composable
fun MediaList(entry: MediaListEntry, index: Int, onFocusExit: () -> Unit = {}, onFocusEnter: () -> Unit = {}, mainActivityViewModel: MainActivityViewModel = viewModel()) {
    val displaySettingsChange by mainActivityViewModel.currentDisplaySettingsChange.collectAsState()
    val invalidateEntry by mainActivityViewModel.invalidateMediaListEntry.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    InvalidationComposable(displaySettingsChange) { invalidate ->
        val context = LocalContext.current

        val provider: MedialibraryProvider<out MediaLibraryItem>

        when (entry) {
            in arrayOf(MediaListEntry.ALL_PLAYLISTS, MediaListEntry.VIDEO_PLAYLISTS, MediaListEntry.AUDIO_PLAYLISTS) -> {
                val extras = MutableCreationExtras().apply {
                    set(APPLICATION_KEY, context.applicationContext as Application)
                    set(
                        PlaylistsViewModel.PLAYLIST_TYPE, when (entry) {
                            MediaListEntry.VIDEO_PLAYLISTS -> Playlist.Type.Video
                            MediaListEntry.AUDIO_PLAYLISTS -> Playlist.Type.Audio
                            else -> Playlist.Type.All
                        }
                    )
                }
                val playlistsViewModel: PlaylistsViewModel = viewModel(
                    key = "PlaylistsViewModel_${entry.name}",
                    factory = PlaylistsViewModel.Factory,
                    extras = extras,
                )
                provider = playlistsViewModel.provider
            }
            else -> {
                val extras = MutableCreationExtras().apply {
                    set(APPLICATION_KEY, context.applicationContext as Application)
                }
                val audioBrowserViewModel: AudioBrowserViewModel = viewModel(
                    factory = AudioBrowserViewModel.Factory,
                    extras = extras,
                )
                audioBrowserViewModel.currentTab = index
                provider = audioBrowserViewModel.getProvider(entry)
            }
        }

        //invalidate if needed
        if (invalidateEntry == entry) {
            provider.pagingSource.invalidate()
            mainActivityViewModel.invalidationDone()
            invalidate()
        }

        entry.sorts  = arrayListOf(Medialibrary.SORT_ALPHA, Medialibrary.SORT_FILENAME, Medialibrary.SORT_ARTIST, Medialibrary.SORT_ALBUM, Medialibrary.SORT_DURATION, Medialibrary.SORT_RELEASEDATE, Medialibrary.SORT_LASTMODIFICATIONDATE, Medialibrary.SORT_FILESIZE, Medialibrary.NbMedia, Medialibrary.SORT_INSERTIONDATE).filter {
            provider.canSortBy(it)
        }
        entry.currentSort = provider.sort
        entry.currentSortDesc = provider.desc

        val audios = provider.pager.collectAsLazyPagingItems()
        var inCard by rememberSaveable { mutableStateOf(entry.displayInCard(context)) }

        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()
        val activity = LocalActivity.current
        val settings = Settings.getInstance(activity!!)
        val onClick: (MediaLibraryItem, Int) -> Unit = { item, position ->
            when (item) {
                is Artist -> TvUtil.openAudioCategory(activity, item)
                is Album -> TvUtil.openAudioCategory(activity, item)
                is Genre -> mainActivityViewModel.showSnackbar(SnackbarContent(activity.resources.getString(R.string.not_implemented)))
                is Playlist -> TvUtil.openAudioCategory(activity, item)
                else -> {
                    when (DefaultPlaybackActionMediaType.TRACK.getCurrentPlaybackAction(settings)) {
                        DefaultPlaybackAction.PLAY -> TvUtil.openMedia(activity as FragmentActivity, item)
                        DefaultPlaybackAction.PLAY_ALL -> MediaUtils.playAll(activity, provider as MedialibraryProvider<MediaWrapper>, position, false)
                        DefaultPlaybackAction.ADD_TO_QUEUE -> MediaUtils.appendMedia(activity, listOf(*item.tracks), showSnackbar = {
                            mainActivityViewModel.showSnackbar(SnackbarContent(it))
                        })
                        DefaultPlaybackAction.INSERT_NEXT -> MediaUtils.insertNext(activity, listOf(*item.tracks).toTypedArray(), showSnackbar = {
                            mainActivityViewModel.showSnackbar(SnackbarContent(it))
                        })
                    }

                }
            }
        }
        mainActivityViewModel.addCtxClickListener(entry) { item, position, ctxMenuItem ->
            val showSnackbar: (String) -> Unit = {
                mainActivityViewModel.showSnackbar(SnackbarContent(it))
            }
            when (item) {
                is Artist -> {
                    when(ctxMenuItem.id) {
                        CTX_PLAY -> MediaUtils.playTracks(activity, item, 0)
                        CTX_PLAY_SHUFFLE -> MediaUtils.playTracks(activity, item, SecureRandom().nextInt(min(item.tracksCount, MEDIALIBRARY_PAGE_SIZE)), true)
                        CTX_APPEND -> MediaUtils.appendMedia(activity, item.tracks, showSnackbar)
                        CTX_PLAY_NEXT -> MediaUtils.insertNext(activity, item.tracks, showSnackbar)
                        CTX_INFORMATION -> mainActivityViewModel.showSnackbar(SnackbarContent(activity.resources.getString(R.string.not_implemented)))
                        CTX_ADD_TO_PLAYLIST -> (activity as FragmentActivity).addToPlaylist(item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
                        CTX_FAV_ADD, CTX_FAV_REMOVE -> coroutineScope.launch { withContext(Dispatchers.IO) { item.isFavorite = ctxMenuItem.id == CTX_FAV_ADD } }
                        else -> {}
                    }
                }
                is Album -> {
                    when(ctxMenuItem.id) {
                        CTX_PLAY -> MediaUtils.playTracks(activity, item, 0)
                        CTX_PLAY_SHUFFLE -> MediaUtils.playTracks(activity, item, SecureRandom().nextInt(min(item.tracksCount, MEDIALIBRARY_PAGE_SIZE)), true)
                        CTX_APPEND -> MediaUtils.appendMedia(activity, item.tracks, showSnackbar)
                        CTX_PLAY_NEXT -> MediaUtils.insertNext(activity, item.tracks, showSnackbar)
                        CTX_INFORMATION -> mainActivityViewModel.showSnackbar(SnackbarContent(activity.resources.getString(R.string.not_implemented)))
                        CTX_GO_TO_ARTIST -> coroutineScope.launch(Dispatchers.IO) { TvUtil.openAudioCategory(activity, item.retrieveAlbumArtist()) }
                        CTX_ADD_TO_PLAYLIST -> (activity as FragmentActivity).addToPlaylist(item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
                        CTX_FAV_ADD, CTX_FAV_REMOVE -> coroutineScope.launch { withContext(Dispatchers.IO) { item.isFavorite = ctxMenuItem.id == CTX_FAV_ADD } }
                        CTX_DELETE -> {
                            ConfirmDeleteDialog.newInstance(arrayListOf(item)).show((activity as FragmentActivity).supportFragmentManager, ConfirmDeleteDialog::class.simpleName)
                        }
                        else -> {}
                    }
                }
                is Genre -> {
                    when(ctxMenuItem.id) {
                        CTX_PLAY -> MediaUtils.playTracks(activity, item, 0)
                        CTX_APPEND -> MediaUtils.appendMedia(activity, item.tracks, showSnackbar)
                        CTX_PLAY_NEXT -> MediaUtils.insertNext(activity, item.tracks, showSnackbar)
                        CTX_INFORMATION -> mainActivityViewModel.showSnackbar(SnackbarContent(activity.resources.getString(R.string.not_implemented)))
                        CTX_ADD_TO_PLAYLIST -> (activity as FragmentActivity).addToPlaylist(item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
                        CTX_FAV_ADD, CTX_FAV_REMOVE -> coroutineScope.launch { withContext(Dispatchers.IO) { item.isFavorite = ctxMenuItem.id == CTX_FAV_ADD } }
                        else -> {}
                    }
                }
                is Playlist -> {
                    when(ctxMenuItem.id) {
                        CTX_PLAY -> MediaUtils.playTracks(activity, item, 0)
                        CTX_PLAY_SHUFFLE -> MediaUtils.playTracks(activity, item, SecureRandom().nextInt(min(item.tracksCount, MEDIALIBRARY_PAGE_SIZE)), true)
                        CTX_APPEND -> MediaUtils.appendMedia(activity, item.tracks, showSnackbar)
                        CTX_PLAY_NEXT -> MediaUtils.insertNext(activity, item.tracks, showSnackbar)
                        CTX_PLAY_AS_AUDIO -> coroutineScope.launch(Dispatchers.IO) {
                            item.tracks?.let { trackArray ->
                                MediaUtils.openList(activity, trackArray.map {
                                    it.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                                    it
                                }.toList(), 0)
                            }
                        }
                        CTX_INFORMATION -> mainActivityViewModel.showSnackbar(SnackbarContent(activity.resources.getString(R.string.not_implemented)))
                        CTX_ADD_TO_PLAYLIST -> (activity as FragmentActivity).addToPlaylist(item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
                        CTX_FAV_ADD, CTX_FAV_REMOVE -> coroutineScope.launch { withContext(Dispatchers.IO) { item.isFavorite = ctxMenuItem.id == CTX_FAV_ADD } }
                        CTX_RENAME -> {
                            RenameDialog.newInstance(item).show((activity as FragmentActivity).supportFragmentManager, RenameDialog::class.simpleName)
                        }
                        CTX_DELETE -> {
                            ConfirmDeleteDialog.newInstance(arrayListOf(item)).show((activity as FragmentActivity).supportFragmentManager, ConfirmDeleteDialog::class.simpleName)
                        }
                        else -> { mainActivityViewModel.showSnackbar(SnackbarContent(activity.resources.getString(R.string.not_implemented)))}
                    }
                }
                else -> {
                    when(ctxMenuItem.id) {
                        CTX_PLAY_ALL -> MediaUtils.playAll(activity, provider as MedialibraryProvider<MediaWrapper>, position, false)
                        CTX_APPEND -> MediaUtils.appendMedia(activity, item.tracks, showSnackbar)
                        CTX_PLAY_NEXT -> MediaUtils.insertNext(activity, item.tracks, showSnackbar)
                        CTX_INFORMATION -> mainActivityViewModel.showSnackbar(SnackbarContent(activity.resources.getString(R.string.not_implemented)))
                        CTX_GO_TO_ALBUM -> coroutineScope.launch(Dispatchers.IO) { TvUtil.openAudioCategory(activity, (item as MediaWrapper).album) }
                        CTX_GO_TO_ARTIST -> coroutineScope.launch(Dispatchers.IO) { TvUtil.openAudioCategory(activity, (item as MediaWrapper).album.retrieveAlbumArtist()) }
                        CTX_ADD_TO_PLAYLIST -> (activity as FragmentActivity).addToPlaylist(item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
                        CTX_FAV_ADD, CTX_FAV_REMOVE -> coroutineScope.launch { withContext(Dispatchers.IO) { item.isFavorite = ctxMenuItem.id == CTX_FAV_ADD } }
                        CTX_DELETE -> {
                            ConfirmDeleteDialog.newInstance(arrayListOf(item)).show((activity as FragmentActivity).supportFragmentManager, ConfirmDeleteDialog::class.simpleName)
                        }
                        CTX_SHARE -> coroutineScope.launch { (activity as AppCompatActivity).share((item as MediaWrapper)) }
                        CTX_GO_TO_FOLDER -> (activity as FragmentActivity).showParent((item as MediaWrapper))
                        else -> {}
                    }
                }
            }
            if (BuildConfig.DEBUG) Log.d("CtxClickListener", "Ctx clicked: ${ctxMenuItem.id} for $item in list $entry")
        }

        val emptyState = if (audios.loadState.refresh == LoadState.Loading)
            EmptyLoadingState.LOADING
        else if (audios.itemCount == 0 && !Permissions.canReadStorage(context))
            EmptyLoadingState.MISSING_PERMISSION
        else if (audios.itemCount == 0 && !Permissions.canReadAudios(context))
            EmptyLoadingState.MISSING_AUDIO_PERMISSION
        else if (audios.itemCount == 0 && provider.onlyFavorites)
            EmptyLoadingState.EMPTY_FAVORITES
        else if (audios.itemCount == 0)
            EmptyLoadingState.EMPTY
        else
            EmptyLoadingState.NONE

        VlcEmptyViewLoader(emptyState) {
            Row(modifier = Modifier
                .fillMaxSize()
                .focusProperties {
                    onExit = {
                        if (requestedFocusDirection == FocusDirection.Up) onFocusExit()
                    }
                    onEnter = {
                        onFocusEnter()
                    }
                }) {
                if (emptyState != EmptyLoadingState.EMPTY_FAVORITES)
                    if (inCard) {
                        PaginatedGrid(
                            items = audios,
                            listState = gridState,
                            columns = GridCells.Fixed(6),
                            verticalArrangement = Arrangement.spacedBy(40.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .graphicsLayer(clip = false)
                        ) { audio, index, modifier ->
                            AudioItemCard(audio, index, entry, modifier, onClick = { onClick(audio, index) })
                        }
                    } else {
                        PaginatedList(
                            items = audios,
                            listState = listState,
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp),
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .graphicsLayer(clip = false)
                        ) { audio, index, modifier ->
                            AudioItemList(
                                item = audio,
                                position = index,
                                entry = entry,
                                modifier = modifier,
                                isFirst = index == 0,
                                isLast = index == audios.itemCount - 1,
                                onClick = { onClick(audio, index) }
                            )
                        }
                    }
                MediaListSidePanel(
                    MediaListSidePanelContent(
                        showScrollToTop = true,
                        showResumePlayback = entry != MediaListEntry.ALL_PLAYLISTS,
                        listState = if (inCard) gridState else listState,
                        entry = entry
                    )
                ) { first, second ->
                    when (first) {
                        MediaListSidePanelListenerKey.DISPLAY_MODE -> {
                            inCard = second as Boolean
                            Settings.getInstance(context).edit { putBoolean(entry.inCardsKey, inCard) }
                            invalidate()
                        }
                        MediaListSidePanelListenerKey.RESUME_PLAYBACK -> {
                            if (entry == MediaListEntry.VIDEO_PLAYLISTS)
                                MediaUtils.loadlastPlaylist(context, PLAYLIST_TYPE_VIDEO)
                            else
                                MediaUtils.loadlastPlaylist(context, PLAYLIST_TYPE_AUDIO)
                        }
                        MediaListSidePanelListenerKey.DISPLAY_SETTINGS -> {
                            mainActivityViewModel.openDisplaySettings(second as MediaListEntry)
                        }
                        else -> throw IllegalStateException("Invalid event")
                    }
                }
            }
        }
    }
}

@Composable
fun vlcBorder(focus: Boolean) = if (focus) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(0.dp, Transparent)

fun AudioBrowserViewModel.getProvider(entry: MediaListEntry): MedialibraryProvider<out MediaLibraryItem> {
    return when (entry) {
        MediaListEntry.ARTISTS -> artistsProvider
        MediaListEntry.ALBUMS -> albumsProvider
        MediaListEntry.TRACKS -> tracksProvider
        MediaListEntry.GENRES -> genresProvider
        else -> throw IllegalStateException("Invalid provider")
    }
}