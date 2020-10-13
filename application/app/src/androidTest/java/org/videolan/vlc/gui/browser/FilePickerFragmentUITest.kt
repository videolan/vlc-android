/*
 * ************************************************************************
 *  FilePickerFragmentUITest.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.browser

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.rule.ActivityTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.Rule
import org.junit.Test
import org.videolan.vlc.R

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class FilePickerFragmentUITest : org.videolan.vlc.BaseUITest() {
    @Rule
    @JvmField
    val activityTestRule = ActivityTestRule(FilePickerActivity::class.java)

    lateinit var activity: FilePickerActivity

    override fun beforeTest() {
        activity = activityTestRule.activity
    }

    @Test
    fun whenAtSomeFolder_clickOnHomeIconReturnsBackToRoot() {
        onView(org.videolan.vlc.withRecyclerView(R.id.network_list).atPosition(0)).perform(click())
        onView(org.videolan.vlc.withRecyclerView(R.id.network_list).atPosition(0)).perform(click())

        onView(withId(R.id.network_list)).check(matches(org.videolan.vlc.withCount(greaterThan(2))))

    }
}
