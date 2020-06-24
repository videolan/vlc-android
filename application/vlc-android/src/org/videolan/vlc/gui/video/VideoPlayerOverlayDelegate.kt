/*
 * ************************************************************************
 *  VideoPlayerOverlayDelegate.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.video

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools.showVideoTrack

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class VideoPlayerOverlayDelegate (private val player: VideoPlayerActivity) {

    fun showTracks() {
        player.showVideoTrack{
            when (it) {
                R.id.audio_track_delay -> player.delayDelegate.showAudioDelaySetting()
                R.id.subtitle_track_delay -> player.delayDelegate.showSubsDelaySetting()
                R.id.subtitle_track_download -> player.downloadSubtitles()
                R.id.subtitle_track_file -> player.pickSubtitles()
            }
        }
    }
}