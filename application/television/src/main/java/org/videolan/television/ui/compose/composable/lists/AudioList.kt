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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.PLAYLIST_TYPE_AUDIO
import org.videolan.resources.PLAYLIST_TYPE_VIDEO
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.MediaListSidePanel
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelContent
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelListenerKey
import org.videolan.television.ui.compose.composable.components.PaginatedGrid
import org.videolan.television.ui.compose.composable.components.PaginatedList
import org.videolan.television.ui.compose.composable.components.VLCTabRow
import org.videolan.television.ui.compose.composable.components.VlcLoader
import org.videolan.television.ui.compose.composable.items.AudioItemCard
import org.videolan.television.ui.compose.composable.items.AudioItemList
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.vlc.util.MediaListEntry
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.tools.KEY_AUDIO_TAB
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.viewmodels.mobile.AudioBrowserViewModel
import org.videolan.vlc.viewmodels.mobile.PlaylistsViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioListScreen(onFocusExit: () -> Unit, onFocusEnter: () -> Unit, mainActivityViewModel: MainActivityViewModel = viewModel()) {
    val context = LocalContext.current
    val settings = Settings.getInstance(context)
    val pagerState = rememberPagerState(
        initialPage = settings.getInt(KEY_AUDIO_TAB, 0),
        pageCount = { mainActivityViewModel.audioTabs.size }
    )
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
    ) {
        VLCTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .focusProperties {
                    onEnter = {
                        onFocusEnter()
                    }
                    onExit = {
                        if (requestedFocusDirection == FocusDirection.Up) {
                            if (BuildConfig.DEBUG) Log.d("MainScreenLogs", "onFocusExit triggered for Column")
                            onFocusExit()
                        }
                    }
                }
                .focusGroup(),

            onSelected = { index ->
                if (index == pagerState.currentPage) return@VLCTabRow
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
                settings.edit { putInt(KEY_AUDIO_TAB, index) }

            },
            tabNumber = mainActivityViewModel.audioTabs.size,
            indicator = { hasFocus ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (hasFocus) White else WhiteTransparent50, RoundedCornerShape(50))
                )
            },
            key = "audio",
            getTab = { index, focused ->
                val tab = mainActivityViewModel.audioTabs[index]
                Text(
                    style = MaterialTheme.typography.labelLarge,
                    text = stringResource(tab),
                    color = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.4F),
                    modifier = Modifier
                        .padding(vertical = 0.dp, horizontal = 8.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        )
        HorizontalPager(
            pagerState,
            userScrollEnabled = false,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> MediaList(MediaListEntry.ARTISTS)
                1 -> MediaList(MediaListEntry.ALBUMS)
                2 -> MediaList(MediaListEntry.TRACKS)
                3 -> MediaList(MediaListEntry.GENRES)
                4 -> MediaList(MediaListEntry.AUDIO_PLAYLISTS)
            }
        }
    }
}

@Composable
fun MediaList(entry: MediaListEntry) {
    InvalidationComposable { invalidate ->
        val context = LocalContext.current

        val provider: MedialibraryProvider<out MediaLibraryItem>

        when(entry) {
            in arrayOf(MediaListEntry.ALL_PLAYLISTS, MediaListEntry.VIDEO_PLAYLISTS, MediaListEntry.AUDIO_PLAYLISTS) -> {
                val extras = MutableCreationExtras().apply {
                    set(APPLICATION_KEY, context.applicationContext as Application)
                    set(PlaylistsViewModel.PLAYLIST_TYPE, when(entry) {
                        MediaListEntry.VIDEO_PLAYLISTS -> Playlist.Type.Video
                        MediaListEntry.AUDIO_PLAYLISTS -> Playlist.Type.Audio
                        else -> Playlist.Type.All
                    })
                }
                val playlistsViewModel:PlaylistsViewModel = viewModel(
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
                provider = audioBrowserViewModel.getProvider(entry)
            }
        }

        val audios = provider.pager.collectAsLazyPagingItems()
        var inCard by remember { mutableStateOf(entry.displayInCard(context)) }

        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()
        VlcLoader(audios.loadState.refresh == LoadState.Loading) {
            Row {
                if (inCard) {
                    PaginatedGrid(
                        items = audios,
                        listState = gridState,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(top = 16.dp),
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    ) { audio, modifier ->
                        AudioItemCard(audio, modifier)
                    }
                } else {
                    PaginatedList(
                        items = audios,
                        listState = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 16.dp),
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    ) { audio, modifier ->
                        AudioItemList(audio, modifier)
                    }
                }
                MediaListSidePanel(MediaListSidePanelContent(
                    showScrollToTop = true,
                    showResumePlayback = entry != MediaListEntry.ALL_PLAYLISTS,
                    if (inCard) gridState else listState
                )) { first, second ->
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
                        else -> {}
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