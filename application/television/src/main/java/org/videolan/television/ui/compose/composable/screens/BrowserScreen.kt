/*
 * ************************************************************************
 *  BrowserScreen.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapperImpl
import org.videolan.television.R
import org.videolan.television.ui.BrowserActivity
import org.videolan.television.ui.compose.composable.components.AudioPlayer
import org.videolan.television.ui.compose.composable.components.DisplaySettings
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.LabeledIconButton
import org.videolan.television.ui.compose.composable.lists.BrowserList
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.viewmodel.FileBrowserViewModel
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.util.MediaListEntry
import org.videolan.television.ui.compose.composable.lists.BrowserListContent
import org.videolan.vlc.viewmodels.browser.PathOperationDelegate

@Composable
fun BrowserScreen(viewModel: MainActivityViewModel = viewModel()) {
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarContent by viewModel.snackBarFlow.collectAsState()
    LaunchedEffect(snackbarContent) {
        snackbarContent?.let { snackbarContent ->
            scope.launch {
                snackbarHostState.showSnackbar(snackbarContent.message, duration = snackbarContent.duration)
                viewModel.showSnackbar(null)
            }
        }
    }
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { contentPadding ->
        Box {
            BrowserScreenContent(Modifier.padding(contentPadding), mainActivityViewModel = viewModel)
            DisplaySettings()
        }
    }

}

@Composable
fun BrowserScreenContent(modifier: Modifier, viewModel: FileBrowserViewModel = viewModel(), mainActivityViewModel: MainActivityViewModel = viewModel()) {
    val currentItem by viewModel.currentPathEntry.collectAsState()
    val segments = viewModel.prepareSegments()
    val activity = LocalActivity.current as? BrowserActivity
    BrowserScreenContent(
        modifier = modifier,
        currentItem = currentItem,
        segments = segments,
        onClose = { activity?.finish() },
        onSegmentClick = { segment ->
            if (BuildConfig.DEBUG) Log.d("BrowserScreen", "Segment is $segment")
            viewModel.setCurrentPathEntry(MediaWrapperImpl(segment.toUri()).apply { type = MediaWrapper.TYPE_DIR })
        },
        retrieveSafePath = { key ->
            activity?.let {
                if (PathOperationDelegate.storages.containsKey(key)) {
                    it.retrieveSafePath(
                        PathOperationDelegate.storages.valueAt(
                            PathOperationDelegate.storages.indexOfKey(
                                key
                            )
                        )
                    )
                } else key
            } ?: key
        },
        browserList = {
            BrowserList(
                modifier = Modifier.padding(
                    top = 16.dp,
                    start = 24.dp,
                    end = 24.dp
                ),
                mainActivityViewModel = mainActivityViewModel,
                fileBrowserViewModel = viewModel
            )
        }
    )
}

@Composable
private fun BrowserScreenContent(
    modifier: Modifier = Modifier,
    currentItem: MediaLibraryItem?,
    segments: List<String>,
    onClose: () -> Unit,
    onSegmentClick: (String) -> Unit,
    retrieveSafePath: (String) -> String,
    browserList: @Composable () -> Unit
) {
    InvalidationComposable(currentItem) {
        Column(
            modifier
                .fillMaxHeight()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledIconButton(
                    label = stringResource(R.string.close),
                    vectorImage = Icons.Default.Close,
                    modifier = Modifier
                ) {
                    onClose()
                }
                LazyRow {
                    items(segments.size) { index ->
                        Row(
                            Modifier
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (index > 0) Image(imageVector = Icons.Default.ChevronRight, contentDescription = null, colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))

                            val key = segments[index].toUri().path!!
                            val text: String? = when {
                                //substitute a storage path to its name. See [replaceStoragePath]
                                PathOperationDelegate.storages.containsKey(key) -> retrieveSafePath(key)

                                else -> segments[index].toUri().lastPathSegment
                            }
                            var focused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged {
                                        focused = it.isFocused
                                    }
                                    .padding(vertical = 8.dp)
                                    .fillMaxHeight()
                                    .align(Alignment.CenterVertically)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (focused) WhiteTransparent10 else Transparent)
                                    .padding(horizontal = 8.dp)
                                    .focusable(true)
                                    .clickable(onClick = {
                                        onSegmentClick(segments[index])
                                    }),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text ?: "root",
                                    textAlign = TextAlign.Center,

                                    )
                            }
                        }
                    }
                }
            }
            Row(modifier) {
                AudioPlayer()
                browserList()
            }
        }
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun BrowserScreenPreview() {
    VlcTVTheme {
        BrowserScreenContent(
            currentItem = null,
            segments = listOf("file:///storage/emulated/0", "file:///storage/emulated/0/Movies"),
            onClose = {},
            onSegmentClick = {},
            retrieveSafePath = { it },
            browserList = {
                BrowserListContent(
                    modifier = Modifier.padding(
                        top = 16.dp,
                        start = 24.dp,
                        end = 24.dp
                    ),
                    items = listOf(
                        MediaWrapperImpl("file:///sdcard/Download/video.mp4".toUri()).apply {
                            type = MediaWrapper.TYPE_VIDEO
                            title = "Video"
                        },
                        MediaWrapperImpl("file:///sdcard/Download/music.mp3".toUri()).apply {
                            type = MediaWrapper.TYPE_AUDIO
                            title = "Music"
                        }
                    ),
                    emptyState = EmptyLoadingState.NONE,
                    inCard = true,
                    isFavorite = false,
                    entry = MediaListEntry.BROWSER,
                    descriptionUpdates = null,
                    onItemRendered = {},
                    onClick = { _, _ -> },
                    onSidePanelAction = { _, _ -> }
                )
            }
        )
    }
}
