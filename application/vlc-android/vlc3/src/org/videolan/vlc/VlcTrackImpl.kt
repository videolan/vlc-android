package org.videolan.vlc
/*
 * ************************************************************************
 *  VlcTrack.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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
import org.videolan.libvlc.MediaPlayer
import  org.videolan.vlc.gui.dialogs.adapters.VlcTrack
import  org.videolan.libvlc.interfaces.IMedia

class VlcTrackImpl : VlcTrack {
    private var mediaTrack: IMedia.Track? = null
    private var mediaplayerTrack: MediaPlayer.TrackDescription? = null

    constructor(track: MediaPlayer.TrackDescription) {
        this.mediaplayerTrack = track
    }

    constructor(track: IMedia.Track) {
        this.mediaTrack = track
    }

    override fun getId() = mediaplayerTrack?.id?.toString() ?: mediaTrack!!.id.toString()

    override fun getName() = mediaplayerTrack?.name ?: mediaTrack!!.description
    override fun getWidth() = (mediaplayerTrack as? IMedia.VideoTrack)?.width ?: 0
    override fun getHeight() = (mediaplayerTrack as? IMedia.VideoTrack)?.height ?: 0
    override fun getProjection() = (mediaplayerTrack as? IMedia.VideoTrack)?.projection ?: 0 //todo good default value?
    override fun getFrameRateDen() = (mediaplayerTrack as? IMedia.VideoTrack)?.frameRateDen ?: 0 //todo good default value?
    override fun getFrameRateNum() = (mediaplayerTrack as? IMedia.VideoTrack)?.frameRateNum ?: 0 //todo good default value?

}