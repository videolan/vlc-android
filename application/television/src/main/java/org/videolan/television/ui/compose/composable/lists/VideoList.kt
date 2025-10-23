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

import android.app.Application
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
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.MediaListSidePanel
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelListenerKey
import org.videolan.television.ui.compose.composable.components.PaginatedGrid
import org.videolan.television.ui.compose.composable.components.PaginatedList
import org.videolan.television.ui.compose.composable.components.VLCTabRow
import org.videolan.television.ui.compose.composable.components.VlcLoader
import org.videolan.television.ui.compose.composable.items.VideoItem
import org.videolan.television.ui.compose.composable.items.VideoItemList
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.tools.KEY_GROUP_VIDEOS
import org.videolan.tools.KEY_VIDEOS_CARDS
import org.videolan.tools.KEY_VIDEO_TAB
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType
import org.videolan.vlc.viewmodels.mobile.VideosViewModel

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
                1 -> MediaList(AudioListEntry.VIDEO_PLAYLISTS)
            }
        }
    }
}


@Composable
fun VideoList() {
    InvalidationComposable { invalidate ->
        val context = LocalContext.current

        val extras = MutableCreationExtras().apply {
            set(VideosViewModel.PARENT_GROUP_KEY, null)
            set(VideosViewModel.PARENT_FOLDER_KEY, null)
            set(APPLICATION_KEY, context.applicationContext as Application)
        }
        val viewModel: VideosViewModel = viewModel(
            factory = VideosViewModel.Factory,
            extras = extras,
        )


        val videos = viewModel.provider.pager.collectAsLazyPagingItems()
        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()
        var inCard by remember { mutableStateOf(Settings.getInstance(context).getBoolean(KEY_VIDEOS_CARDS, true)) }

        VlcLoader(videos.loadState.refresh == LoadState.Loading) {
            Row {
                if (inCard) {
                    PaginatedGrid(
                        items = videos,
                        listState = gridState,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 16.dp),
                        loaderAspectRatio = 16f / 9,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    ) { video, modifier ->
                        VideoItem(video, modifier = modifier)
                    }
                } else {
                    PaginatedList(
                        items = videos,
                        listState = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 16.dp),
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    ) { video, modifier ->
                        VideoItemList(video, modifier = modifier)
                    }
                }
                MediaListSidePanel(inCard, if (inCard) gridState else listState, grouping = viewModel.groupingType) { first, second ->
                    when (first) {
                        MediaListSidePanelListenerKey.DISPLAY_MODE -> {
                            inCard = second as Boolean
                            Settings.getInstance(context).edit { putBoolean(KEY_VIDEOS_CARDS, inCard) }
                            invalidate()
                        }

                        MediaListSidePanelListenerKey.GROUPING -> {
                            val videoGroup = second as VideoGroupingType
                            Settings.getInstance(context).putSingle(KEY_GROUP_VIDEOS, videoGroup.settingsKey)
                            viewModel.changeGroupingType(videoGroup)
                            invalidate()
                        }
                    }
                }
            }
        }
    }

}