/*
 * ************************************************************************
 *  QueueNavigator.kt
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

import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.security.SecureRandom
import java.util.Stack

/**
 * Handles queue navigation, including shuffle history, repeat mode calculations,
 * and next/prev index determination.
 */
class QueueNavigator(
    val repeating: MutableStateFlow<Int> = MutableStateFlow(PlaybackStateCompat.REPEAT_MODE_NONE),
    val shuffling: MutableStateFlow<Boolean> = MutableStateFlow(false)
) {

    var nextIndex: Int = -1
    var prevIndex: Int = -1
    var lastPrevious: Long = -1L

    val previous = Stack<Int>()
    private val random = SecureRandom()

    var isShuffling: Boolean
        get() = shuffling.value
        set(value) {
            shuffling.value = value
        }

    fun hasPrevious(): Boolean = prevIndex != -1

    fun hasNext(): Boolean = nextIndex != -1

    fun clearHistory() {
        previous.clear()
    }

    fun pushPrevious(index: Int) {
        previous.push(index)
    }

    fun popPrevious(): Int? = if (previous.isNotEmpty()) previous.pop() else null

    suspend fun determinePrevAndNextIndices(
        currentIndex: Int,
        playQueue: PlayQueue,
        expand: Boolean = false,
        expandFn: (suspend (Boolean, Boolean) -> Int)? = null
    ) {
        val media = playQueue.getCurrentMedia()
        if (expand && media != null && expandFn != null) {
            nextIndex = expandFn(media.type == MediaWrapper.TYPE_STREAM, media.hasFlag(MediaWrapper.MEDIA_NO_PARSE))
        } else {
            nextIndex = -1
        }
        prevIndex = -1

        if (nextIndex == -1) {
            val size = playQueue.size
            if (size == 0) return

            isShuffling = isShuffling && (size > 2)

            if (isShuffling) {
                if (previous.isNotEmpty()) {
                    prevIndex = previous.peek()
                    while (!playQueue.isValidPosition(prevIndex)) {
                        previous.removeAt(previous.size - 1)
                        if (previous.isEmpty()) {
                            prevIndex = -1
                            break
                        }
                        prevIndex = previous.peek()
                    }
                }
                if (previous.size + 1 >= size) {
                    if (repeating.value == PlaybackStateCompat.REPEAT_MODE_NONE) {
                        nextIndex = -1
                        return
                    } else {
                        previous.clear()
                    }
                }
                do {
                    nextIndex = random.nextInt(size)
                } while (nextIndex == currentIndex || previous.contains(nextIndex))
            } else {
                if (currentIndex > 0) prevIndex = currentIndex - 1
                nextIndex = when {
                    currentIndex + 1 < size -> currentIndex + 1
                    repeating.value == PlaybackStateCompat.REPEAT_MODE_NONE -> -1
                    else -> 0
                }
            }
        }
    }

    fun previousTotalTime(playQueue: PlayQueue, currentIndex: Int): Long {
        if (playQueue.size == 0 || currentIndex < 0) return 0L

        return if (isShuffling) {
            previous.asSequence()
                .mapNotNull { playQueue.getMedia(it)?.length }
                .sum()
        } else {
            (0 until currentIndex).asSequence()
                .mapNotNull { playQueue.getMedia(it)?.length }
                .sum()
        }
    }
}
