/*
 * ************************************************************************
 *  PlayQueue.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.vlc.media.queue

import androidx.lifecycle.MutableLiveData
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.media.MediaWrapperList

/**
 * Manages the playback queue, containing the list of [MediaWrapper] items,
 * bounds validation, and queue mutation operations.
 */
class PlayQueue(
    val mediaList: MediaWrapperList = MediaWrapperList()
) : MediaWrapperList.EventListener {

    var currentIndex: Int = -1
        set(value) {
            field = value
            currentPlayedMedia.postValue(mediaList.getMedia(value))
        }

    val currentPlayedMedia: MutableLiveData<MediaWrapper?> = MutableLiveData(null)

    val size: Int
        get() = mediaList.size()

    fun hasMedia(): Boolean = size > 0

    fun hasPlaylist(): Boolean = size > 1

    fun canRepeat(): Boolean = size > 0

    fun canShuffle(): Boolean = size > 2

    fun isValidPosition(position: Int): Boolean = position in 0 until size

    fun getMedia(index: Int): MediaWrapper? = mediaList.getMedia(index)

    fun getCurrentMedia(): MediaWrapper? = mediaList.getMedia(currentIndex)

    fun getMediaList(): List<MediaWrapper> = mediaList.copy

    val isAudioList: Boolean
        get() = mediaList.isAudioList

    fun addEventListener(listener: MediaWrapperList.EventListener) {
        mediaList.addEventListener(listener)
    }

    fun removeEventListener(listener: MediaWrapperList.EventListener) {
        mediaList.removeEventListener(listener)
    }

    fun replaceWith(list: List<MediaWrapper>) {
        mediaList.replaceWith(list)
    }

    fun append(list: List<MediaWrapper>) {
        mediaList.removeEventListener(this)
        for (media in list) {
            mediaList.add(media)
        }
        mediaList.addEventListener(this)
    }

    fun insertNext(list: List<MediaWrapper>) {
        val targetIndex = if (isValidPosition(currentIndex)) currentIndex + 1 else size
        mediaList.removeEventListener(this)
        for ((offset, media) in list.withIndex()) {
            mediaList.insert(targetIndex + offset, media)
        }
        mediaList.addEventListener(this)
    }

    fun insertItem(index: Int, item: MediaWrapper) {
        mediaList.insert(index, item)
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        mediaList.move(fromPosition, toPosition)
    }

    fun remove(index: Int) {
        if (isValidPosition(index)) {
            mediaList.remove(index)
        }
    }

    fun removeLocation(location: String) {
        mediaList.remove(location)
    }

    fun clear() {
        mediaList.clear()
        currentIndex = -1
    }

    override fun onItemAdded(index: Int, mrl: String) {}

    override fun onItemRemoved(index: Int, mrl: String) {}

    override fun onItemMoved(indexBefore: Int, indexAfter: Int, mrl: String) {}
}
