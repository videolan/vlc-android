/*
 * ************************************************************************
 *  BrowserList.kt
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

package org.videolan.television.ui.compose.composable.lists

import android.app.Application
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.MediaListSidePanel
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelContent
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelListenerKey
import org.videolan.television.ui.compose.composable.components.VlcEmptyViewLoader
import org.videolan.television.ui.compose.composable.items.AudioItemCard
import org.videolan.television.ui.compose.composable.items.AudioItemList
import org.videolan.television.viewmodel.FileBrowserViewModel
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.SnackbarContent
import org.videolan.tools.Settings
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.TYPE_FILE

@Composable
fun BrowserList(modifier: Modifier = Modifier, mainActivityViewModel: MainActivityViewModel = viewModel(), fileBrowserViewModel: FileBrowserViewModel = viewModel()) {

    val context = LocalContext.current
    val root = fileBrowserViewModel.currentPathEntry.collectAsState()
    InvalidationComposable(root.value) {
        val entry = MediaListEntry.BROWSER
        val path = when (root.value) {
            is Storage -> (root.value as Storage).uri.toString()
            is MediaWrapper if (root.value as MediaWrapper).type == MediaWrapper.TYPE_DIR -> (root.value as MediaWrapper).uri.toString()
            else -> throw IllegalStateException("Invalid browsing item")
        }
        val browserExtras = MutableCreationExtras().apply {
            set(APPLICATION_KEY, context.applicationContext as Application)
            set(BrowserModel.TYPE_KEY, TYPE_FILE)
            set(BrowserModel.URL_KEY, path)
            set(BrowserModel.SHOW_DUMMY_KEY, false)
        }
        val browserModel: BrowserModel = viewModel(
            factory = BrowserModel.Factory,
            extras = browserExtras,
            key = path
        )
        val items by browserModel.dataset.observeAsState()
        val descriptionUpdates = browserModel.provider.descriptionUpdate.observeAsState()

        val emptyState =
            if (items?.isEmpty() == true && !Permissions.canReadStorage(context))
                EmptyLoadingState.MISSING_PERMISSION
            else if (items?.isEmpty() == true && !Permissions.canReadAudios(context))
                EmptyLoadingState.MISSING_AUDIO_PERMISSION
            else if (items?.isEmpty() == true)
                EmptyLoadingState.EMPTY
            else
                EmptyLoadingState.NONE
        val activity = LocalActivity.current
        val onClick: (MediaLibraryItem, Int) -> Unit = { item, position ->
            fileBrowserViewModel.setCurrentPathEntry(item)
        }
        val onLongClick: (MediaLibraryItem, Int) -> Unit = { item, position ->
            mainActivityViewModel.showSnackbar(SnackbarContent(activity!!.resources.getString(R.string.not_implemented)))
        }
        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()
        var lastFocusedItem by rememberSaveable { mutableLongStateOf(0L) }
        val focusRequesters = remember {
            HashMap<Long, FocusRequester>()
        }

        entry.sorts = arrayListOf(Medialibrary.SORT_ALPHA, Medialibrary.SORT_FILENAME)
        entry.currentSort = browserModel.provider.sort
        entry.currentSortDesc = browserModel.provider.desc

        val displaySettingsChange by mainActivityViewModel.currentDisplaySettingsChange.collectAsState()
        InvalidationComposable(displaySettingsChange) { invalidate ->
            var inCard by remember { mutableStateOf(entry.displayInCard(context)) }
            if (!items.isNullOrEmpty())
                VlcEmptyViewLoader(emptyState) {
                    Row(modifier = modifier.fillMaxHeight()) {
                        if (inCard) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(150.dp),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f)
                                    .focusProperties {
                                        onEnter = {
                                            focusRequesters[lastFocusedItem]?.requestFocus()
                                        }
                                    },
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                contentPadding = PaddingValues(top = 16.dp),
                                state = gridState
                            ) {
                                items(count = items?.size ?: 0) { index ->
                                    items!![index].let { item ->
                                        InvalidationComposable(descriptionUpdates.value?.first == index) {
                                            AudioItemCard(
                                                item, Modifier
                                                    .onFocusChanged {
                                                        if (it.isFocused)
                                                            lastFocusedItem = item.id
                                                    }, spannableDescription = true, onClick = { onClick(item, index) }, onLongClick = { onLongClick(item, index) })
                                        }
                                    }

                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f)
                                    .focusProperties {
                                        onEnter = {
                                            focusRequesters[lastFocusedItem]?.requestFocus()
                                        }
                                    },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 16.dp),
                                state = listState
                            ) {
                                items(count = items?.size ?: 0) { index ->
                                    items!![index].let { item ->
                                        InvalidationComposable(descriptionUpdates.value?.first == index) {
                                            AudioItemList(
                                                item, Modifier
                                                    .onFocusChanged {
                                                        if (it.isFocused)
                                                            lastFocusedItem = item.id
                                                    }, spannableDescription = true, onClick = { onClick(item, index) }, onLongClick = { onLongClick(item, index) })
                                        }
                                    }

                                }
                            }
                        }
                        MediaListSidePanel(
                            MediaListSidePanelContent(
                                showScrollToTop = true,
                                showResumePlayback = false,
                                if (inCard) gridState else listState,
                                entry
                            ), inGrouping = false
                        ) { first, second ->
                            when (first) {
                                MediaListSidePanelListenerKey.DISPLAY_MODE -> {
                                    inCard = second as Boolean
                                    Settings.getInstance(context).edit { putBoolean(entry.inCardsKey, inCard) }
                                    invalidate()
                                }

                                MediaListSidePanelListenerKey.RESUME_PLAYBACK -> {
                                    throw IllegalStateException("Cannot resume playback for file browser")
                                }
                            }
                        }
                    }
                }
        }
    }
}