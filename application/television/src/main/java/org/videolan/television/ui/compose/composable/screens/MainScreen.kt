/*
 * ************************************************************************
 *  MainScreen.kt
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

package org.videolan.television.ui.compose.composable.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.components.AudioPlayer
import org.videolan.television.ui.compose.composable.components.MlProgress
import org.videolan.television.ui.compose.composable.components.RoundedRectangleIndicator
import org.videolan.television.ui.compose.composable.lists.AudioListScreen
import org.videolan.television.ui.compose.composable.lists.BrowseList
import org.videolan.television.ui.compose.composable.lists.MoreScreen
import org.videolan.television.ui.compose.composable.lists.PlaylistsList
import org.videolan.television.ui.compose.composable.lists.VideoListScreen
import org.videolan.television.viewmodel.MainActivityViewModel

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    Box {
        MainContent()
        MlProgress(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@Composable
fun MainContent() {
    Row {
        AudioPlayer()
        Tabs(modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tabs(modifier: Modifier = Modifier, viewModel: MainActivityViewModel = viewModel()) {
    val tabs = viewModel.tabs
    val focusRequesters = remember {
        List(tabs.size) { FocusRequester() }
    }
    var hasFocus by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }
    val pagerState = rememberPagerState(
        pageCount = { tabs.size }
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = if (visible) 24.dp else 0.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = visible,
            transitionSpec = {
                if (!targetState) {
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                } else {
                    slideInVertically { height -> -height } + fadeIn() togetherWith
                            slideOutVertically { height -> height } + fadeOut()
                }.using(
                    SizeTransform(clip = false)
                )
            }, label = "tabs collapsing animation"
        ) { tabsVisible ->
            if (tabsVisible)
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    divider = {},
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .focusProperties {
                            onEnter = {
                                focusRequesters[pagerState.currentPage].requestFocus()
                                hasFocus = true
                            }
                            onExit = {
                                hasFocus = false
                            }
                        }
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .align(Alignment.CenterHorizontally)
                        .focusGroup(),
                    indicator = { RoundedRectangleIndicator(pagerState.currentPage, hasFocus) }
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = pagerState.currentPage == index
                        val coroutineScope = rememberCoroutineScope()
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
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                },
                            text = {
                                Row {
                                    Icon(
                                        painter = painterResource(tab.second),
                                        tint = if (pagerState.currentPage == index) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.4F),
                                        contentDescription = stringResource(id = R.string.reset),
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(24.dp)
                                    )
                                    Text(
                                        text = stringResource(tab.first),
                                        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.4F),
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp, vertical = 0.dp)
                                            .align(Alignment.CenterVertically)
                                    )
                                }
                            },
                            icon = null,
                            enabled = true,
                        )
                    }
                }
            else
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusable(true)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_collapse_arrow),
                        contentDescription = stringResource(id = R.string.reset),
                        modifier = Modifier
                            .width(24.dp)
                            .height(24.dp)
                            .align(Alignment.Center)
                            .rotate(180f)
                            .alpha(0.5f)
                    )
                }

        }
        VLCContentPanel(pagerState) {
            visible = it
        }
    }
}

@Composable
private fun VLCContentPanel(pagerState: PagerState, onVisibleChange: (Boolean) -> Unit) {
    HorizontalPager(
        pagerState,
        userScrollEnabled = false,
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxSize()
    ) { page ->
        TabPanels(
            page,
            onFocusExit = { onVisibleChange(true) },
            onFocusEnter = { onVisibleChange(false) })
    }
}

@Composable
private fun TabPanels(pagerState: Int, onFocusExit: () -> Unit, onFocusEnter: () -> Unit, viewModel: MainActivityViewModel = viewModel()) {
    when (viewModel.tabs[pagerState].first) {
        R.string.video -> VideoListScreen(onFocusExit = { onFocusExit() }, onFocusEnter = { onFocusEnter() })
        R.string.audio -> AudioListScreen(onFocusExit = { onFocusExit() }, onFocusEnter = { onFocusEnter() })
        R.string.browse -> BrowseList(onFocusExit = { onFocusExit() }, onFocusEnter = { onFocusEnter() })
        R.string.playlists -> PlaylistsList(onFocusExit = { onFocusExit() }, onFocusEnter = { onFocusEnter() })
        else -> MoreScreen(onFocusExit = { onFocusExit() }, onFocusEnter = { onFocusEnter() })
    }
}