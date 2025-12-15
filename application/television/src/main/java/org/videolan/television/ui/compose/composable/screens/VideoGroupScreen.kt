/*
 * ************************************************************************
 *  VideoGroupScreen.kt
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

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.components.AudioPlayer
import org.videolan.television.ui.compose.composable.components.DisplaySettings
import org.videolan.television.ui.compose.composable.components.LabeledIconButton
import org.videolan.television.ui.compose.composable.lists.VideoList
import org.videolan.television.viewmodel.MainActivityViewModel

@Composable
fun VideoGroupScreen(folder: Folder? = null, group : VideoGroup? = null, viewModel: MainActivityViewModel = viewModel()) {
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
                VideoGroupScreenContent(Modifier.padding(contentPadding), folder, group)
                DisplaySettings(inGrouping = true)
            }
        }
}

@Composable
fun VideoGroupScreenContent(modifier: Modifier, folder: Folder? = null, group : VideoGroup? = null) {
    Column(modifier
        .fillMaxHeight()
        .padding(
            top = 16.dp,
            start = 24.dp,
            end = 24.dp
        )) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val activity = LocalActivity.current
            LabeledIconButton(
                label = stringResource(R.string.close),
                vectorImage = Icons.AutoMirrored.Outlined.ArrowBack,
                modifier =  Modifier
//                    .focusRequester(focusRequester = focusRequester),
            ) {
                activity?.finish()
            }
            Text(text = if (folder != null) stringResource(R.string.talkback_folder, folder.title) else stringResource(R.string.talkback_video_group, group!!.title))
        }
        Row(modifier) {
            if (folder != null)
                VideoList(modifier, folder= folder)
            else
                VideoList(modifier, group = group)
            AudioPlayer()
            Tabs(modifier = Modifier.weight(1f))
        }
    }
}
