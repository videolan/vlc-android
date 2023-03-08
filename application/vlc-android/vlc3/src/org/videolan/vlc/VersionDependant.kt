package org.videolan.vlc

import android.content.Context
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.vlc.gui.dialogs.adapters.VlcTrack

/*
 * ************************************************************************
 *  Vlc3.kt
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

fun IMedia.getAudioTracks(): List<IMedia.AudioTrack> {
    val tracks = ArrayList<IMedia.AudioTrack>()
    for (i in 0 until trackCount) {
        val track = getTrack(i)
        if (track is IMedia.AudioTrack) tracks.add(track)
    }
    return tracks.toList()
}

fun IMedia.getAllTracks(): List<IMedia.Track> {
    val result = ArrayList<IMedia.Track>()
    for (i in 0 until trackCount) {
        result.add(getTrack(i))
    }
    return result
}


fun MediaPlayer.getSelectedVideoTrack(): VlcTrack? = currentVideoTrack?.let { VlcTrackImpl(it) }

fun MediaPlayer.getSelectedAudioTrack(): VlcTrack? {
    val currentTrackId = audioTrack
   audioTracks?.forEach {
       if (it.id == currentTrackId) return VlcTrackImpl(it)
   }
    return null
}

fun MediaPlayer.getSelectedSpuTrack(): VlcTrack? {
    val currentTrackId = spuTrack
    spuTracks?.forEach {
        if (it.id == currentTrackId) return VlcTrackImpl(it)
    }
    return null
}

fun MediaPlayer.setVideoTrack(index:String):Boolean {
    return setVideoTrack(index.toInt())
}
fun MediaPlayer.setAudioTrack(index:String):Boolean {
    return setAudioTrack(index.toInt())
}

fun MediaPlayer.setSpuTrack(index:String):Boolean {
    return setSpuTrack(index.toInt())
}

fun MediaPlayer.getAllAudioTracks(): Array<VlcTrack> = audioTracks.convertToVlcTrack()
fun MediaPlayer.getAllVideoTracks():Array<VlcTrack> = videoTracks.convertToVlcTrack()
fun MediaPlayer.getAllSpuTracks():Array<VlcTrack> = spuTracks.convertToVlcTrack()

fun Array<MediaPlayer.TrackDescription>?.convertToVlcTrack(): Array<VlcTrack> {
    if (this == null) return arrayOf()
    val newTracks = ArrayList<VlcTrack>()
    this.forEach {
        newTracks.add(VlcTrackImpl(it))
    }
    return newTracks.toTypedArray()
}

fun MediaPlayer.unselectTrackType(type: Int) {
    throw IllegalStateException("This is a VLC 4 only API. It should not be called by VLC 3")
}
fun getDisableTrack(context: Context) : VlcTrack {
    throw IllegalStateException("This is a VLC 4 only API. It should not be called by VLC 3")
}
