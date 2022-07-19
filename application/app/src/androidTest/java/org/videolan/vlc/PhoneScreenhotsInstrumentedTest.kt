/*
 * ************************************************************************
 *  PhoneScreenhotsInstrumentedTest.kt
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

package org.videolan.vlc

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.util.Log
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.UiDevice
import org.hamcrest.core.AllOf
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.videolan.resources.EXTRA_TARGET
import org.videolan.tools.Settings
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.helpers.UiTools.isTablet
import org.videolan.vlc.util.DpadHelper.pressHome
import org.videolan.vlc.util.DpadHelper.pressPip
import org.videolan.vlc.util.DpadHelper.pressStop
import org.videolan.vlc.util.ScreenshotUtil
import org.videolan.vlc.util.UiUtils.waitId
import org.videolan.vlc.util.UiUtils.waitUntilLoaded
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

//@RunWith(AndroidJUnit4::class)
class PhoneScreenhotsInstrumentedTest : BaseUITest() {
    @Rule
    @JvmField
    val activityTestRule = ActivityTestRule(MainActivity::class.java, true, false)

    @Rule
    @JvmField
    val demoModeRule = DemoModeRule()

    lateinit var activity: MainActivity

    @Test
    fun testTakeScreenshot() {
        onView(isRoot()).perform(waitId(R.id.audio_list, 5000))
        //Audio
       // ScreenshotUtil.takeScreenshot("01_trash")
        onView(withId(R.id.sliding_tabs)).perform(TabsMatcher(0))
        waitUntilLoaded { activity.findViewById(R.id.audio_list) }
        SystemClock.sleep(500)
        ScreenshotUtil.takeScreenshot(3, "audio_list")
        onView(withId(R.id.sliding_tabs)).perform(TabsMatcher(2))
        SystemClock.sleep(500)
        waitUntilLoaded { activity.findViewById(R.id.audio_list) }

        onView(withId(R.id.ml_menu_last_playlist)).perform(click())
        onView(isRoot()).perform(waitId(R.id.audio_media_switcher, 5000))
        onView(withId(R.id.audio_media_switcher)).perform(click())
        SystemClock.sleep(300)
        ScreenshotUtil.takeScreenshot(6,"audio_player_playlist")
        onView(withId(R.id.playlist_switch)).perform(click())
        ScreenshotUtil.takeScreenshot(5,"audio_player")
        onView(withId(R.id.playlist_switch)).perform(click())
        //close audio player
        onView(withId(R.id.header)).perform(click())
        pressStop()
    }

    @Test
    fun testTakeScreenshotVideo() {
        onView(AllOf.allOf(withId(R.id.nav_video), withEffectiveVisibility(Visibility.VISIBLE)))
                .perform(click())
        Log.d("Espresso", "0")
        waitUntilLoaded { activity.findViewById(R.id.video_grid) }
        Log.d("Espresso", "1")

        ScreenshotUtil.takeScreenshot(1, "video_list")

        val rvMatcher = withRecyclerView(R.id.video_grid)
        Log.d("Espresso", "2")
        onView(rvMatcher.atPosition(2)).perform(click())
        Log.d("Espresso", "3")

        rotateLandscape()
        onView(isRoot()).perform(orientationLandscape())
        onView(isRoot()).perform(waitId(R.id.player_root, 5000))

        SystemClock.sleep(1500)
        onView(withId(R.id.player_root)).perform(click())
        SystemClock.sleep(500)
        ScreenshotUtil.takeScreenshot(2, "video_player")
        onView(withId(R.id.player_overlay_adv_function)).perform(ForceClickAction())
        SystemClock.sleep(500)
        ScreenshotUtil.takeScreenshot(7,"video_player_advanced_options")
        disableRotateLandscape()
        SystemClock.sleep(500)
        onView(withRecyclerView(R.id.options_list).atPositionOnView(4, R.id.option_title)).perform(click())
//        ScreenshotUtil.takeScreenshot(9,"video_player_equalizer")
        pressBack()
        onView(withId(R.id.player_overlay_adv_function)).perform(ForceClickAction())
        onView(withRecyclerView(R.id.options_list).atPositionOnView(6, R.id.option_title)).perform(click())
        pressHome()
        pressPip()
        ScreenshotUtil.takeScreenshot(8,"pip")
    }

    @Test
    fun testTakeScreenshotBrowser() {
        onView(AllOf.allOf(withId(R.id.nav_directories), withEffectiveVisibility(Visibility.VISIBLE)))
                 .perform(click())
         ScreenshotUtil.takeScreenshot(4,"browser")
     }

    private fun rotateLandscape() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).setOrientationLeft()
    private fun disableRotateLandscape() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).setOrientationNatural()

    companion object {
        @ClassRule
        @JvmField
        val localeTestRule = LocaleTestRule()
    }

    override fun beforeTest() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        Settings.getInstance(context).edit().putBoolean("auto_rescan", false).putBoolean("audio_resume_card", false).commit()
        val intent = Intent().apply {
            putExtra(EXTRA_TARGET, R.id.nav_audio)
        }
        activityTestRule.launchActivity(intent)
        activity = activityTestRule.activity
        if (activity.isTablet()) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
}
