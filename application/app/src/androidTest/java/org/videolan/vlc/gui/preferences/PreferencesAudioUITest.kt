package org.videolan.vlc.gui.preferences

import android.os.Build
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.filters.SdkSuppress
import org.junit.Rule
import org.junit.Test
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.KEY_AUDIO_TASK_REMOVED
import org.videolan.tools.KEY_ENABLE_HEADSET_DETECTION
import org.videolan.tools.KEY_ENABLE_PLAY_ON_HEADSET_INSERTION
import org.videolan.tools.RESUME_PLAYBACK
import org.videolan.vlc.PreferenceMatchers.withKey
import org.videolan.vlc.R
import org.videolan.vlc.onPreferenceRow

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
    fun checkPersistentAudioPlaybackSetting() {
        val key = KEY_AUDIO_TASK_REMOVED
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
        val key = KEY_ENABLE_HEADSET_DETECTION
        checkToggleWorks(key, settings)
    }

    @Test
    fun checkResumeOnHeadsetSetting() {
        val key = KEY_ENABLE_PLAY_ON_HEADSET_INSERTION
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
        val key = KEY_AOUT

        checkModeChanged(key, "0", "0", MAP_AOUT)
        checkModeChanged(key, "1", "0", MAP_AOUT)
        checkModeChanged(key, "2", "0", MAP_AOUT)
    }

    companion object {
        val MAP_AOUT = mapOf("0" to R.string.aout_aaudio, "1" to R.string.aout_audiotrack, "2" to R.string.aout_opensles)
    }
}
