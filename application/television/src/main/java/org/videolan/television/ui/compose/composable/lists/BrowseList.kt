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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.components.ContentLine
import org.videolan.television.viewmodel.BrowserViewModel
import org.videolan.vlc.viewmodels.browser.BrowserFavoritesModel
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.NetworkModel
import org.videolan.vlc.viewmodels.browser.TYPE_STORAGE
import org.videolan.vlc.viewmodels.mobile.VideosViewModel

@Composable
fun BrowseList(onFocusExit: () -> Unit, onFocusEnter: () -> Unit) {
    val context = LocalContext.current
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
    val networks by networkModel.dataset.observeAsState()
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
            .focusGroup()
    ) {
        if (!favorites.isNullOrEmpty())
            ContentLine(favorites, false, R.string.favorites, titleFocusable = false, spannableDescription = true)
        if (!storages.isNullOrEmpty())
            ContentLine(storages, false, R.string.browser_storages, titleFocusable = false, spannableDescription = true)
        if (!networks.isNullOrEmpty())
            ContentLine(networks, false, R.string.network_browsing, titleFocusable = false, spannableDescription = true)
    }
}