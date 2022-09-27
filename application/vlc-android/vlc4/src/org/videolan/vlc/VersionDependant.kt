package org.videolan.vlc

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

fun MediaPlayer.getSelectedVideoTrack(): VlcTrack {
    return VlcTrackImpl(getSelectedTrack(IMedia.Track.Type.Video))
}

fun MediaPlayer.getSelectedAudioTrackId(): String {
    return getSelectedTrack(IMedia.Track.Type.Audio).id
}

fun MediaPlayer.getSelectedSpuTrackId(): String {
    return getSelectedTrack(IMedia.Track.Type.Text).id
}

fun MediaPlayer.getCurrentSpuTrack(): VlcTrack? {
    return VlcTrackImpl(getSelectedTrack(IMedia.Track.Type.Text))
}

fun MediaPlayer.getVideoTracksCount(): Int {
    return getTracks(IMedia.Track.Type.Video).size
}

fun MediaPlayer.getAudioTracksCount(): Int {
    return getTracks(IMedia.Track.Type.Audio).size
}

fun MediaPlayer.getSpuTracksCount(): Int {
    return getTracks(IMedia.Track.Type.Text).size
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

fun isVLC4() = true