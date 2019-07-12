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
import kotlinx.coroutines.*
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.helpers.AudioUtil


@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
abstract class AudioMediaSwitcher(context: Context, attrs: AttributeSet) : FlingViewGroup(context, attrs) {

    private var mAudioMediaSwitcherListener: AudioMediaSwitcherListener? = null

    private var hasPrevious: Boolean = false
    private var previousPosition: Int = 0

    private val mViewSwitchListener = object : ViewSwitchListener {

        override fun onSwitching(progress: Float) {
            if (mAudioMediaSwitcherListener != null)
                mAudioMediaSwitcherListener!!.onMediaSwitching()
        }

        override fun onSwitched(position: Int) {
            if (mAudioMediaSwitcherListener != null) {
                if (previousPosition != position) {
                    if (position == 0 && hasPrevious)
                        mAudioMediaSwitcherListener!!.onMediaSwitched(AudioMediaSwitcherListener.PREVIOUS_MEDIA)
                    if (position == 1 && !hasPrevious)
                        mAudioMediaSwitcherListener!!.onMediaSwitched(AudioMediaSwitcherListener.NEXT_MEDIA)
                    else if (position == 2)
                        mAudioMediaSwitcherListener!!.onMediaSwitched(AudioMediaSwitcherListener.NEXT_MEDIA)
                    previousPosition = position
                } else
                    mAudioMediaSwitcherListener!!.onMediaSwitched(AudioMediaSwitcherListener.CURRENT_MEDIA)
            }
        }

        override fun onTouchDown() {
            if (mAudioMediaSwitcherListener != null)
                mAudioMediaSwitcherListener!!.onTouchDown()
        }

        override fun onTouchUp() {
            if (mAudioMediaSwitcherListener != null)
                mAudioMediaSwitcherListener!!.onTouchUp()
        }

        override fun onTouchClick() {
            if (mAudioMediaSwitcherListener != null)
                mAudioMediaSwitcherListener!!.onTouchClick()
        }

        override fun onBackSwitched() {}
    }

    init {

        setOnViewSwitchedListener(mViewSwitchListener)
    }

    fun updateMedia(scope: CoroutineScope, service: PlaybackService?) {
        if (service == null) return
        val artMrl = service.coverArt
        val prevArtMrl = service.prevCoverArt
        val nextArtMrl = service.nextCoverArt
        scope.launch {
            val coverCurrent = if (artMrl != null) withContext(Dispatchers.IO) { AudioUtil.readCoverBitmap(Uri.decode(artMrl), 512) } else null
            val coverPrev = if (prevArtMrl != null) withContext(Dispatchers.IO) { AudioUtil.readCoverBitmap(Uri.decode(prevArtMrl), 512) } else null
            val coverNext = if (nextArtMrl != null) withContext(Dispatchers.IO) { AudioUtil.readCoverBitmap(Uri.decode(nextArtMrl), 512) } else null
            removeAllViews()

            hasPrevious = false
            previousPosition = 0

            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            if (service.hasPrevious()) {
                addMediaView(inflater,
                        service.titlePrev,
                        service.artistPrev,
                        coverPrev)
                hasPrevious = true
            }
            if (service.hasMedia())
                addMediaView(inflater,
                        service.title,
                        service.artist,
                        coverCurrent)
            if (service.hasNext())
                addMediaView(inflater,
                        service.titleNext,
                        service.artistNext,
                        coverNext)

            if (service.hasPrevious() && service.hasMedia()) {
                previousPosition = 1
                scrollTo(1)
            } else
                scrollTo(0)
        }
    }

    protected abstract fun addMediaView(inflater: LayoutInflater, title: String?, artist: String?, cover: Bitmap?)

    fun setAudioMediaSwitcherListener(l: AudioMediaSwitcherListener) {
        mAudioMediaSwitcherListener = l
    }

    interface AudioMediaSwitcherListener {

        fun onMediaSwitching()

        fun onMediaSwitched(position: Int)

        fun onTouchDown()

        fun onTouchUp()

        fun onTouchClick()

        companion object {
            const val PREVIOUS_MEDIA = 1
            const val CURRENT_MEDIA = 2
            const val NEXT_MEDIA = 3
        }
    }
}
