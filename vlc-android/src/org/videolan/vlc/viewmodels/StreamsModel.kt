/*******************************************************************************
 *  MRLPanelModel.kt
 * ****************************************************************************
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
 ******************************************************************************/

package org.videolan.vlc.viewmodels

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.databinding.ObservableField
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.VLCIO


class StreamsModel: ViewModel() {
     val observableSearchText = ObservableField<String>()
     val observableHistory = MutableLiveData<Array<MediaWrapper>>()

    fun updateHistory() = launch(UI.immediate) {
        val history = withContext(VLCIO) { VLCApplication.getMLInstance().lastStreamsPlayed() }
        observableHistory.value = history
    }
}