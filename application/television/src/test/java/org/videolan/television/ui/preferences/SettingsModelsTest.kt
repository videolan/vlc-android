/*
 * ************************************************************************
 *  SettingsModelsTest.kt
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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for setting data models.
 */
class SettingsModelsTest {

    /**
     * Verifies that getEffectiveKey returns the storageKey if provided.
     */
    @Test
    fun whenStorageKeyIsProvided_itIsReturnedAsEffectiveKey() {
        val item = SettingItem.Options(
            key = "ui_key",
            title = 0,
            storageKey = "actual_storage_key"
        )
        
        assertEquals("actual_storage_key", item.getEffectiveKey())
    }

    /**
     * Verifies that getEffectiveKey returns the key if no storageKey is provided.
     */
    @Test
    fun whenStorageKeyIsNotProvided_keyIsReturnedAsEffectiveKey() {
        val item = SettingItem.Options(
            key = "ui_key",
            title = 0
        )
        
        assertEquals("ui_key", item.getEffectiveKey())
    }
}
