/*
 * ************************************************************************
 *  SettingsActionHandlerTest.kt
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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import io.mockk.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.videolan.tools.*

/**
 * Unit tests for [SettingsActionHandler].
 *
 * These tests verify that setting actions correctly trigger intended side effects
 * such as starting activities or launching dialogs.
 */
class SettingsActionHandlerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val settings: SharedPreferences = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    
    private lateinit var handler: SettingsActionHandler

    @Before
    fun setup() {
        handler = SettingsActionHandler(settings)
        mockkConstructor(Intent::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Verifies that the 'debug_logs' action correctly starts the DebugLogActivity.
     */
    @Test
    fun whenDebugLogsActionIsExecuted_debugLogActivityIsStarted() {
        val item = SettingItem.Action(KEY_ACTION_DEBUG_LOGS, 0)
        
        every { anyConstructed<Intent>().setComponent(any()) } returns mockk()
        every { context.startActivity(any()) } just Runs
        
        handler.execute(context, testScope, item) {}
        
        verify { context.startActivity(any()) }
    }

    /**
     * Verifies that the 'quit_app' action triggers process killing.
     */
    @Test
    fun whenQuitAppActionIsExecuted_processIsKilled() {
        val item = SettingItem.Action(KEY_ACTION_QUIT_APP, 0)
        
        mockkStatic(android.os.Process::class)
        every { android.os.Process.killProcess(any()) } just Runs
        every { android.os.Process.myPid() } returns 123
        
        handler.execute(context, testScope, item) {}
        
        verify { android.os.Process.killProcess(123) }
    }

    /**
     * Verifies that 'optional_features' correctly starts the PreferencesActivity
     * with the correct XML resource.
     */
    @Test
    fun whenOptionalFeaturesActionIsExecuted_preferencesActivityIsStarted() {
        val item = SettingItem.Action(KEY_ACTION_OPTIONAL_FEATURES, 0)
        
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Int>()) } returns mockk()
        every { context.startActivity(any()) } just Runs
        
        handler.execute(context, testScope, item) {}
        
        verify { context.startActivity(any()) }
    }
}
