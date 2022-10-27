package org.videolan.vlc

import android.content.Context
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.vlc.gui.dialogs.adapters.VlcTrack

/*
 * ************************************************************************
 *  Verlc3.kt
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

fun IMedia.getAudioTracks():List<IMedia.AudioTrack> = getTracks(IMedia.Track.Type.Audio)?.map { it as IMedia.AudioTrack }?.toList() ?: emptyList()

fun IMedia.getAllTracks() = tracks.toList()

fun MediaPlayer.getSelectedVideoTrack(): VlcTrack? {
    return getSelectedTrack(IMedia.Track.Type.Video)?.let { VlcTrackImpl(it) }
}

fun MediaPlayer.getSelectedAudioTrack(): VlcTrack? {
    return getSelectedTrack(IMedia.Track.Type.Audio)?.let { VlcTrackImpl(it) }
}

fun MediaPlayer.getSelectedSpuTrack(): VlcTrack? {
    return getSelectedTrack(IMedia.Track.Type.Text)?.let { VlcTrackImpl(it) }
}

fun MediaPlayer.getVideoTracksCount(): Int {
    return getTracks(IMedia.Track.Type.Video)?.size ?: 0
}

fun MediaPlayer.getAudioTracksCount(): Int {
    return getTracks(IMedia.Track.Type.Audio)?.size ?: 0
}

fun MediaPlayer.getSpuTracksCount(): Int {
    return getTracks(IMedia.Track.Type.Text)?.size ?: 0
}

fun MediaPlayer.setVideoTrack(index:String):Boolean {
    return selectTrack(index)
}
fun MediaPlayer.setAudioTrack(index:String):Boolean {
    return selectTrack(index)
}

fun MediaPlayer.setSpuTrack(index:String):Boolean {
    return selectTrack(index)
}

fun MediaPlayer.getAllAudioTracks(): Array<VlcTrack> = getTracks(IMedia.Track.Type.Audio)?.convertToVlcTrack() ?: arrayOf()
fun MediaPlayer.getAllVideoTracks():Array<VlcTrack> = getTracks(IMedia.Track.Type.Video)?.convertToVlcTrack() ?: arrayOf()
fun MediaPlayer.getAllSpuTracks():Array<VlcTrack> = getTracks(IMedia.Track.Type.Text)?.convertToVlcTrack() ?: arrayOf()

fun Array<IMedia.Track>.convertToVlcTrack(): Array<VlcTrack> {
    val newTracks = ArrayList<VlcTrack>()
    this.forEach {
        newTracks.add(VlcTrackImpl(it))
    }
    return newTracks.toTypedArray()
}

/**
 * Generates a fake track to be used as "Disable track" in the [TrackAdapter]
 *
 * @param context the context to use
 * @return a fake track
 */
fun getDisableTrack(context: Context) = object : VlcTrack {
    override fun getName() = context.getString(R.string.disable_track)

    override fun getId() = "-1"

    override fun getWidth() = 0

    override fun getHeight() = 0

    override fun getProjection() = 0

    override fun getFrameRateDen() = 0

    override fun getFrameRateNum() = 0
}
