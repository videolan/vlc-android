/*
 * ************************************************************************
 *  PlayQueueTest.kt
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

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.BaseTest
import org.videolan.vlc.media.queue.PlayQueue

class PlayQueueTest : BaseTest() {

    @Test
    fun testInitialQueueState() {
        val playQueue = PlayQueue()
        assertEquals(0, playQueue.size)
        assertEquals(-1, playQueue.currentIndex)
        assertFalse(playQueue.hasMedia())
        assertFalse(playQueue.hasPlaylist())
        assertNull(playQueue.getCurrentMedia())
    }

    @Test
    fun testAppendAndRetrieveMedia() {
        val playQueue = PlayQueue()
        val media1: MediaWrapper = mockk(relaxed = true)
        val media2: MediaWrapper = mockk(relaxed = true)

        playQueue.append(listOf(media1, media2))

        assertEquals(2, playQueue.size)
        assertTrue(playQueue.hasMedia())
        assertTrue(playQueue.hasPlaylist())
        assertTrue(playQueue.isValidPosition(0))
        assertTrue(playQueue.isValidPosition(1))
        assertFalse(playQueue.isValidPosition(2))
        assertEquals(media1, playQueue.getMedia(0))
        assertEquals(media2, playQueue.getMedia(1))
    }

    @Test
    fun testInsertNext() {
        val playQueue = PlayQueue()
        val media1: MediaWrapper = mockk(relaxed = true)
        val media2: MediaWrapper = mockk(relaxed = true)
        val inserted: MediaWrapper = mockk(relaxed = true)

        playQueue.append(listOf(media1, media2))
        playQueue.currentIndex = 0

        playQueue.insertNext(listOf(inserted))

        assertEquals(3, playQueue.size)
        assertEquals(media1, playQueue.getMedia(0))
        assertEquals(inserted, playQueue.getMedia(1))
        assertEquals(media2, playQueue.getMedia(2))
    }

    @Test
    fun testMoveItem() {
        val playQueue = PlayQueue()
        val media1: MediaWrapper = mockk(relaxed = true)
        val media2: MediaWrapper = mockk(relaxed = true)

        playQueue.append(listOf(media1, media2))
        playQueue.moveItem(0, 1)

        assertEquals(media2, playQueue.getMedia(0))
        assertEquals(media1, playQueue.getMedia(1))
    }

    @Test
    fun testRemoveAndClear() {
        val playQueue = PlayQueue()
        val media1: MediaWrapper = mockk(relaxed = true)
        val media2: MediaWrapper = mockk(relaxed = true)

        playQueue.append(listOf(media1, media2))
        playQueue.currentIndex = 0

        playQueue.remove(0)
        assertEquals(1, playQueue.size)
        assertEquals(media2, playQueue.getMedia(0))

        playQueue.clear()
        assertEquals(0, playQueue.size)
        assertEquals(-1, playQueue.currentIndex)
    }
}
