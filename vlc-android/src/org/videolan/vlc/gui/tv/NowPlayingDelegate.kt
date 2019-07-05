/*
 * *************************************************************************
 *  NowPlayingDelegate.kt
 * **************************************************************************
 *  Copyright © 2019 VLC authors and VideoLAN
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.tv

import androidx.lifecycle.Observer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.EmptyPBSCallback
import org.videolan.vlc.viewmodels.tv.MainTvModel

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class NowPlayingDelegate(private val model: MainTvModel): PlaybackService.Callback by EmptyPBSCallback {
    private var service: PlaybackService? = null

    private val playbackServiceObserver = Observer<PlaybackService> { service ->
        if (service !== null) {
            this.service = service
            service.addCallback(this)
        } else this.service?.let {
            it.removeCallback(this)
            this.service = null
        }
        updateCurrent()
    }

    private val nowPlayingObserver = Observer<Boolean> { updateCurrent() }

    init {
        PlaybackService.service.observeForever(playbackServiceObserver)
        PlaylistManager.showAudioPlayer.observeForever(nowPlayingObserver)
    }

    fun onClear() {
        PlaybackService.service.removeObserver(playbackServiceObserver)
        PlaylistManager.showAudioPlayer.removeObserver(nowPlayingObserver)
    }

    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Playing -> updateCurrent()
        }
    }

    private fun updateCurrent() = model.run {
        updateNowPlaying()
        if (showHistory) launch { updateHistory() }
    }
}
