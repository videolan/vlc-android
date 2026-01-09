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
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.television.R
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.MediaListSidePanel
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelContent
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelListenerKey
import org.videolan.television.ui.compose.composable.components.VlcEmptyViewLoader
import org.videolan.television.ui.compose.composable.items.AudioItemCard
import org.videolan.television.ui.compose.composable.items.AudioItemList
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.SnackbarContent
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.TYPE_FILE

@Composable
fun BrowserList(root: MediaLibraryItem? = null, modifier: Modifier = Modifier, mainActivityViewModel: MainActivityViewModel = viewModel()) {

    val entry = MediaListEntry.BROWSER
    val context = LocalContext.current
    if (BuildConfig.DEBUG) Log.d("BrowserList", "Browsing: ${(root as? Storage)?.uri ?: root}")

    val path = when {
        root is Storage -> root.uri.toString()
        root is MediaWrapper && root.type == MediaWrapper.TYPE_DIR -> root.uri.toString()
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
        extras = browserExtras
    )
    val items by browserModel.dataset.observeAsState()
    items?.forEach {
        if (BuildConfig.DEBUG) Log.d("BrowserLogs", "For ${entry.providerClass} -> found: ${it.title}")
    }

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
        TvUtil.openMedia(activity as FragmentActivity, item)
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
                                    AudioItemCard(
                                        item, Modifier
                                        .onFocusChanged {
                                            if (it.isFocused)
                                                lastFocusedItem = item.id
                                        }, spannableDescription = true, onClick = { onClick(item, index) }, onLongClick = { onLongClick(item, index) })
                                }

                            }

                            if (browserModel.loading.value == true) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
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
                                    AudioItemList(
                                        item, Modifier
                                            .onFocusChanged {
                                                if (it.isFocused)
                                                    lastFocusedItem = item.id
                                            }, spannableDescription = true, onClick = { onClick(item, index) }, onLongClick = { onLongClick(item, index) })

                                }

                            }

                            if (browserModel.loading.value == true) {
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