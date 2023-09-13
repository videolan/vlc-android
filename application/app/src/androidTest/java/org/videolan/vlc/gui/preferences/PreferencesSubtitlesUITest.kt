package org.videolan.vlc.gui.preferences

import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.rule.IntentsTestRule
import org.junit.Rule
import org.junit.Test
import org.videolan.vlc.PreferenceMatchers.withKey
import org.videolan.vlc.R
import org.videolan.vlc.onPreferenceRow

class PreferencesSubtitlesUITest: BasePreferenceUITest() {
    @get:Rule
    val intentsTestRule = IntentsTestRule(PreferencesActivity::class.java)

    lateinit var activity: PreferencesActivity

    override fun beforeTest() {
        activity = intentsTestRule.activity

        onPreferenceRow(R.id.recycler_view, withKey("subtitles_category"))!!
                .perform(click())
    }

    @Test
    fun checkAutoLoadSubtitleSetting() {
        val key = "subtitles_autoload"
        checkToggleWorks(key, settings)
    }

    @Test
    fun checkSubtitleSizeSetting() {
        val key = "subtitles_size"

        checkModeChanged(key, "19", "16", MAP_SUBTITLE_SIZE)
        checkModeChanged(key, "16", "16", MAP_SUBTITLE_SIZE)
        checkModeChanged(key, "13", "16", MAP_SUBTITLE_SIZE)
        checkModeChanged(key, "10", "16", MAP_SUBTITLE_SIZE)
    }


    @Test
    fun checkSubtitleBackgroundSetting() {
        val key = "subtitles_background"
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    fun checkBoldSubtitleSetting() {
        val key = "subtitles_bold"
        checkToggleWorks(key, settings, default = false)
    }

    @Test
    fun checkSubtitleEncodingSetting() {
        val key = "subtitle_text_encoding"

        checkModeChanged(key, "", "", MAP_SUBTITLE_ENCODING)
        checkModeChanged(key, "UTF-8", "", MAP_SUBTITLE_ENCODING)
    }

    companion object {
        val MAP_SUBTITLE_SIZE = mapOf("19" to R.string.subtitles_size_small, "16" to R.string.subtitles_size_normal, "13" to R.string.subtitles_size_big, "10" to R.string.subtitles_size_huge)
        val MAP_SUBTITLE_ENCODING = mapOf(
                "" to "Default (Windows-1252)", "UTF-8" to "Universal (UTF-8)"
        )
    }
}