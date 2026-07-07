/*
 * ************************************************************************
 *  SettingsViewModelTest.kt
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

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.videolan.tools.LocaleUtils
import org.videolan.vlc.gui.preferences.search.PreferenceItem
import org.videolan.vlc.gui.preferences.search.PreferenceParser
import java.util.Locale

/**
 * Unit tests for [SettingsViewModel].
 *
 * These tests verify the core logic of settings management, reactive search results,
 * and navigation identity within the TV settings module.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val application: Application = mockk(relaxed = true)
    private val localizedContext: Context = mockk(relaxed = true)
    private val settings: SharedPreferences = mockk(relaxed = true)
    private val resources: Resources = mockk(relaxed = true)

    private lateinit var viewModel: SettingsViewModel

    /**
     * Sets up the test environment by mocking necessary dependencies and static objects.
     */
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock static objects to isolate ViewModel logic from Android framework side effects
        mockkObject(SettingsFactory)
        mockkObject(PreferenceParser)
        mockkObject(LocaleUtils)
        
        every { application.resources } returns resources
        every { application.applicationContext } returns application
        every { application.getString(any()) } returns "mock_string"
        
        // Mock locale-specific extensions to prevent crashes during init
        with(LocaleUtils) {
            every { application.getLocales() } returns emptyList<Locale>()
        }

        every { localizedContext.resources } returns resources
        every { localizedContext.getString(any()) } returns "mock_string"
        
        // Provide a predictable set of categories for testing
        every { SettingsFactory.createSettings(any()) } returns listOf(
            SettingCategory(org.videolan.vlc.R.string.general, listOf(
                SettingItem.Toggle("auto_rescan", org.videolan.vlc.R.string.auto_rescan)
            ), 0),
            SettingCategory(org.videolan.vlc.R.string.audio_prefs_category, listOf(
                SettingItem.Toggle("audio_preferred_language", org.videolan.vlc.R.string.audio_preferred_language)
            ), 0)
        )

        // Mock PreferenceParser to return an empty list by default
        every { PreferenceParser.parsePreferences(any()) } returns arrayListOf<PreferenceItem>()

        viewModel = SettingsViewModel(application, localizedContext, settings)
    }

    /**
     * Cleans up mocks after each test.
     */
    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Verifies that the search query must meet a minimum length (2 characters)
     * before returning results.
     */
    @Test
    fun whenSearchQueryIsShort_resultsAreEmpty() = runTest {
        viewModel.setSearchQuery("a")
        // We expect the flow to eventually emit an empty list for a short query.
        val results = viewModel.searchResults.first()
        assertTrue(results.isEmpty())
    }

    /**
     * Verifies that search correctly filters settings items based on a match
     * with the title or summary.
     */
    @Test
    fun whenSearchQueryMatches_resultsAreFiltered() = runTest {
        val mockItems = arrayListOf(
            PreferenceItem("auto_rescan", 0, "Auto Rescan", "Summary", "Auto Rescan Eng", "Summary Eng", "General", "General Eng", "true"),
            PreferenceItem("other", 0, "Other", "Summary", "Other Eng", "Summary Eng", "General", "General Eng", "false")
        )
        // Mock PreferenceParser to return our test items.
        every { PreferenceParser.parsePreferences(any()) } returns mockItems

        // Set the query
        viewModel.setSearchQuery("Auto")
        
        // Trigger the search by forcing categories to emit.
        viewModel.setCategories(listOf(
            SettingCategory(org.videolan.vlc.R.string.general, listOf(
                SettingItem.Toggle("auto_rescan", org.videolan.vlc.R.string.auto_rescan)
            ), 0)
        ))
        
        // Use first { ... } to wait for the expected emission.
        val results = viewModel.searchResults.first { it.isNotEmpty() }
        
        assertEquals(1, results.size)
        assertEquals("auto_rescan", results[0].key)
    }

    /**
     * Verifies that deep-linking to a specific preference key correctly identifies
     * and selects its parent category.
     */
    @Test
    fun whenInitWithPreferenceKey_correctCategoryIsSelected() = runTest {
        // Mock categories for init
        viewModel.setCategories(listOf(
            SettingCategory(org.videolan.vlc.R.string.audio_prefs_category, listOf(
                SettingItem.Toggle("audio_preferred_language", org.videolan.vlc.R.string.audio_preferred_language)
            ), 0)
        ))

        viewModel.init("audio_preferred_language")
        
        val selected = viewModel.selectedCategory.value
        assertEquals(org.videolan.vlc.R.string.audio_prefs_category, selected?.title)
    }
}
