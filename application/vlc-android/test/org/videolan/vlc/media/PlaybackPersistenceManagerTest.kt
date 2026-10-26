/*
 * ************************************************************************
 *  PlaybackPersistenceManagerTest.kt
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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.vlc.BaseTest
import org.videolan.vlc.media.persistence.PlaybackPersistenceManager
import org.videolan.vlc.media.persistence.ResumeStatus

class PlaybackPersistenceManagerTest : BaseTest() {

    @Test
    fun testLoadResumeStatus() {
        val settings: SharedPreferences = mockk(relaxed = true) {
            every { getString("test_key_always", "0") } returns "0"
            every { getString("test_key_ask", "0") } returns "2"
            every { getString("test_key_never", "0") } returns "1"
        }
        val medialibrary: Medialibrary = mockk(relaxed = true)
        val manager = PlaybackPersistenceManager(settings, medialibrary)

        assertEquals(ResumeStatus.ALWAYS, manager.loadResumeStatus("test_key_always"))
        assertEquals(ResumeStatus.ASK, manager.loadResumeStatus("test_key_ask"))
        assertEquals(ResumeStatus.NEVER, manager.loadResumeStatus("test_key_never"))
    }
}
