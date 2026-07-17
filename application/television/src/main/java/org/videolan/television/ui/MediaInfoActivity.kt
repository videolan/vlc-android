/*
 * ************************************************************************
 *  MediaInfoActivity.kt
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

package org.videolan.television.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.videolan.television.ui.compose.composable.screens.MediaInfoScreen
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.viewmodel.MediaInfoUiState
import org.videolan.television.viewmodel.MediaInfoViewModel

@AndroidEntryPoint
class MediaInfoActivity : DefaultTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        val itemType = intent.getIntExtra(EXTRA_ITEM_TYPE, -1)

        if (itemId == -1L || itemType == -1) {
            finish()
            return
        }

        setContent {
            VlcTVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MediaInfoRoot(itemId, itemType)
                }
            }
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_ITEM_TYPE = "item_type"

        fun start(context: Context, itemId: Long, itemType: Int) {
            val intent = Intent(context, MediaInfoActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_ITEM_TYPE, itemType)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun MediaInfoRoot(itemId: Long, itemType: Int) {
    val viewModel: MediaInfoViewModel = hiltViewModel()
    LaunchedEffect(itemId, itemType) {
        viewModel.setup(itemId, itemType)
    }
    val uiState by viewModel.uiState.collectAsState()
    when (val state = uiState) {
        MediaInfoUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        MediaInfoUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(org.videolan.vlc.R.string.unknown_error)) }
        is MediaInfoUiState.Success -> {
            val activity = LocalActivity.current
            MediaInfoScreen(
                item = state.item,
                fileSize = state.fileSize,
                tracks = state.tracks,
                onPlay = {
                    org.videolan.vlc.media.MediaUtils.playTracks(activity!!, state.item, 0)
                }
            )
        }
    }
}
