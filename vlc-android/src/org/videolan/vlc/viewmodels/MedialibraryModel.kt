/*****************************************************************************
 * MedialibraryModel.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 *
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
 *****************************************************************************/

package org.videolan.vlc.viewmodels

import android.content.Context
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem


abstract class MedialibraryModel<T : MediaLibraryItem>(context: Context) : BaseModel<T>(context), Medialibrary.OnMedialibraryReadyListener, Medialibrary.OnDeviceChangeListener {

    val medialibrary = Medialibrary.getInstance()

    init {
        medialibrary.addOnMedialibraryReadyListener(this)
        medialibrary.addOnDeviceChangeListener(this)
    }

    override fun fetch() {
        if (medialibrary.isStarted) onMedialibraryReady()
    }

    override fun onMedialibraryReady() {
        launch { refresh() }
    }

    override fun onMedialibraryIdle() {
        launch { refresh() }
    }

    override fun onDeviceChange() {
        launch {
            refresh()
        }
    }

    override fun onCleared() {
        medialibrary.removeOnMedialibraryReadyListener(this)
        medialibrary.removeOnDeviceChangeListener(this)
        super.onCleared()
    }
}