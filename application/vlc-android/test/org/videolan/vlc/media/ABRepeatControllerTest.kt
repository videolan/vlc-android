/*
 * ************************************************************************
 *  ABRepeatControllerTest.kt
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

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.vlc.BaseTest
import org.videolan.vlc.media.control.ABRepeatController

class ABRepeatControllerTest : BaseTest() {

    @Test
    fun testABRepeatValueSetting() {
        val settings: SharedPreferences = mockk(relaxed = true) {
            every { getBoolean(PLAYBACK_HISTORY, true) } returns true
        }
        val controller = ABRepeatController(settings)
        val media: MediaWrapper = mockk(relaxed = true)

        // Initial setting sets start marker
        controller.setABRepeatValue(media, 1000L)
        val abRepeat1 = controller.abRepeat.value
        assertEquals(1000L, abRepeat1?.start)
        assertEquals(-1L, abRepeat1?.stop)
        verify { media.setLongMeta(MediaWrapper.META_AB_REPEAT_START, 1000L) }

        // Second setting sets stop marker
        controller.setABRepeatValue(media, 5000L)
        val abRepeat2 = controller.abRepeat.value
        assertEquals(1000L, abRepeat2?.start)
        assertEquals(5000L, abRepeat2?.stop)
        verify { media.setLongMeta(MediaWrapper.META_AB_REPEAT_STOP, 5000L) }
    }

    @Test
    fun testToggleABRepeat() {
        val settings: SharedPreferences = mockk(relaxed = true)
        val controller = ABRepeatController(settings)

        assertFalse(controller.abRepeatOn.value ?: true)

        controller.toggleABRepeat()
        assertTrue(controller.abRepeatOn.value ?: false)

        controller.toggleABRepeat()
        assertFalse(controller.abRepeatOn.value ?: true)
        assertEquals(-1L, controller.abRepeat.value?.start)
        assertEquals(-1L, controller.abRepeat.value?.stop)
    }

    @Test
    fun testResetABRepeatValues() {
        val settings: SharedPreferences = mockk(relaxed = true)
        val controller = ABRepeatController(settings)
        val media: MediaWrapper = mockk(relaxed = true)

        controller.setABRepeatValue(media, 2000L)
        controller.resetABRepeatValues(media)

        assertEquals(-1L, controller.abRepeat.value?.start)
        assertEquals(-1L, controller.abRepeat.value?.stop)
        verify { media.setLongMeta(MediaWrapper.META_AB_REPEAT_START, 0L) }
        verify { media.setLongMeta(MediaWrapper.META_AB_REPEAT_STOP, 0L) }
    }
}
