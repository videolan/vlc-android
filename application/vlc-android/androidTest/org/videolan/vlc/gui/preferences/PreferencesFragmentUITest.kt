package org.videolan.vlc.gui.preferences

import android.content.ComponentName
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.videolan.tools.KEY_MEDIALIBRARY_AUTO_RESCAN
import org.videolan.vlc.PreferenceMatchers.withKey
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.onPreferenceRow
import org.videolan.tools.KEY_VIDEO_APP_SWITCH
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.SCREEN_ORIENTATION

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class PreferencesFragmentUITest: BasePreferenceUITest() {
    @get:Rule
    val intentsTestRule = IntentsTestRule(PreferencesActivity::class.java)

    lateinit var activity: PreferencesActivity

    override fun beforeTest() {
        activity = intentsTestRule.activity
    }

    @Test
    fun clickOnMediaFolders_openDirectoriesActivity() {
        val key = "directories"
        onPreferenceRow(R.id.recycler_view, withKey(key))!!
                .check(matches(isDisplayed()))
                .perform(click())

        intended(allOf(
                hasComponent(ComponentName(context, SecondaryActivity::class.java)),
                hasExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
        ))
    }

    @Test
    fun clickOnToggleRescan_keyToggled() {
        checkToggleWorks(KEY_MEDIALIBRARY_AUTO_RESCAN, settings)
    }

    @Test
    fun checkPipModeSetting() {
        val key = KEY_VIDEO_APP_SWITCH

        checkModeChanged(key, "0", "0", MAP_PIP_MODE)
        checkModeChanged(key, "1", "0", MAP_PIP_MODE)
        checkModeChanged(key, "2", "0", MAP_PIP_MODE)
    }

    @Test
    fun checkHardwareAccelerationSetting() {
        val key = "hardware_acceleration"

        checkModeChanged(key, "-1", "-1", MAP_HARDWARE_ACCEL)
        checkModeChanged(key, "0", "-1", MAP_HARDWARE_ACCEL)
        checkModeChanged(key, "1", "-1", MAP_HARDWARE_ACCEL)
        checkModeChanged(key, "2", "-1", MAP_HARDWARE_ACCEL)
    }

    @Test
    fun checkScreenOrientationSetting() {
        val key = SCREEN_ORIENTATION

        checkModeChanged(key, "99", "99", MAP_ORIENTATION)
        checkModeChanged(key, "100", "99", MAP_ORIENTATION)
        checkModeChanged(key, "101", "99", MAP_ORIENTATION)
        checkModeChanged(key, "102", "99", MAP_ORIENTATION)
    }

    @Test
    fun checkPlaybackHistorySetting() {
        val key = PLAYBACK_HISTORY

        checkToggleWorks(key, settings)
    }

    companion object {
        val MAP_PIP_MODE = mapOf("0" to R.string.stop, "1" to R.string.play_as_audio_background, "2" to R.string.play_pip_title)
        val MAP_HARDWARE_ACCEL = mapOf("-1" to R.string.automatic, "0" to R.string.hardware_acceleration_disabled, "1" to R.string.hardware_acceleration_decoding, "2" to R.string.hardware_acceleration_full)
        val MAP_ORIENTATION = mapOf("99" to R.string.screen_orientation_sensor, "100" to R.string.screen_orientation_start_lock, "101" to R.string.screen_orientation_landscape, "102" to R.string.screen_orientation_portrait)
    }
}