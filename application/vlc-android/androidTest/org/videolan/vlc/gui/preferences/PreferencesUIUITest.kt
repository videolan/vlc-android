package org.videolan.vlc.gui.preferences

import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PreferenceMatchers.withKey
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.onPreferenceRow
import org.videolan.vlc.util.*

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class PreferencesUIUITest: BasePreferenceUITest() {
    @get:Rule
    val intentsTestRule = IntentsTestRule(PreferencesActivity::class.java)

    lateinit var activity: PreferencesActivity

    override fun beforeTest() {
        activity = intentsTestRule.activity

        onPreferenceRow(R.id.recycler_view, withKey("ui_category"))!!
                .perform(click())
    }

    @Test
    fun checkDayNightSetting_followSystemMode() {
        val key = KEY_APP_THEME
        checkModeChanged(key, "-1", "-1", MAP_DAYNIGHT_MODE)
    }

    @Test
    fun checkDayNightSetting_autoMode() {
        val key = KEY_APP_THEME
        checkModeChanged(key, "0", "-1", MAP_DAYNIGHT_MODE)
    }

    @Test
    fun checkDayNightSetting_lightMode() {
        val key = KEY_APP_THEME
        checkModeChanged(key, "1", "-1", MAP_DAYNIGHT_MODE)
    }

    @Test
    fun checkDayNightSetting_darkMode() {
        val key = KEY_APP_THEME
        checkModeChanged(key, "2", "-1", MAP_DAYNIGHT_MODE)
    }

    @Test
    fun checkAndroidTvSetting_appDueRestart() {
        val key = PREF_TV_UI

        checkToggleWorks(key, settings, default = false)

        intentsTestRule.finishActivity()
        assertThat(intentsTestRule.activityResult.resultCode, equalTo(RESULT_RESTART_APP))
    }

    @Test
    fun checkLocaleSetting() {
        val lp = LocaleUtils.getLocalesUsedInProject(context, BuildConfig.TRANSLATION_ARRAY, context.getString(R.string.device_default))
        val lpEntries = lp.localeEntries
        val lpValues = lp.localeEntryValues

        val MAP_LOCALE = lpValues.zip(lpEntries).toMap()

        val key = "set_locale"

        checkModeChanged(key, lpValues[3], "", MAP_LOCALE)
    }

    @Test
    fun checkShowAllFilesSetting_dueRestart() {
        val key = "browser_show_all_files"

        checkToggleWorks(key, settings)

        intentsTestRule.finishActivity()
        assertThat(intentsTestRule.activityResult.resultCode, equalTo(RESULT_RESTART))
    }

    @Test
    fun checkGroupVideosSetting() {
        val key = "video_min_group_length"

        checkModeChanged(key, "-1", "-1", MAP_GROUP_VIDEOS)
        checkModeChanged(key, "0", "-1", MAP_GROUP_VIDEOS)
    }

    @Test
    fun checkSeenVideoMarkerSetting_dueUpdateSeenMedia() {
        val key = "media_seen"

        checkToggleWorks(key, settings)

        intentsTestRule.finishActivity()
        assertThat(intentsTestRule.activityResult.resultCode, equalTo(RESULT_UPDATE_SEEN_MEDIA))
    }

    @Test
    fun checkVideoPlaylistMode() {
        val key = FORCE_PLAY_ALL

        checkToggleWorks(key, settings)
    }

    @Test
    fun checkShowVideoThumbnailSetting_dueRestart() {
        val key = SHOW_VIDEO_THUMBNAILS

        checkToggleWorks(key, settings)

        intentsTestRule.finishActivity()
        assertThat(intentsTestRule.activityResult.resultCode, equalTo(RESULT_RESTART))
    }

    @Test
    fun checkAudioCoverBlurredBackgroundSetting() {
        val key = "blurred_cover_background"

        checkToggleWorks(key, settings)
    }

    @Test
    fun checkLastAudioResumePlayHintSetting() {
        val key = "audio_resume_card"

        checkToggleWorks(key, settings)
    }

    @Test
    fun checkMediaCoverLockscreenSetting() {
        val key = "lockscreen_cover"

        checkToggleWorks(key, settings)
    }

    @Test
    fun checkShowAllArtistsSetting() {
        val key = KEY_ARTISTS_SHOW_ALL

        checkToggleWorks(key, settings)
    }

    companion object {
        val MAP_DAYNIGHT_MODE = mapOf("-1" to R.string.daynight_follow_system_title, "0" to R.string.daynight_title, "1" to R.string.light_theme, "2" to R.string.enable_black_theme)
        val MAP_GROUP_VIDEOS = mapOf("-1" to R.string.video_min_group_length_disable, "0" to R.string.video_min_group_length_folder)
    }
}