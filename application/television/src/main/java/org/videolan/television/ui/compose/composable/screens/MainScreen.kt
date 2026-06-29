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

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import org.videolan.television.R
import org.videolan.television.ui.MainTvActivity
import org.videolan.television.ui.SearchActivity
import org.videolan.television.ui.compose.AudioDestination
import org.videolan.television.ui.compose.MainDestination
import org.videolan.television.ui.compose.VideoDestination
import org.videolan.television.ui.compose.composable.components.AudioPlayer
import org.videolan.television.ui.compose.composable.components.DisplaySettings
import org.videolan.television.ui.compose.composable.components.LabeledIconButton
import org.videolan.television.ui.compose.composable.components.MlProgress
import org.videolan.television.ui.compose.composable.components.SplashScreen
import org.videolan.television.ui.compose.composable.components.VLCTabRow
import org.videolan.television.ui.compose.composable.lists.AudioListScreen
import org.videolan.television.ui.compose.composable.lists.BrowseList
import org.videolan.television.ui.compose.composable.lists.MoreScreen
import org.videolan.television.ui.compose.composable.lists.PlaylistsList
import org.videolan.television.ui.compose.composable.lists.VideoListScreen
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.SnackbarContent
import org.videolan.tools.KEY_AUDIO_TAB
import org.videolan.tools.KEY_MAIN_TAB
import org.videolan.tools.KEY_VIDEO_TAB
import org.videolan.tools.Settings

@Composable
fun MainScreen(viewModel: MainActivityViewModel = viewModel()) {
    val snackbarContent by viewModel.snackBarFlow.collectAsState()
    MainScreenContent(
        tabs = viewModel.tabs,
        videoTabs = viewModel.videoTabs,
        audioTabs = viewModel.audioTabs,
        snackbarContent = snackbarContent,
        onSnackbarDismissed = { viewModel.showSnackbar(null) },
        viewModel = viewModel
    )
}

@Composable
fun MainScreenContent(
    tabs: List<Pair<Int, Int>>,
    videoTabs: List<Int>,
    audioTabs: List<Int>,
    snackbarContent: SnackbarContent? = null,
    onSnackbarDismissed: () -> Unit = {},
    viewModel: MainActivityViewModel? = null
) {
    SplashScreen {
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(snackbarContent) {
            snackbarContent?.let { snackbarContent ->
                scope.launch {
                    snackbarHostState.showSnackbar(snackbarContent.message, duration = snackbarContent.duration)
                    onSnackbarDismissed()
                }
            }
        }
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
        ) { contentPadding ->
            Box {
                MainContent(Modifier.padding(contentPadding), tabs, videoTabs, audioTabs, viewModel)
                if (viewModel != null) {
                    MlProgress(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        mainActivityViewModel = viewModel
                    )
                    DisplaySettings(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainContent(
    modifier: Modifier,
    tabs: List<Pair<Int, Int>>,
    videoTabs: List<Int>,
    audioTabs: List<Int>,
    viewModel: MainActivityViewModel? = null
) {
    Row(modifier) {
        if (viewModel != null) AudioPlayer()
        Tabs(
            modifier = Modifier.weight(1f),
            tabs = tabs,
            videoTabs = videoTabs,
            audioTabs = audioTabs,
            viewModel = viewModel
        )
    }
}

@Composable
fun Tabs(modifier: Modifier = Modifier, viewModel: MainActivityViewModel = viewModel()) {
    Tabs(
        modifier = modifier,
        tabs = viewModel.tabs,
        videoTabs = viewModel.videoTabs,
        audioTabs = viewModel.audioTabs,
        viewModel = viewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tabs(
    modifier: Modifier = Modifier,
    tabs: List<Pair<Int, Int>>,
    videoTabs: List<Int>,
    audioTabs: List<Int>,
    viewModel: MainActivityViewModel? = null
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val isPreview = LocalInspectionMode.current
    val settings = if (isPreview) null else Settings.getInstance(context)

    var firstLaunch by rememberSaveable { mutableStateOf(true) }
    var visible by rememberSaveable { mutableStateOf(true) }
    var wasVisible by remember { mutableStateOf(visible) }
    val visibleChangedToTrue = visible && !wasVisible
    LaunchedEffect(visible) {
        wasVisible = visible
        viewModel?.setShowTabs(visible)
    }

    val savedTab = settings?.getInt(KEY_MAIN_TAB, 0) ?: 0
    val initialDestination = remember {
        when (savedTab) {
            1 -> {
                val audioTabIndex = settings?.getInt(KEY_AUDIO_TAB, 0) ?: 0
                MainDestination.Audio(AudioDestination.entries.getOrElse(audioTabIndex) { AudioDestination.Artists })
            }
            2 -> MainDestination.Browse
            3 -> MainDestination.Playlists
            4 -> MainDestination.More
            else -> {
                val videoTabIndex = settings?.getInt(KEY_VIDEO_TAB, 0) ?: 0
                MainDestination.Video(VideoDestination.entries.getOrElse(videoTabIndex) { VideoDestination.Videos })
            }
        }
    }

    val backStack = rememberNavBackStack(initialDestination)
    val currentDestination = backStack.lastOrNull() as? MainDestination
    val hasSubtabs = currentDestination is MainDestination.Audio || currentDestination is MainDestination.Video

    val duration = 300
    val animatedPadding by animateDpAsState(
        if (visible) {
            28.dp
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
                start = 56.dp,
                end = 56.dp
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
                val forceFocus = (this.transition.isRunning || firstLaunch) && visible && (!hasSubtabs || firstLaunch)
                firstLaunch = false
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.focusGroup()
                    ) {
                        LabeledIconButton(stringResource(R.string.old_ui), vectorImage = Icons.Default.Palette) {
                            activity?.startActivity(Intent(activity.applicationContext, MainTvActivity::class.java))
                            activity?.finish()
                        }
                        LabeledIconButton(stringResource(R.string.search), vectorImage = Icons.Default.Search) {
                            activity?.startActivity(Intent(context, SearchActivity::class.java))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Box(modifier = Modifier) {
                            VLCTabRow(
                                selectedTabIndex = tabs.indexOfFirst { it.first == (backStack.lastOrNull() as? MainDestination)?.titleRes },
                                onSelected = { index ->
                                    val destination = when (index) {
                                        0 -> {
                                            val videoTabIndex = settings?.getInt(KEY_VIDEO_TAB, 0) ?: 0
                                            MainDestination.Video(VideoDestination.entries.getOrElse(videoTabIndex) { VideoDestination.Videos })
                                        }

                                        1 -> {
                                            val audioTabIndex = settings?.getInt(KEY_AUDIO_TAB, 0) ?: 0
                                            MainDestination.Audio(AudioDestination.entries.getOrElse(audioTabIndex) { AudioDestination.Artists })
                                        }

                                        2 -> MainDestination.Browse
                                        3 -> MainDestination.Playlists
                                        else -> MainDestination.More
                                    }
                                    backStack.clear()
                                    backStack.add(destination)
                                    settings?.edit { putInt(KEY_MAIN_TAB, index) }
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(WhiteTransparent10)
                                    .padding(4.dp),
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
                                mainActivityViewModel = viewModel,
                                getTab = { index, focused ->
                                    val tab = tabs[index]
                                    val animatedColor by animateColorAsState(
                                        targetValue = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.6F),
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
                        Spacer(modifier = Modifier.width(48.dp))
                        Image(
                            painter = painterResource(id = R.drawable.icon),
                            contentDescription = stringResource(id = R.string.app_name),
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(48.dp)
                        )
                    }
                    SubTabs(backStack, videoTabs, audioTabs, forceFocus = (visibleChangedToTrue && !firstLaunch) && hasSubtabs, viewModel = viewModel)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) visible = true }
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
        VLCContentPanel(backStack) {
            visible = it
        }
    }
}

@Composable
private fun SubTabs(
    backStack: NavBackStack<NavKey>,
    videoTabs: List<Int>,
    audioTabs: List<Int>,
    forceFocus: Boolean = false,
    viewModel: MainActivityViewModel? = null
) {
    val currentKey = backStack.lastOrNull() as? MainDestination ?: return
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val settings = if (isPreview) null else Settings.getInstance(context)

    // Use a stable key to prevent VLCTabRow recreation when switching subtabs within the same category.
    // This ensures focus is not lost during sub-navigation.
    val categoryKey = when (currentKey) {
        is MainDestination.Video -> "video"
        is MainDestination.Audio -> "audio"
        else -> "none"
    }

    AnimatedContent(
        targetState = categoryKey,
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
        transitionSpec = {
            if (targetState != "none") {
                (expandVertically(tween(300), expandFrom = Alignment.Top) + fadeIn(tween(300))) togetherWith fadeOut(tween(300))
            } else {
                fadeIn(tween(300)) togetherWith (shrinkVertically(tween(300), shrinkTowards = Alignment.Top) + fadeOut(tween(300)))
            }.using(
                SizeTransform(clip = false)
            )
        },
        label = "subtabs animation"
    ) { category ->
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when (category) {
                "video" -> {
                    val subDestination = (currentKey as? MainDestination.Video)?.subDestination ?: VideoDestination.Videos
                    VLCTabRow(
                        selectedTabIndex = VideoDestination.entries.indexOf(subDestination),
                        modifier = Modifier
                            .padding(top = 16.dp, start = 8.dp, end = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(WhiteTransparent10)
                            .padding(4.dp),
                        onSelected = { index ->
                            val newDest = MainDestination.Video(VideoDestination.entries[index])
                            backStack.clear()
                            backStack.add(newDest)
                            settings?.edit { putInt(KEY_VIDEO_TAB, index) }
                        },
                        tabNumber = videoTabs.size,
                        forceFocus = forceFocus,
                        indicator = { hasFocus ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(if (hasFocus) White else WhiteTransparent50, RoundedCornerShape(50))
                            )
                        },
                        key = "video_sub",
                        mainActivityViewModel = viewModel,
                        getTab = { index, focused ->
                            val tab = videoTabs[index]
                            val animatedColor by animateColorAsState(
                                targetValue = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.6F),
                                label = "color"
                            )
                            Text(
                                text = stringResource(tab),
                                style = MaterialTheme.typography.labelLarge,
                                color = animatedColor,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .height(24.dp)
                                    .wrapContentHeight(Alignment.CenterVertically)
                            )
                        }
                    )
                }

                "audio" -> {
                    val subDestination = (currentKey as? MainDestination.Audio)?.subDestination ?: AudioDestination.Artists
                    VLCTabRow(
                        selectedTabIndex = AudioDestination.entries.indexOf(subDestination),
                        modifier = Modifier
                            .padding(top = 16.dp, start = 8.dp, end = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(WhiteTransparent10)
                            .padding(4.dp),
                        onSelected = { index ->
                            val newDest = MainDestination.Audio(AudioDestination.entries[index])
                            backStack.clear()
                            backStack.add(newDest)
                            settings?.edit { putInt(KEY_AUDIO_TAB, index) }
                        },
                        tabNumber = audioTabs.size,
                        forceFocus = forceFocus,
                        indicator = { hasFocus ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(if (hasFocus) White else WhiteTransparent50, RoundedCornerShape(50))
                            )
                        },
                        key = "audio_sub",
                        mainActivityViewModel = viewModel,
                        getTab = { index, focused ->
                            val tab = audioTabs[index]
                            val animatedColor by animateColorAsState(
                                targetValue = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.6F),
                                label = "color"
                            )
                            Text(
                                text = stringResource(tab),
                                style = MaterialTheme.typography.labelLarge,
                                color = animatedColor,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .height(24.dp)
                                    .wrapContentHeight(Alignment.CenterVertically)
                            )
                        }
                    )
                }

                else -> {
                    Spacer(Modifier.height(0.dp))
                }
            }
        }
    }
}

@Composable
private fun VLCContentPanel(backStack: NavBackStack<NavKey>, modifier: Modifier = Modifier, onVisibleChange: (Boolean) -> Unit) {
    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier
                .padding(top = 8.dp)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Content Panel Placeholder", style = MaterialTheme.typography.headlineMedium)
        }
        return
    }
    NavDisplay(
        backStack = backStack,
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxSize()
    ) { destination ->
        NavEntry(destination) { destinationKey ->
            TabPanels(
                destinationKey as MainDestination,
                onFocusExit = { onVisibleChange(true) },
                onFocusEnter = { onVisibleChange(false) }
            )
        }
    }
}

@Composable
private fun TabPanels(destination: MainDestination, onFocusExit: () -> Unit, onFocusEnter: () -> Unit) {
    when (destination) {
        is MainDestination.Video -> VideoListScreen(subDestination = destination.subDestination, onFocusExit = onFocusExit, onFocusEnter = onFocusEnter)
        is MainDestination.Audio -> AudioListScreen(subDestination = destination.subDestination, onFocusExit = onFocusExit, onFocusEnter = onFocusEnter)
        MainDestination.Browse -> BrowseList(onFocusExit = { onFocusExit() }, onFocusEnter = { onFocusEnter() })
        MainDestination.Playlists -> PlaylistsList(onFocusExit = { onFocusExit() }, onFocusEnter = { onFocusEnter() })
        else -> MoreScreen(onFocusExit = { onFocusExit() }, onFocusEnter = { onFocusEnter() })
    }
}

@Preview(device = "id:tv_1080p")
@Composable
fun MainScreenPreview() {
    VlcPreview {
        MainScreenContent(
            tabs = listOf(
                Pair(R.string.video, R.drawable.ic_video),
                Pair(R.string.audio, R.drawable.ic_menu_audio),
                Pair(R.string.browse, R.drawable.ic_folder),
                Pair(R.string.playlists, R.drawable.ic_playlist),
                Pair(R.string.more, R.drawable.ic_nav_more),
            ),
            videoTabs = listOf(
                R.string.video,
                R.string.playlists,
            ),
            audioTabs = listOf(
                R.string.artists,
                R.string.albums,
                R.string.tracks,
                R.string.genres,
                R.string.playlists,
            )
        )
    }
}