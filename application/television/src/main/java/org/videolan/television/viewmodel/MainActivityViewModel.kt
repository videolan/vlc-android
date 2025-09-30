/*
 * ************************************************************************
 *  MainActivityViewModel.kt
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

package org.videolan.television.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider
import org.videolan.resources.HEADER_PERMISSION
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE
import org.videolan.resources.util.getFromMl
import org.videolan.television.R
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.util.Permissions

class MainActivityViewModel(app: Application) : AndroidViewModel(app) {

    val tabs = listOf(
            Pair(R.string.video, R.drawable.ic_video),
            Pair(R.string.audio, R.drawable.ic_menu_audio),
            Pair(R.string.browse, R.drawable.ic_folder),
            Pair(R.string.playlists, R.drawable.ic_playlist),
            Pair(R.string.more, R.drawable.ic_nav_more),
    )

    val audioTabs = listOf(
        R.string.artists,
        R.string.albums,
        R.string.tracks,
        R.string.genres,
        R.string.playlists,
    )
}