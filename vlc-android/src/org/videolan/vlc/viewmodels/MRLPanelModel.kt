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
import android.databinding.BaseObservable
import android.databinding.ObservableArrayList
import android.databinding.ObservableField
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.VLCIO


class MRLPanelModel: ViewModel() {
     val observableSearchText = ObservableField<String>()
     fun afterSearchTextChanged(s: Editable?) {
         if (s.toString() != observableSearchText.get())
              observableSearchText.set(s.toString())
     }

     val observableHistory = MutableLiveData<Array<MediaWrapper>>()

     fun updateHistory() {
          launch(VLCIO) {
               val history = VLCApplication.getMLInstance().lastStreamsPlayed()
               launch(UI) {
                   observableHistory.value = history
               }
          }
     }

}