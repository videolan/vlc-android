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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.television.ui.compose.composable.items.VideoItem
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.vlc.BuildConfig

@Composable
fun VideoListScreen(onFocusExit: () -> Unit, onFocusEnter: () -> Unit, viewModel: MainActivityViewModel = viewModel()) {
    val videos by viewModel.videos.observeAsState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(200.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp),
        modifier = Modifier
            .focusProperties {
                onExit = {
                    when (requestedFocusDirection) {
                        FocusDirection.Left -> cancelFocusChange()
                        FocusDirection.Right -> cancelFocusChange()
                        FocusDirection.Up -> {
                            if (BuildConfig.DEBUG) Log.d("MainScreenLogs", "onFocusExit triggered")
                            onFocusExit()
                        }
                    }
                    if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "onExit")
                }
                onEnter = {
                    onFocusEnter()
                }


            }
    ) {
        items(videos?.size ?: 0) { index ->
            VideoItem(videos!!, index)
        }
    }
}