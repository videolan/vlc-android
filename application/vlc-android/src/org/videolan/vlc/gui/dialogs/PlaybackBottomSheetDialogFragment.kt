/*
 * ************************************************************************
 *  PlaybackBottomSheetDialogFragment.kt
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

package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.onEach
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.util.launchWhenStarted

abstract class PlaybackBottomSheetDialogFragment:VLCBottomSheetDialogFragment(), PlaybackService.Callback {

    var playbackService: PlaybackService? = null

    /**
     * Triggered when the service is available
     *
     */
    abstract fun onServiceAvailable()

    /**
     * Triggered when the current media changes
     *
     */
    abstract fun onMediaChanged()
    open val dismissOnServiceEnded = true
    open val dismissOnPlaybackEnded = true


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        PlaybackService.serviceFlow.onEach { onServiceChanged(it) }.launchWhenStarted(lifecycleScope)
    }

    private fun onServiceChanged(service: PlaybackService?) {
        if (service != null) {
            playbackService = service
            playbackService!!.addCallback(this)
            onServiceAvailable()
        } else {
            this.playbackService?.apply {
                removeCallback(this@PlaybackBottomSheetDialogFragment)
            }
            playbackService = null
            if (dismissOnServiceEnded) dismiss()
        }
    }

    override fun onDestroy() {
        this.playbackService?.apply {
            removeCallback(this@PlaybackBottomSheetDialogFragment)
        }
        super.onDestroy()
    }

    override fun update() {
        if (playbackService?.playlistManager?.hasCurrentMedia() == true)
            onMediaChanged()
        else if (dismissOnPlaybackEnded)
            dismiss()
    }

    override fun onMediaEvent(event: IMedia.Event) { }

    override fun onMediaPlayerEvent(event: MediaPlayer.Event) { }

}