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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.components.VlcLoader
import org.videolan.television.ui.compose.composable.items.AudioItemCard
import org.videolan.television.ui.compose.composable.items.AudioItemList
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.MediaListModelEntry
import org.videolan.television.viewmodel.MediaListsViewModel
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioListScreen(onFocusExit: () -> Unit, onFocusEnter: () -> Unit, viewModel: MediaListsViewModel = viewModel(), mainActivityViewModel: MainActivityViewModel = viewModel()) {
    val context = LocalContext.current
    val settings = Settings.getInstance(context)
    val focusRequesters = remember {
        List(mainActivityViewModel.audioTabs.size) { FocusRequester() }
    }
    var hasFocus by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        pageCount = { mainActivityViewModel.audioTabs.size }
    )
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
    ) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            divider = {},
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.background,
            edgePadding = 0.dp,
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .focusProperties {
                    onEnter = {
                        focusRequesters[pagerState.currentPage].requestFocus()
                        hasFocus = true
                        onFocusEnter()

                    }
                    onExit = {
                        hasFocus = false
                        if (requestedFocusDirection == FocusDirection.Up) {
                            if (BuildConfig.DEBUG) Log.d("MainScreenLogs", "onFocusExit triggered for Column")
                            onFocusExit()
                        }
                    }
                }
                .focusGroup(),
            indicator = { tabPositions ->
                Box(
                    Modifier
                        .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(
                            color = if (hasFocus) White else WhiteTransparent50,
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }
        ) {
            mainActivityViewModel.audioTabs.forEachIndexed { index, tab ->
                val selected = pagerState.currentPage == index
                Tab(
                    selected = selected,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .zIndex(2f)
                        .focusRequester(focusRequester = focusRequesters[index])
                        .onFocusChanged {
                            if (it.isFocused) {
                                settings.edit { putInt("audio_tab", index) }
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        },
                    text = {
                        Text(
                            text = stringResource(tab),
                            color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.4F),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 0.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    },
                    icon = null,
                    enabled = true,
                )
            }
        }
        HorizontalPager(pagerState,
            userScrollEnabled = false,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> MediaList(MediaListModelEntry.ARTISTS)
                1 -> MediaList(MediaListModelEntry.ALBUMS)
                2 -> MediaList(MediaListModelEntry.TRACKS)
                3 -> MediaList(MediaListModelEntry.GENRES)
                4 -> MediaList(MediaListModelEntry.AUDIO_PLAYLISTS)
            }
        }
    }
    LaunchedEffect(Unit) {
        // Initial tab selection
        pagerState.scrollToPage(settings.getInt("audio_tab", 0))
    }
}

@Composable
fun MediaList(entry: MediaListModelEntry, viewModel: MediaListsViewModel = viewModel()) {
    val context = LocalContext.current
    entry.updateMediaList(viewModel)
    val audios by entry.getMediaList(viewModel).observeAsState()
    val audioLoading by entry.getLoadingState(viewModel).observeAsState()
    val inCard = entry.displayInCard(context)
    VlcLoader(audioLoading) {
            if (inCard) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(audios?.size ?: 0, key = { index -> audios!![index].id }) { index ->
                        AudioItemCard(audios!!, index)

                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(audios?.size ?: 0, key = { index -> audios!![index].id }) { index ->
                        AudioItemList(audios!!, index)

                    }
                }
        }
    }
}

@Composable
fun vlcBorder(focus: Boolean) = if (focus) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(0.dp, Transparent)