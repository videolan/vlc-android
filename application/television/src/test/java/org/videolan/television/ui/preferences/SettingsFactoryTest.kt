/*
 * ************************************************************************
 *  SettingsFactoryTest.kt
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

package org.videolan.television.ui.preferences

import android.content.Context
import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.videolan.vlc.R

/**
 * Unit tests for [SettingsFactory].
 *
 * Verifies that the settings structure is correctly defined and all expected
 * categories are present.
 */
class SettingsFactoryTest {

    private val context: Context = mockk(relaxed = true)
    private val resources: Resources = mockk(relaxed = true)

    @Before
    fun setup() {
        every { context.resources } returns resources
        // Mock string arrays used in factory
        every { resources.getStringArray(any()) } returns arrayOf("Entry 1", "Entry 2")
    }

    /**
     * Verifies that createSettings returns all expected categories and they are not empty.
     */
    @Test
    fun whenCreateSettingsIsCalled_allCategoriesArePresent() {
        val categories = SettingsFactory.createSettings(context)
        
        assertNotNull(categories)
        assertTrue(categories.isNotEmpty())
        
        val titles = categories.map { it.title }
        
        assertTrue(titles.contains(R.string.general))
        assertTrue(titles.contains(R.string.video_prefs_category))
        assertTrue(titles.contains(R.string.audio_prefs_category))
        assertTrue(titles.contains(R.string.subtitles_prefs_category))
        assertTrue(titles.contains(R.string.interface_prefs_screen))
        assertTrue(titles.contains(R.string.parental_control))
        assertTrue(titles.contains(R.string.remote_access))
        assertTrue(titles.contains(R.string.advanced_prefs_category))
    }

    /**
     * Verifies that each category contains items (except Search which is specialized).
     */
    @Test
    fun eachCategoryContainsItems() {
        val categories = SettingsFactory.createSettings(context)
        
        categories.forEach { category ->
            if (category.title != R.string.search) {
                assertFalse("Category ${category.title} should not be empty", category.items.isEmpty())
            }
        }
    }
}
