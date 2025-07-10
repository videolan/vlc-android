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
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.view.get
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.UiDevice
import androidx.viewpager.widget.ViewPager
import org.hamcrest.core.AllOf
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.EXTRA_FOR_ESPRESSO
import org.videolan.resources.EXTRA_TARGET
import org.videolan.tools.KEY_AUDIO_RESUME_CARD
import org.videolan.tools.Settings
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.helpers.UiTools.isTablet
import org.videolan.vlc.gui.view.TitleListView
import org.videolan.vlc.util.DpadHelper.pressHome
import org.videolan.vlc.util.DpadHelper.pressPip
import org.videolan.vlc.util.DpadHelper.pressStop
import org.videolan.vlc.util.DummyMediaWrapperProvider
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
        waitUntilLoaded { activity.findViewById(R.id.audio_list) }
        onView(withId(R.id.sliding_tabs)).perform(TabsMatcher(0))
        waitUntilLoaded { activity.findViewById(R.id.audio_list) }
        SystemClock.sleep(1500)
        waitUntilLoaded { activity.findViewById<ViewPager>(R.id.pager).get(0).findViewById(R.id.audio_list) }
        SystemClock.sleep(500)
        ScreenshotUtil.takeScreenshot(2, "audio_list")
        onView(withId(R.id.sliding_tabs)).perform(TabsMatcher(2))
        waitUntilLoaded { activity.findViewById<ViewPager>(R.id.pager).get(2).findViewById(R.id.audio_list) }
        SystemClock.sleep(1500)
        //We use the audio list as PiP background. The PiP img is static
        ScreenshotUtil.takeScreenshot(7,"pip_video")

        onView(withId(R.id.ml_menu_last_playlist)).perform(click())
        onView(isRoot()).perform(waitId(R.id.audio_media_switcher, 5000))
        activity.slideUpOrDownAudioPlayer()
        SystemClock.sleep(1500)
        waitUntilLoaded { activity.findViewById(R.id.songs_list) }
        SystemClock.sleep(1500)
        ScreenshotUtil.takeScreenshot(4,"audio_player_playlist")
        onView(withId(R.id.playlist_switch)).perform(click())
        ScreenshotUtil.takeScreenshot(3,"audio_player")
        onView(withId(R.id.playlist_switch)).perform(click())

        onView(withId(R.id.adv_function)).perform(click())
        waitUntilLoaded { activity.findViewById(R.id.options_list) }
        onView(withId(R.id.options_list))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(3, click()))
        waitId(R.id.equalizer_bands, 5000)
        ScreenshotUtil.takeScreenshot(9,"equalizer")
        pressBack()

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
        SystemClock.sleep(1500)
        Log.d("Espresso", "1")

        ScreenshotUtil.takeScreenshot(1, "video_list")

        val rvMatcher = withRecyclerView(R.id.video_grid)
        Log.d("Espresso", "2")
        onView(rvMatcher.atPosition(2)).perform(click())
        Log.d("Espresso", "3")

        onView(isRoot()).perform(orientationLandscape())
        onView(isRoot()).perform(waitId(R.id.player_root, 5000))

        SystemClock.sleep(1500)
        onView(withId(R.id.player_root)).perform(click())
        SystemClock.sleep(500)
        ScreenshotUtil.takeScreenshot(6, "video_player")

    }

    @Test
    fun testTakeScreenshotBrowser() {
        onView(AllOf.allOf(withId(R.id.nav_directories), withEffectiveVisibility(Visibility.VISIBLE)))
                 .perform(click())
        waitUntilLoaded { activity.findViewById<TitleListView>(R.id.network_browser_entry).findViewById(R.id.list) }

        ScreenshotUtil.takeScreenshot(5,"browser")
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
        Settings.getInstance(context).edit().putBoolean("auto_rescan", false).putBoolean(KEY_AUDIO_RESUME_CARD, false).commit()
        val intent = Intent().apply {
            putExtra(EXTRA_TARGET, R.id.nav_audio)
            putParcelableArrayListExtra(
                EXTRA_FOR_ESPRESSO, arrayListOf(
                    MLServiceLocator.getAbstractMediaWrapper(
                        Uri.parse("upnp://test/mock"), 0L, 0F, 0L, MediaWrapper.TYPE_ALL,
                        null, "My NAS", -1, -1, "",
                        "", -1, "", "",
                        0, 0, "/storage/emulated/0/Download/upnp2.png",
                        0, 0, 0,
                        0, 0L, 0L,
                        0L
                    ),
                    MLServiceLocator.getAbstractMediaWrapper(
                        Uri.parse("upnp://test/mock"), 0L, 0F, 0L, MediaWrapper.TYPE_ALL,
                        null, "My SMB server", -1, -1, "",
                        "", -1, "", "",
                        0, 0, "/storage/emulated/0/Download/upnp1.png",
                        0, 0, 0,
                        0, 0L, 0L,
                        0L
                    )

                    )
            )
        }
        activityTestRule.launchActivity(intent)
        activity = activityTestRule.activity
    }
}
