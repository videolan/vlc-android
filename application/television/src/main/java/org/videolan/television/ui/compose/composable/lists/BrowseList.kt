/*
 * ************************************************************************
 *  BrowseList.kt
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

import android.app.Application
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE
import org.videolan.television.R
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.compose.composable.components.ContentLine
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.SnackbarContent
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.ContextOption.CTX_ADD_TO_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_APPEND
import org.videolan.vlc.util.ContextOption.CTX_FAV_ADD
import org.videolan.vlc.util.ContextOption.CTX_FAV_REMOVE
import org.videolan.vlc.util.ContextOption.CTX_INFORMATION
import org.videolan.vlc.util.ContextOption.CTX_PLAY
import org.videolan.vlc.util.ContextOption.CTX_PLAY_NEXT
import org.videolan.vlc.util.ContextOption.CTX_PLAY_SHUFFLE
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.viewmodels.browser.BrowserFavoritesModel
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.NetworkModel
import org.videolan.vlc.viewmodels.browser.TYPE_STORAGE
import java.security.SecureRandom
import kotlin.math.min

@Composable
fun BrowseList(onFocusExit: () -> Unit, onFocusEnter: () -> Unit, mainActivityViewModel: MainActivityViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val coroutineScope = rememberCoroutineScope()
    val extras = MutableCreationExtras().apply {
        set(APPLICATION_KEY, context.applicationContext as Application)
    }
    val favoritesModel: BrowserFavoritesModel = viewModel(
        factory = BrowserFavoritesModel.Factory,
        extras = extras
    )

    val browserExtras = MutableCreationExtras().apply {
        set(APPLICATION_KEY, context.applicationContext as Application)
        set(BrowserModel.TYPE_KEY, TYPE_STORAGE)
        set(BrowserModel.URL_KEY, null)
        set(BrowserModel.SHOW_DUMMY_KEY, false)
    }
    val browserModel: BrowserModel = viewModel(
        factory = BrowserModel.Factory,
        extras = browserExtras
    )

    val networkExtras = MutableCreationExtras().apply {
        set(APPLICATION_KEY, context.applicationContext as Application)
        set(NetworkModel.URL_KEY, null)
        set(NetworkModel.MOCKED_KEY, null)
    }
    val networkModel: NetworkModel = viewModel(
        factory = NetworkModel.Factory,
        extras = networkExtras
    )

    val favorites by favoritesModel.favorites.observeAsState()
    val storages by browserModel.dataset.observeAsState()
    networkModel.dataset.convertToFlow()
    val networks = networkModel.dataset.datasetFlow.collectAsState()
    networks.value.forEach {
        if (BuildConfig.DEBUG) Log.d("NetworkDebug", "Network found: ${it.title} ${(it as MediaWrapper).uri}")
    }

    mainActivityViewModel.addCtxClickListener(MediaListEntry.BROWSER) { item, position, ctxMenuItem ->
        val showSnackbar: (String) -> Unit = {
            mainActivityViewModel.showSnackbar(SnackbarContent(it))
        }
        when(ctxMenuItem.id) {
            CTX_PLAY -> MediaUtils.openMedia(activity, (item as MediaWrapper))
            CTX_PLAY_SHUFFLE -> MediaUtils.playTracks(activity!!, item, SecureRandom().nextInt(min(item.tracksCount, MEDIALIBRARY_PAGE_SIZE)), true)
            CTX_APPEND -> MediaUtils.appendMedia(activity!!, item.tracks, showSnackbar)
            CTX_PLAY_NEXT -> MediaUtils.insertNext(activity, item.tracks, showSnackbar)
            CTX_INFORMATION -> mainActivityViewModel.showSnackbar(SnackbarContent(activity!!.resources.getString(R.string.not_implemented)))
            CTX_ADD_TO_PLAYLIST -> (activity as FragmentActivity).addToPlaylist(item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            CTX_FAV_ADD, CTX_FAV_REMOVE -> coroutineScope.launch { withContext(Dispatchers.IO) { item.isFavorite = ctxMenuItem.id == CTX_FAV_ADD } }
            else -> {}
        }
    }


    Column(
        modifier = Modifier
            .focusProperties {
                onExit = {
                    onFocusExit()
                }
                onEnter = {
                    onFocusEnter()
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp)
            .focusGroup()
    ) {
        val activity = LocalActivity.current
        val onClick: (MediaLibraryItem, Int) -> Unit = { item, position ->
            TvUtil.openMedia(activity as FragmentActivity, item)
        }
        val onLongClick: (MediaLibraryItem, Int) -> Unit = { item, position ->
            mainActivityViewModel.showSnackbar(SnackbarContent(activity!!.resources.getString(R.string.not_implemented)))
        }

        val favoritesModelDescriptionUpdates = favoritesModel.provider.descriptionUpdate.observeAsState()
        if (!favorites.isNullOrEmpty())
            InvalidationComposable(favoritesModelDescriptionUpdates.value) {
                ContentLine(
                    favorites,
                    MediaListEntry.BROWSER.apply { isRoot = true },
                    false,
                    R.string.favorites,
                    titleFocusable = false,
                    spannableDescription = true,
                    onItemClick = { onClick(favorites!![it], it) },
                    onItemLongClick = { onLongClick(favorites!![it], it) })
            }

        val storagesModelDescriptionUpdates = browserModel.provider.descriptionUpdate.observeAsState()

        if (!storages.isNullOrEmpty())
            InvalidationComposable(storagesModelDescriptionUpdates.value) {
                ContentLine(
                    storages,
                    MediaListEntry.BROWSER.apply { isRoot = true },
                    false,
                    R.string.browser_storages,
                    titleFocusable = false,
                    spannableDescription = true,
                    onItemClick = { onClick(storages!![it], it) },
                    onItemLongClick = { onLongClick(storages!![it], it) })
            }
        InvalidationComposable(networks.value) {
            if (!networks.value.isEmpty())
                ContentLine(
                    networks.value,
                    MediaListEntry.BROWSER.apply { isRoot = true },
                    false,
                    R.string.network_browsing,
                    titleFocusable = false,
                    browserRoot = true,
                    spannableDescription = true,
                    onItemClick = { index -> onClick(networks.value[index], index) },
                    onItemLongClick = { index -> onLongClick(networks.value[index], index) })
        }
    }
}