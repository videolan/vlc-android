package org.videolan.vlc.gui.preferences

import android.os.Build
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.videolan.vlc.PreferenceMatchers.withKey
import org.videolan.vlc.R
import org.videolan.vlc.onPreferenceRow
import org.videolan.tools.KEY_PLAYBACK_SPEED_PERSIST
import org.videolan.tools.RESUME_PLAYBACK

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class PreferencesAudioUITest: BasePreferenceUITest() {
    @get:Rule
    val intentsTestRule = IntentsTestRule(PreferencesActivity::class.java)

    lateinit var activity: PreferencesActivity

    override fun beforeTest() {
        activity = intentsTestRule.activity

        onPreferenceRow(R.id.recycler_view, withKey("audio_category"))!!
                .perform(click())
    }

    @Test
    fun checkResumePlaybackAfterCallSetting() {
        val key = RESUME_PLAYBACK
        checkToggleWorks(key, settings)
    }

    @Test
    fun checkSavePlaybackSpeedSetting() {
        val key = KEY_PLAYBACK_SPEED_PERSIST
        checkToggleWorks(key, settings)
    }

    @Test
    fun checkPersistentAudioPlaybackSetting() {
        val key = "audio_task_removed"
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    fun checkDigitalAudioSetting() {
        val key = "audio_digital_output"
        checkToggleWorks(key, settings)
    }

    @Test
    fun checkPersistAudioRepeatModeSetting() {
        val key = "audio_save_repeat"
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    fun checkDetectHeadsetSetting() {
        val key = "enable_headset_detection"
        checkToggleWorks(key, settings)
    }

    @Test
    fun checkResumeOnHeadsetSetting() {
        val key = "enable_play_on_headset_insertion"
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.O)
    fun checkAudioDuckingSetting() {
        val key = "audio_ducking"
        checkToggleWorks(key, settings)
    }

    @Test
    fun checkAudioOutputModeSetting() {
        // TODO: Fails due to android bug in scrolling
        val key = "aout"

        checkModeChanged(key, "0", "0", MAP_AOUT)
        checkModeChanged(key, "1", "0", MAP_AOUT)
    }

    companion object {
        val MAP_AOUT = mapOf("0" to R.string.aout_audiotrack, "1" to R.string.aout_opensles)
    }
}