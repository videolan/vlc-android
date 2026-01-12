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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaWrapperImpl
import org.videolan.television.R
import org.videolan.television.ui.BrowserActivity
import org.videolan.television.ui.compose.composable.components.AudioPlayer
import org.videolan.television.ui.compose.composable.components.DisplaySettings
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.LabeledIconButton
import org.videolan.television.ui.compose.composable.lists.BrowserList
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.viewmodel.FileBrowserViewModel
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.vlc.BuildConfig
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
            BrowserScreenContent(Modifier.padding(contentPadding))
            DisplaySettings()
        }
    }

}

@Composable
fun BrowserScreenContent(modifier: Modifier, viewModel: FileBrowserViewModel = viewModel()) {
    val currentItem = viewModel.currentPathEntry.collectAsState()
    InvalidationComposable(currentItem.value) {
        Column(
            modifier
                .fillMaxHeight()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val activity = LocalActivity.current as BrowserActivity
                LabeledIconButton(
                    label = stringResource(R.string.close),
                    vectorImage = Icons.AutoMirrored.Outlined.ArrowBack,
                    modifier = Modifier
                ) {
                    activity.finish()
                }
                val segments = viewModel.prepareSegments()
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
                                PathOperationDelegate.storages.containsKey(key) -> activity.retrieveSafePath(
                                    PathOperationDelegate.storages.valueAt(
                                        PathOperationDelegate.storages.indexOfKey(
                                            key
                                        )
                                    )
                                )

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
                                    val segment = segments[index]
                                    if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Segment is $segment")
                                    viewModel.setCurrentPathEntry(MediaWrapperImpl(segment.toUri()).apply { type = MediaWrapper.TYPE_DIR })
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
                BrowserList(
                    modifier = Modifier.padding(
                        top = 16.dp,
                        start = 24.dp,
                        end = 24.dp
                    )
                )
            }
        }
    }
}