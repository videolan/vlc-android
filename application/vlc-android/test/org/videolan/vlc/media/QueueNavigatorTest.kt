/*
 * ************************************************************************
 *  QueueNavigatorTest.kt
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

package org.videolan.vlc.media

import android.support.v4.media.session.PlaybackStateCompat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.BaseTest
import org.videolan.vlc.media.queue.PlayQueue
import org.videolan.vlc.media.queue.QueueNavigator

class QueueNavigatorTest : BaseTest() {

    @Test
    fun testInitialNavigatorState() {
        val navigator = QueueNavigator()
        assertEquals(-1, navigator.nextIndex)
        assertEquals(-1, navigator.prevIndex)
        assertFalse(navigator.hasNext())
        assertFalse(navigator.hasPrevious())
    }

    @Test
    fun testSequentialNavigation() = runTest {
        val playQueue = PlayQueue()
        val media1: MediaWrapper = mockk(relaxed = true)
        val media2: MediaWrapper = mockk(relaxed = true)
        val media3: MediaWrapper = mockk(relaxed = true)
        playQueue.append(listOf(media1, media2, media3))

        val navigator = QueueNavigator()

        // Current index 0 -> prev: -1, next: 1
        navigator.determinePrevAndNextIndices(0, playQueue)
        assertEquals(-1, navigator.prevIndex)
        assertEquals(1, navigator.nextIndex)
        assertTrue(navigator.hasNext())
        assertFalse(navigator.hasPrevious())

        // Current index 1 -> prev: 0, next: 2
        navigator.determinePrevAndNextIndices(1, playQueue)
        assertEquals(0, navigator.prevIndex)
        assertEquals(2, navigator.nextIndex)
        assertTrue(navigator.hasNext())
        assertTrue(navigator.hasPrevious())

        // Current index 2 (at end, repeat none) -> prev: 1, next: -1
        navigator.repeating.value = PlaybackStateCompat.REPEAT_MODE_NONE
        navigator.determinePrevAndNextIndices(2, playQueue)
        assertEquals(1, navigator.prevIndex)
        assertEquals(-1, navigator.nextIndex)
        assertFalse(navigator.hasNext())

        // Current index 2 (at end, repeat all) -> prev: 1, next: 0
        navigator.repeating.value = PlaybackStateCompat.REPEAT_MODE_ALL
        navigator.determinePrevAndNextIndices(2, playQueue)
        assertEquals(1, navigator.prevIndex)
        assertEquals(0, navigator.nextIndex)
        assertTrue(navigator.hasNext())
    }

    @Test
    fun testPreviousTotalTime() {
        val navigator = QueueNavigator()
        val media1: MediaWrapper = mockk(relaxed = true) { every { length } returns 100L }
        val media2: MediaWrapper = mockk(relaxed = true) { every { length } returns 200L }
        val media3: MediaWrapper = mockk(relaxed = true) { every { length } returns 300L }
        val playQueue = PlayQueue().apply { append(listOf(media1, media2, media3)) }

        // Linear playback at index 2 -> sum of media1 + media2 = 300L
        val time = navigator.previousTotalTime(playQueue, 2)
        assertEquals(300L, time)
    }
}
