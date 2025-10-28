/*
 * ************************************************************************
 *  PaginatedLazyColumn.kt
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

package org.videolan.television.ui.compose.composable.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.ui.compose.theme.Orange50

/**
 * Paginated lazy grid
 *
 * @param modifier Modifier for the lazy grid
 * @param listState [LazyGridState] for the lazy grid
 * @param items List of items to display
 * @param verticalArrangement Arrangement for the lazy grid
 * @param horizontalArrangement Arrangement for the lazy grid
 * @param contentPadding PaddingValues for the lazy grid
 * @param loaderAspectRatio Aspect ratio for the loader
 * @param content Composable to render each item
 */
@Composable
fun PaginatedGrid(
    modifier: Modifier = Modifier,
    listState: LazyGridState,
    verticalArrangement: Arrangement.HorizontalOrVertical,
    horizontalArrangement: Arrangement.HorizontalOrVertical,
    contentPadding: PaddingValues,
    loaderAspectRatio: Float = 1f,
    items: LazyPagingItems<out MediaLibraryItem>,
    content: @Composable (item: MediaLibraryItem, modifier: Modifier) -> Unit
) {
    val focusRequesters = remember {
        HashMap<Long, FocusRequester>()
    }
    var lastFocusedItem by rememberSaveable { mutableLongStateOf(0L) }

    focusRequesters.clear()
    for (i in 0..<items.itemCount) {
        items[i]?.let {
            focusRequesters[it.id] = FocusRequester()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = modifier
            .focusProperties {
                onEnter = {
                    focusRequesters[lastFocusedItem]?.requestFocus()
                }
            },
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        contentPadding = contentPadding,
        state = listState
    ) {
        items(count = items.itemCount) { index ->
            items[index]?.let { video ->
                content(
                    video, Modifier
                        .onFocusChanged {
                            if (it.isFocused)
                                lastFocusedItem = video.id
                        }
                        .focusRequester(focusRequester = focusRequesters[video.id] ?: FocusRequester.Default))
            } ?: Box(modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Orange50))

        }

        if (items.loadState.append == LoadState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(loaderAspectRatio)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/**
 * Paginated lazy list
 *
 * @param modifier Modifier for the lazy list
 * @param listState [LazyListState] for the lazy list
 * @param items List of items to display
 * @param verticalArrangement Arrangement for the lazy list
 * @param contentPadding PaddingValues for the lazy list
 * @param loaderAspectRatio Aspect ratio for the loader
 * @param content Composable to render each item
 */
@Composable
fun PaginatedList(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    verticalArrangement: Arrangement.HorizontalOrVertical,
    contentPadding: PaddingValues,
    items: LazyPagingItems<out MediaLibraryItem>,
    content: @Composable (item: MediaLibraryItem, modifier: Modifier) -> Unit
) {
    val focusRequesters = remember {
        HashMap<Long, FocusRequester>()
    }
    var lastFocusedItem by rememberSaveable { mutableLongStateOf(0L) }

    focusRequesters.clear()
    for (i in 0..<items.itemCount) {
        items[i]?.let {
            focusRequesters[it.id] = FocusRequester()
        }
    }

    LazyColumn(
        modifier = modifier
            .focusProperties {
                onEnter = {
                    focusRequesters[lastFocusedItem]?.requestFocus()
                }
            },
        verticalArrangement = verticalArrangement,
        contentPadding = contentPadding,
        state = listState
    ) {
        items(count = items.itemCount) { index ->
            items[index]?.let { video ->
                content(
                    video, Modifier
                        .onFocusChanged {
                            if (it.isFocused)
                                lastFocusedItem = video.id
                        }
                        .focusRequester(focusRequester = focusRequesters[video.id] ?: FocusRequester.Default))
            } ?: Box(modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Orange50))

        }

        if (items.loadState.append == LoadState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}