package org.videolan.vlc.gui.preferences

import android.os.Build
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.filters.SdkSuppress
import org.junit.Rule
import org.junit.Test
import org.videolan.tools.ENABLE_BRIGHTNESS_GESTURE
import org.videolan.tools.ENABLE_DOUBLE_TAP_SEEK
import org.videolan.tools.ENABLE_SEEK_BUTTONS
import org.videolan.tools.ENABLE_VOLUME_GESTURE
import org.videolan.tools.KEY_AUDIO_BOOST
import org.videolan.tools.KEY_SAVE_INDIVIDUAL_AUDIO_DELAY
import org.videolan.tools.POPUP_FORCE_LEGACY
import org.videolan.tools.POPUP_KEEPSCREEN
import org.videolan.vlc.PreferenceMatchers.withKey
import org.videolan.vlc.R
import org.videolan.vlc.onPreferenceRow

class PreferencesVideoUITest: BasePreferenceUITest() {
    @get:Rule
    val intentsTestRule = IntentsTestRule(PreferencesActivity::class.java)

    lateinit var activity: PreferencesActivity

    override fun beforeTest() {
        activity = intentsTestRule.activity

        onPreferenceRow(R.id.recycler_view, withKey("video_category"))!!
                .perform(click())
    }

    @Test
    fun checkAudioIndividualDelaySetting() {
        val key = KEY_SAVE_INDIVIDUAL_AUDIO_DELAY
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    fun checkVideoTitleOnTransitionSetting() {
        val key = "video_transition_show"
        checkToggleWorks(key, settings)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun checkForcePipLegacySetting() {
        val key = POPUP_FORCE_LEGACY
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    fun checkConfirmResumeShowDialogSetting() {
        val key = "dialog_confirm_resume"
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    fun checkAudioBoostSetting() {
        val key = KEY_AUDIO_BOOST
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    fun checkVolumeGestureSetting() {
        val key = ENABLE_VOLUME_GESTURE
        checkToggleWorks(key, settings)
    }

    @Test
    fun checkBrightnessGestureSetting() {
        val key = ENABLE_BRIGHTNESS_GESTURE
        checkToggleWorks(key, settings)
    }

    @Test
    fun checkDoubleTapSeekSetting() {
        val key = ENABLE_DOUBLE_TAP_SEEK
        checkToggleWorks(key, settings)
    }

    @Test
    fun checkShowSeekButtonSetting() {
        val key = ENABLE_SEEK_BUTTONS
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.O)
    fun checkKeepScreenOnSetting() {
        val key = POPUP_KEEPSCREEN
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    fun checkCloneModeSetting() {
        // TODO: Fails due to android bug in scrolling

        val key = "enable_clone_mode"
        checkToggleWorks(key, settings, default = false)
    }
}