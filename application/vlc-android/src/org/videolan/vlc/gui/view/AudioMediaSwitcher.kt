/*****************************************************************************
 * AudioMediaSwitcher.java
 *
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
 */

package org.videolan.vlc.gui.view

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.helpers.AudioUtil

abstract class AudioMediaSwitcher(context: Context, attrs: AttributeSet) : FlingViewGroup(context, attrs) {

    private lateinit var audioMediaSwitcherListener: AudioMediaSwitcherListener

    private var hasPrevious: Boolean = false
    private var previousPosition: Int = 0

    override val viewSwitchListener = object : ViewSwitchListener {
        override fun onSwitching(progress: Float) {
            audioMediaSwitcherListener.onMediaSwitching()
        }

        override fun onSwitched(position: Int) {
            audioMediaSwitcherListener.let {
                when {
                    previousPosition == position -> it.onMediaSwitched(AudioMediaSwitcherListener.CURRENT_MEDIA)
                    position == 0 && hasPrevious -> it.onMediaSwitched(AudioMediaSwitcherListener.PREVIOUS_MEDIA)
                    position == 1 && !hasPrevious -> it.onMediaSwitched(AudioMediaSwitcherListener.NEXT_MEDIA)
                    position == 2 -> it.onMediaSwitched(AudioMediaSwitcherListener.NEXT_MEDIA)
                }
                previousPosition = position
            }
        }

        override fun onTouchDown() {
            audioMediaSwitcherListener.onTouchDown()
        }

        override fun onTouchUp() {
            audioMediaSwitcherListener.onTouchUp()
        }

        override fun onTouchClick() {
            audioMediaSwitcherListener.onTouchClick()
        }

        override fun onTouchLongClick() {
            audioMediaSwitcherListener.onTouchLongClick()
        }

        override fun onBackSwitched() {}
    }

    fun onTextClicked() {
        audioMediaSwitcherListener.onTextClicked()
    }

    suspend fun updateMedia(service: PlaybackService?) {
        if (service == null) return
        val artMrl = service.coverArt
        val prevArtMrl = service.prevCoverArt
        val nextArtMrl = service.nextCoverArt
        val (coverCurrent, coverPrev, coverNext) = withContext(Dispatchers.IO) {
            Triple(
                    artMrl.let { AudioUtil.readCoverBitmap(Uri.decode(artMrl), 512) },
                    prevArtMrl.let { AudioUtil.readCoverBitmap(Uri.decode(prevArtMrl), 512) },
                    nextArtMrl?.let { AudioUtil.readCoverBitmap(Uri.decode(nextArtMrl), 512) }
            )
        }
        val trackInfo = service.trackInfo()
        val prevTrackInfo = service.prevTrackInfo()
        val nextTrackInfo = service.nextTrackInfo()
        removeAllViews()

        hasPrevious = false
        previousPosition = 0

        val inflater = LayoutInflater.from(context)
        if (service.hasPrevious()) {
            addMediaView(inflater, service.titlePrev, service.artistPrev, service.albumPrev, coverPrev, prevTrackInfo)
            hasPrevious = true
        }
        val chapter = service.getCurrentChapter()
        val (titleCurrent, artistCurrent, albumCurrent) = when {
            !chapter.isNullOrEmpty() -> arrayOf(chapter, service.title, service.artist)
            else -> arrayOf(service.title, service.artist, service.album);
        }
        if (service.hasMedia()) addMediaView(inflater, titleCurrent, artistCurrent, albumCurrent, coverCurrent, trackInfo)
        if (service.hasNext()) addMediaView(inflater, service.titleNext, service.artistNext, service.albumNext, coverNext, nextTrackInfo)

        if (service.hasPrevious() && service.hasMedia()) {
            previousPosition = 1
            scrollTo(1)
        } else
            scrollTo(0)
    }

    protected abstract fun addMediaView(inflater: LayoutInflater, title: String?, artist: String?, album: String?, cover: Bitmap?, trackInfo: String?)

    fun setAudioMediaSwitcherListener(l: AudioMediaSwitcherListener) {
        audioMediaSwitcherListener = l
    }

    interface AudioMediaSwitcherListener {

        fun onMediaSwitching()

        fun onMediaSwitched(position: Int)

        fun onTouchDown()

        fun onTouchUp()

        fun onTouchClick()

        fun onTouchLongClick()

        fun onTextClicked()

        companion object {
            const val PREVIOUS_MEDIA = 1
            const val CURRENT_MEDIA = 2
            const val NEXT_MEDIA = 3
        }
    }

    object EmptySwitcherListener : AudioMediaSwitcherListener {
        override fun onMediaSwitching() {}
        override fun onMediaSwitched(position: Int) {}
        override fun onTouchDown() {}
        override fun onTouchUp() {}
        override fun onTouchClick() {}
        override fun onTouchLongClick() = Unit
        override fun onTextClicked() {}
    }
}
