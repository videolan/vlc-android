/*
 * ************************************************************************
 *  SearchScreen.kt
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.SearchAggregate
import org.videolan.medialibrary.stubs.StubAlbum
import org.videolan.medialibrary.stubs.StubArtist
import org.videolan.medialibrary.stubs.StubGenre
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.components.VlcEmptyViewLoader
import org.videolan.television.ui.compose.composable.items.AudioItem
import org.videolan.television.ui.compose.composable.items.VideoItem
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.television.viewmodel.SearchViewModel
import org.videolan.tools.firstLetterUppercase
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.util.MediaListEntry

@Composable
fun SearchScreen(viewModel: SearchViewModel, onVoiceSearchClick: () -> Unit) {
    val query by viewModel.query.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()

    SearchScreen(
        query = query,
        searchResult = searchResult,
        onQueryChange = { viewModel.setQuery(it) },
        onVoiceSearchClick = onVoiceSearchClick
    )
}

@Composable
fun SearchScreen(
    query: String,
    searchResult: SearchAggregate?,
    onQueryChange: (String) -> Unit,
    onVoiceSearchClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    VlcTVTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = VlcTVTheme.dimens.overscanVertical)
            ) {
                // Search Input Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VlcTVTheme.dimens.overscanHorizontal)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        label = { Text(stringResource(id = R.string.search)) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = onVoiceSearchClick) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val loadingState = when {
                    query.length < 3 -> EmptyLoadingState.NONE
                    searchResult == null -> EmptyLoadingState.LOADING
                    searchResult.isEmpty -> EmptyLoadingState.EMPTY_SEARCH
                    else -> EmptyLoadingState.NONE
                }

                VlcEmptyViewLoader(state = loadingState) {
                    searchResult?.let { results ->
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = VlcTVTheme.dimens.overscanVertical),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            results.videos?.let { videos ->
                                if (videos.isNotEmpty()) {
                                    item {
                                        SearchCategory(
                                            title = stringResource(id = R.string.videos).firstLetterUppercase(),
                                            items = videos.toList(),
                                            entry = MediaListEntry.VIDEO
                                        )
                                    }
                                }
                            }
                            results.tracks?.let { tracks ->
                                if (tracks.isNotEmpty()) {
                                    item {
                                        SearchCategory(
                                            title = stringResource(id = R.string.songs).firstLetterUppercase(),
                                            items = tracks.toList(),
                                            entry = MediaListEntry.TRACKS
                                        )
                                    }
                                }
                            }
                            results.artists?.let { artists ->
                                if (artists.isNotEmpty()) {
                                    item {
                                        SearchCategory(
                                            title = stringResource(id = R.string.artists).firstLetterUppercase(),
                                            items = artists.toList(),
                                            entry = MediaListEntry.ARTISTS
                                        )
                                    }
                                }
                            }
                            results.albums?.let { albums ->
                                if (albums.isNotEmpty()) {
                                    item {
                                        SearchCategory(
                                            title = stringResource(id = R.string.albums).firstLetterUppercase(),
                                            items = albums.toList(),
                                            entry = MediaListEntry.ALBUMS
                                        )
                                    }
                                }
                            }
                            results.genres?.let { genres ->
                                if (genres.isNotEmpty()) {
                                    item {
                                        SearchCategory(
                                            title = stringResource(id = R.string.genres).firstLetterUppercase(),
                                            items = genres.toList(),
                                            entry = MediaListEntry.GENRES
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun SearchCategory(
    title: String,
    items: List<MediaLibraryItem>,
    entry: MediaListEntry
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = VlcTVTheme.dimens.overscanHorizontal, bottom = 8.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = VlcTVTheme.dimens.overscanHorizontal,
                vertical = VlcTVTheme.dimens.itemFocusGlowRadius
            ),
            horizontalArrangement = Arrangement.spacedBy(VlcTVTheme.dimens.itemFocusGlowRadius)
        ) {
            itemsIndexed(items) { index, item ->
                when (entry) {
                    MediaListEntry.VIDEO -> VideoItem(
                        video = item,
                        entry = entry,
                        position = index,
                        onClick = { /* TODO */ }
                    )

                    else -> AudioItem(
                        audios = items,
                        entry = entry,
                        index = index,
                        onClick = { /* TODO */ }
                    )
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SearchScreenEmptyPreview() {
    VlcPreview {
        SearchScreen(
            query = "",
            searchResult = null,
            onQueryChange = {},
            onVoiceSearchClick = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SearchScreenResultsPreview() {
    val videos = arrayOf(
        StubMediaWrapper(
            1,
            "file://video1.mp4",
            0,
            0f,
            1000,
            MediaWrapper.TYPE_VIDEO,
            "Video 1",
            "video1.mp4",
            0,
            0,
            "",
            "",
            0,
            "",
            "",
            1920,
            1080,
            "",
            0,
            0,
            0,
            0,
            0,
            0,
            false,
            false,
            0,
            true,
            0
        )
    )
    val tracks = arrayOf(
        StubMediaWrapper(
            2,
            "file://track1.mp3",
            0,
            0f,
            200,
            MediaWrapper.TYPE_AUDIO,
            "Track 1",
            "track1.mp3",
            1,
            1,
            "Artist 1",
            "Genre 1",
            1,
            "Album 1",
            "Artist 1",
            0,
            0,
            "",
            0,
            0,
            1,
            1,
            0,
            0,
            false,
            false,
            0,
            true,
            0
        )
    )
    val albums = arrayOf(
        StubAlbum(1, "Album 1", 2024, "", "Artist 1", 1, 10, 10, 1000, false)
    )
    val artists = arrayOf(
        StubArtist(1, "Artist 1", "Bio", "", "", 10, 5, 5, false)
    )
    val genres = arrayOf(
        StubGenre(1, "Genre 1", 20, 20, false)
    )

    val results = SearchAggregate(albums, artists, genres, videos, tracks, null)

    VlcPreview {
        SearchScreen(
            query = "test",
            searchResult = results,
            onQueryChange = {},
            onVoiceSearchClick = {}
        )
    }
}
