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

import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.interfaces.MediaAddedCb
import org.videolan.medialibrary.interfaces.MediaUpdatedCb
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.util.uiStart


abstract class MedialibraryModel<T : MediaLibraryItem> : BaseModel<T>(), Medialibrary.OnMedialibraryReadyListener, MediaUpdatedCb by EmptyMLCallbacks, MediaAddedCb by EmptyMLCallbacks {

    val medialibrary = Medialibrary.getInstance()

    override fun fetch() {
        medialibrary.addOnMedialibraryReadyListener(this)
        if (medialibrary.isStarted) onMedialibraryReady()
    }

    override fun onMedialibraryReady() {
        launch(UI, uiStart()) { refresh() }
    }

    override fun onMedialibraryIdle() {
        launch(UI, uiStart()) { refresh() }
    }

    override fun onCleared() {
        super.onCleared()
        medialibrary.removeOnMedialibraryReadyListener(this)
    }
}