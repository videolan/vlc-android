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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.components.AudioPlayer
import org.videolan.television.ui.compose.composable.components.DisplaySettings
import org.videolan.television.ui.compose.composable.components.MlProgress
import org.videolan.television.ui.compose.composable.components.SplashScreen
import org.videolan.television.ui.compose.composable.components.VLCTabRow
import org.videolan.television.ui.compose.composable.lists.AudioListScreen
import org.videolan.television.ui.compose.composable.lists.BrowseList
import org.videolan.television.ui.compose.composable.lists.MoreScreen
import org.videolan.television.ui.compose.composable.lists.PlaylistsList
import org.videolan.television.ui.compose.composable.lists.VideoListScreen
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.tools.KEY_MAIN_TAB
import org.videolan.tools.Settings

@Composable
fun MainScreen() {
    Box {
        SplashScreen {
            MainContent()
            MlProgress(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
            DisplaySettings()
        }
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
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = Settings.getInstance(context)

    val tabs = viewModel.tabs
    var firstLaunch by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(true) }
    val pagerState = rememberPagerState(
        initialPage = settings.getInt(KEY_MAIN_TAB, 0),
        pageCount = { tabs.size }
    )

    val duration = 300
    val animatedPadding by animateDpAsState(
        if (visible) {
            24.dp
        } else {
            0.dp
        },
        tween(duration),
        label = "padding"
    )

    BackHandler(enabled = !visible) {
        visible = true
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(
                top = animatedPadding,
                start = 32.dp,
                end = 32.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = visible,
            transitionSpec = {
                if (!targetState) {
                    slideInVertically(tween(duration)) { height -> height } + fadeIn(tween(duration)) togetherWith
                            slideOutVertically(tween(duration)) { height -> -height } + fadeOut(tween(duration))
                } else {
                    slideInVertically(tween(duration)) { height -> -height } + fadeIn(tween(duration)) togetherWith
                            slideOutVertically(tween(duration)) { height -> 0 } + fadeOut(tween(duration))
                }.using(
                    SizeTransform(clip = false)
                )
            }, label = "tabs collapsing animation"
        ) { tabsVisible ->
            if (tabsVisible) {
                val forceFocus = (this.transition.isRunning || firstLaunch) && visible
                firstLaunch = false
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.focusGroup()
                ) {
                    Spacer(modifier = Modifier.width(48.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    Box(modifier = Modifier) {
                        VLCTabRow(
                            selectedTabIndex = pagerState.currentPage,
                            onSelected = { index ->
                                if (index == pagerState.currentPage) return@VLCTabRow
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                                settings.edit { putInt(KEY_MAIN_TAB, index) }
                            },
                            modifier = Modifier,
                            forceFocus = forceFocus,
                            indicator = { hasFocus ->
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(if (hasFocus) White else WhiteTransparent50, RoundedCornerShape(50))
                                )
                            },
                            tabNumber = tabs.size,
                            key = "main",
                            getTab = { index, focused ->
                                val tab = tabs[index]
                                val animatedColor by animateColorAsState(
                                    targetValue = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.4F),
                                    label = "color"
                                )
                                if (tab.first == R.string.search) {
                                    Icon(
                                        painter = painterResource(tab.second),
                                        tint = animatedColor,
                                        contentDescription = stringResource(id = R.string.reset),
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(24.dp)
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .height(24.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(tab.second),
                                            tint = animatedColor,
                                            contentDescription = stringResource(id = R.string.reset),
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .width(24.dp)
                                                .height(24.dp)
                                        )
                                        Text(
                                            text = stringResource(tab.first),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = animatedColor,
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .align(Alignment.CenterVertically)
                                        )
                                    }
                                }
                            }
                        )

                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Image(
                        painter = painterResource(id = R.drawable.icon),
                        contentDescription = stringResource(id = R.string.app_name),
                        modifier = Modifier.size(48.dp)
                    )
                }
            } else {
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
        }
        VLCContentPanel(pagerState) {
            visible = it
        }
    }
}

@Composable
private fun VLCContentPanel(pagerState: PagerState, modifier: Modifier = Modifier, onVisibleChange: (Boolean) -> Unit) {
    HorizontalPager(
        pagerState,
        userScrollEnabled = false,
        verticalAlignment = Alignment.Top,
        modifier = modifier
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