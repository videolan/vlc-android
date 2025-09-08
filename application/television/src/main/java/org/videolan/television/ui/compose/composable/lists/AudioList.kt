/*
 * ************************************************************************
 *  AudioList.kt
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import org.videolan.television.ui.compose.composable.AudioItem
import org.videolan.television.ui.compose.theme.Orange800
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.viewmodel.AudioListViewModel
import org.videolan.tools.Settings


@Composable
fun AudioListScreen(viewModel: AudioListViewModel = viewModel()) {
    val context = LocalContext.current
    val settings = Settings.getInstance(context)
    var selectedTabIndex by remember { mutableIntStateOf(settings.getInt("audio_tab", 0)) }
    val selectedItemFocusRestorer = remember { FocusRequester.Default }
    Column {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier
                .focusRestorer(selectedItemFocusRestorer)
                .align(Alignment.CenterHorizontally)
        ) {
            viewModel.audioTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selectedTabIndex,
                    onFocus = {
                        selectedTabIndex = index
                        settings.edit { putInt("audio_tab", index) }
                    },
                    modifier = Modifier.focusRestorer(if (index == selectedTabIndex) selectedItemFocusRestorer else FocusRequester.Default)
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = stringResource(tab),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedTabIndex == index) Orange800 else White.copy(0.4F),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 0.dp)
                                .align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
        when (selectedTabIndex) {
            0 -> ArtistsList()
            1 -> AlbumsList()
            2 -> TracksList()
            3 -> GenresList()
            4 -> AudioPlaylistsList()
        }
    }
}

@Composable
fun ArtistsList(viewModel: AudioListViewModel = viewModel()) {
    viewModel.updateAudioArtists()
    val audios by viewModel.audioArtists.observeAsState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(audios?.size ?: 0) { index ->
            AudioItem(audios!!, index, vlcBorder())

        }
    }
}

@Composable
fun AlbumsList(viewModel: AudioListViewModel = viewModel()) {
    viewModel.updateAudioAlbums()
    val audios by viewModel.audioAlbums.observeAsState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(audios?.size ?: 0) { index ->
            AudioItem(audios!!, index, vlcBorder())

        }
    }
}

@Composable
fun TracksList(viewModel: AudioListViewModel = viewModel()) {
    viewModel.updateAudioTracks()
    val audios by viewModel.audioTracks.observeAsState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(audios?.size ?: 0) { index ->
            AudioItem(audios!!, index, vlcBorder())

        }
    }
}

@Composable
fun AudioPlaylistsList(viewModel: AudioListViewModel = viewModel()) {
    viewModel.updateAudioPlaylists()
    val audios by viewModel.audioPlaylists.observeAsState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(audios?.size ?: 0) { index ->
            AudioItem(audios!!, index, vlcBorder())

        }
    }
}

@Composable
fun GenresList(viewModel: AudioListViewModel = viewModel()) {
    viewModel.updateAudioGenres()
    val audios by viewModel.audioGenres.observeAsState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(audios?.size ?: 0) { index ->
            AudioItem(audios!!, index, vlcBorder())

        }
    }
}

@Composable
fun vlcBorder() = Border(
    border = BorderStroke(3.dp, MaterialTheme.colorScheme.border),
)