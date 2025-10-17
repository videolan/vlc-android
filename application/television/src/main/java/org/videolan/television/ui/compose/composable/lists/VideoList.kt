/*
 * ************************************************************************
 *  VideoList.kt
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import org.videolan.television.ui.compose.composable.components.MediaListSidePanel
import org.videolan.television.ui.compose.composable.components.PaginatedLazyColumn
import org.videolan.television.ui.compose.composable.components.PaginatedLazyGrid
import org.videolan.television.ui.compose.composable.components.VLCTabRow
import org.videolan.television.ui.compose.composable.components.VlcLoader
import org.videolan.television.ui.compose.composable.items.VideoItem
import org.videolan.television.ui.compose.composable.items.VideoItemList
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.MediaListModelEntry
import org.videolan.television.viewmodel.MediaListsViewModel
import org.videolan.tools.KEY_VIDEO_TAB
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig

@Composable
fun VideoListScreen(onFocusExit: () -> Unit, onFocusEnter: () -> Unit, mainActivityViewModel: MainActivityViewModel = viewModel()) {
    val context = LocalContext.current
    val settings = Settings.getInstance(context)
    val pagerState = rememberPagerState(
        initialPage = settings.getInt(KEY_VIDEO_TAB, 0),
        pageCount = { mainActivityViewModel.videoTabs.size }
    )
    val coroutineScope = rememberCoroutineScope()
    var firstLaunch by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
    ) {
        VLCTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .focusProperties {
                    onEnter = {
                        if (!firstLaunch)
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
                settings.edit { putInt(KEY_VIDEO_TAB, index) }
            },
            tabNumber = mainActivityViewModel.videoTabs.size,
            indicator = { hasFocus ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (hasFocus) White else WhiteTransparent50, RoundedCornerShape(50))
                )
            },
            key = "video",
            getTab = { index, focused ->
                val tab = mainActivityViewModel.videoTabs[index]
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
                0 -> VideoList()
                1 -> MediaList(MediaListModelEntry.VIDEO_PLAYLISTS)
            }
        }
    }
}


@Composable
fun VideoList(viewModel: MediaListsViewModel = viewModel()) {
    val context = LocalContext.current
    val videos by MediaListModelEntry.VIDEO.getMediaList(viewModel).observeAsState()
    val videoLoading by MediaListModelEntry.VIDEO.getLoadingState(viewModel).observeAsState()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val inCard = MediaListModelEntry.VIDEO.displayInCard(context)
    VlcLoader(videoLoading) {

        Row {
            if (inCard) {
                PaginatedLazyGrid(
                    items = videos?.toPersistentList() ?: persistentListOf(),
                    loadMoreItems = { viewModel.loadMore(MediaListModelEntry.VIDEO) },
                    listState = gridState,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp),
                    isLoading = videoLoading ?: false,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) { video, modifier ->
                    VideoItem(video, modifier = modifier)
                }
            } else {
                PaginatedLazyColumn(
                    items = videos?.toPersistentList() ?: persistentListOf(),
                    loadMoreItems = { viewModel.loadMore(MediaListModelEntry.VIDEO) },
                    listState = listState,
                    contentPadding = PaddingValues(top = 16.dp),
                    isLoading = videoLoading ?: false,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) { video, modifier ->
                    VideoItemList(video, modifier = modifier)
                }
            }
            MediaListSidePanel(inCard, if (inCard) gridState else listState, MediaListModelEntry.VIDEO)
        }
    }
}