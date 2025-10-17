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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.videolan.medialibrary.media.MediaLibraryItem

/**
 * Paginated lazy column
 *
 * @param modifier Modifier for the lazy column
 * @param listState LazyListState for the lazy column
 * @param items List of items to display
 * @param loadMoreItems Callback to load more items
 * @param isLoading Boolean indicating if loading is in progress
 * @param contentPadding PaddingValues for the lazy column
 * @param content Composable to render each item
 */
@Composable
fun PaginatedLazyColumn(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    items: PersistentList<MediaLibraryItem>,
    loadMoreItems: () -> Unit,
    isLoading: Boolean,
    contentPadding: PaddingValues,
    content: @Composable (item: MediaLibraryItem, modifier: Modifier) -> Unit
) {
    val shouldLoadMore = remember {
        derivedStateOf {
            val itemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= (itemsCount - 2) && !isLoading
        }
    }

    val focusRequesters = remember {
        HashMap<Long, FocusRequester>()
    }
    var lastFocusedItem by rememberSaveable { mutableLongStateOf(0L) }

    focusRequesters.clear()
    items.forEach {
        focusRequesters[it.id] = FocusRequester()
    }
    if (items.isNotEmpty())
        lastFocusedItem = items.first().id

    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore.value }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                loadMoreItems()
            }
    }
    LazyColumn(
        modifier = modifier
            .focusProperties {
                onEnter = {
                    focusRequesters[lastFocusedItem]?.requestFocus()
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding,
        state = listState
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            content(
                item, Modifier
                    .onFocusChanged {
                        if (it.isFocused)
                            lastFocusedItem = item.id
                    }
                    .focusRequester(focusRequester = focusRequesters[item.id] ?: FocusRequester.Default))
        }
    }
}

/**
 * Paginated lazy grid
 *
 * @param modifier Modifier for the lazy grid
 * @param listState LazyListState for the lazy grid
 * @param items List of items to display
 * @param loadMoreItems Callback to load more items
 * @param isLoading Boolean indicating if loading is in progress
 * @param verticalArrangement Arrangement for the lazy grid
 * @param horizontalArrangement Arrangement for the lazy grid
 * @param contentPadding PaddingValues for the lazy grid
 * @param content Composable to render each item
 */
@Composable
fun PaginatedLazyGrid(
    modifier: Modifier = Modifier,
    listState: LazyGridState,
    items: PersistentList<MediaLibraryItem>,
    loadMoreItems: () -> Unit,
    isLoading: Boolean,
    verticalArrangement: Arrangement.HorizontalOrVertical,
    horizontalArrangement: Arrangement.HorizontalOrVertical,
    contentPadding: PaddingValues,
    content: @Composable (item: MediaLibraryItem, modifier: Modifier) -> Unit
) {
    val shouldLoadMore = remember {
        derivedStateOf {
            val itemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= (itemsCount - 10) && !isLoading
        }
    }

    val focusRequesters = remember {
        HashMap<Long, FocusRequester>()
    }
    var lastFocusedItem by rememberSaveable { mutableLongStateOf(0L) }

    focusRequesters.clear()
    items.forEach {
        focusRequesters[it.id] = FocusRequester()
    }
    if (items.isNotEmpty())
        lastFocusedItem = items.first().id

    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore.value }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                loadMoreItems()
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
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            content(
                item, Modifier
                    .onFocusChanged {
                        if (it.isFocused)
                            lastFocusedItem = item.id
                    }
                    .focusRequester(focusRequester = focusRequesters[item.id] ?: FocusRequester.Default))
        }
    }
}