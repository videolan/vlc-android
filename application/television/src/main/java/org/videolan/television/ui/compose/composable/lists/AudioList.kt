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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import org.videolan.television.ui.compose.composable.components.MediaListSidePanel
import org.videolan.television.ui.compose.composable.components.PaginatedLazyColumn
import org.videolan.television.ui.compose.composable.components.PaginatedLazyGrid
import org.videolan.television.ui.compose.composable.components.VLCTabRow
import org.videolan.television.ui.compose.composable.components.VlcLoader
import org.videolan.television.ui.compose.composable.items.AudioItemCard
import org.videolan.television.ui.compose.composable.items.AudioItemList
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.MediaListModelEntry
import org.videolan.television.viewmodel.MediaListsViewModel
import org.videolan.tools.KEY_AUDIO_TAB
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig


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
                0 -> MediaList(MediaListModelEntry.ARTISTS)
                1 -> MediaList(MediaListModelEntry.ALBUMS)
                2 -> MediaList(MediaListModelEntry.TRACKS)
                3 -> MediaList(MediaListModelEntry.GENRES)
                4 -> MediaList(MediaListModelEntry.AUDIO_PLAYLISTS)
            }
        }
    }
}

@Composable
fun MediaList(entry: MediaListModelEntry, viewModel: MediaListsViewModel = viewModel()) {
    val context = LocalContext.current
    val audios by entry.getMediaList(viewModel).observeAsState()
    val audioLoading by entry.getLoadingState(viewModel).observeAsState()
    val inCard = entry.displayInCard(context)
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    VlcLoader(audioLoading) {
        Row {
            if (inCard) {
                PaginatedLazyGrid(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    listState = gridState,
                    items = audios?.toPersistentList() ?: persistentListOf(),
                    loadMoreItems = { viewModel.loadMore(entry) },
                    isLoading = audioLoading ?: false,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(top = 16.dp),
                ) { audio, modifier ->
                    AudioItemCard(audio, modifier)
                }
            } else {
                PaginatedLazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    items = audios?.toPersistentList() ?: persistentListOf(),
                    loadMoreItems = { viewModel.loadMore(entry) },
                    listState = listState,
                    isLoading = audioLoading ?: false,
                    contentPadding = PaddingValues(top = 16.dp)
                ) { audio, modifier ->
                    AudioItemList(audio, modifier)
                }
            }
            MediaListSidePanel(inCard, if (inCard) gridState else listState, entry)
        }
    }
}

@Composable
fun vlcBorder(focus: Boolean) = if (focus) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(0.dp, Transparent)