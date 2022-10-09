package org.videolan.vlc.gui.preferences

import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.videolan.vlc.PreferenceMatchers.isEnabled
import org.videolan.vlc.PreferenceMatchers.withKey
import org.videolan.vlc.R
import org.videolan.vlc.onPreferenceRow

class PreferencesCastingUITest: BasePreferenceUITest() {
    @get:Rule
    val intentsTestRule = IntentsTestRule(PreferencesActivity::class.java)

    lateinit var activity: PreferencesActivity

    override fun beforeTest() {
        activity = intentsTestRule.activity

        onPreferenceRow(R.id.recycler_view, withKey("casting_category"))!!
                .perform(click())
    }

    @Test
    fun checkWirelessCastingSetting() {
        val key = "enable_casting"

        onPreferenceRow(R.id.recycler_view, withKey("casting_passthrough"), isEnabled)!!
                .check(matches(isDisplayed()))

        checkToggleWorks(key, settings)

        onPreferenceRow(R.id.recycler_view, withKey("casting_passthrough"), not(isEnabled))!!
                .check(matches(isDisplayed()))
    }

    @Test
    fun checkAudioPassthroughSetting() {
        val key = "casting_passthrough"
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    fun checkCastingQualitySetting() {
        val key = "casting_quality"

        checkModeChanged(key, "0", "2", MAP_CASTING_QUALITY)
        checkModeChanged(key, "1", "2", MAP_CASTING_QUALITY)
        checkModeChanged(key, "2", "2", MAP_CASTING_QUALITY)
        checkModeChanged(key, "3", "2", MAP_CASTING_QUALITY)
    }

    companion object {
        val MAP_CASTING_QUALITY = mapOf(
                "0" to R.string.casting_quality_high, "1" to R.string.casting_quality_medium, "2" to R.string.casting_quality_low,
                "3" to R.string.casting_quality_lowcpu
        )
    }
}