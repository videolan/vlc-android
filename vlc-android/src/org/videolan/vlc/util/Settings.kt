package org.videolan.vlc.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.util.Settings.init


@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
object Settings : SingletonHolder<SharedPreferences, Context>({ PreferenceManager.getDefaultSharedPreferences(it).also { prefs -> init(prefs) } }) {

    var showVideoThumbs = true
    var tvUI = false
    var listTitleEllipsize = 0

    fun init(prefs: SharedPreferences) {
        showVideoThumbs = prefs.getBoolean(SHOW_VIDEO_THUMBNAILS, true)
        tvUI = prefs.getBoolean(PREF_TV_UI, false)
        listTitleEllipsize = prefs.getString(LIST_TITLE_ELLIPSIZE, "0").toInt()
    }

    val showTvUi : Boolean
        get() = AndroidDevices.isTv || tvUI

}

// Keys
const val KEY_ARTISTS_SHOW_ALL = "artists_show_all"
const val KEY_APP_THEME = "app_theme"
const val KEY_BLACK_THEME = "enable_black_theme"
const val KEY_DAYNIGHT = "daynight"
const val SHOW_VIDEO_THUMBNAILS = "show_video_thumbnails"
const val FORCE_LIST_PORTRAIT = "force_list_portrait"

//UI
const val LIST_TITLE_ELLIPSIZE = "list_title_ellipsize"


// AudioPlayer
const val PREF_PLAYLIST_TIPS_SHOWN = "playlist_tips_shown"
const val PREF_AUDIOPLAYER_TIPS_SHOWN = "audioplayer_tips_shown"
const val KEY_MEDIALIBRARY_SCAN = "ml_scan"
const val ML_SCAN_ON = 0
const val ML_SCAN_OFF = 1

const val PREF_TV_UI = "tv_ui"
const val FORCE_PLAY_ALL = "force_play_all"

const val SCREEN_ORIENTATION = "screen_orientation"
const val VIDEO_RESUME_TIME = "VideoResumeTime"
const val ENABLE_SEEK_BUTTONS = "enable_seek_buttons"
const val ENABLE_DOUBLE_TAP_SEEK = "enable_double_tap_seek"
const val ENABLE_VOLUME_GESTURE = "enable_volume_gesture"
const val ENABLE_BRIGHTNESS_GESTURE = "enable_brightness_gesture"
const val SAVE_BRIGHTNESS = "save_brightness"
const val BRIGHTNESS_VALUE = "brightness_value"
const val POPUP_KEEPSCREEN = "popup_keepscreen"
const val POPUP_FORCE_LEGACY = "popup_force_legacy"

const val VIDEO_PAUSED = "VideoPaused"
const val VIDEO_SPEED = "VideoSpeed"
const val VIDEO_RATIO = "video_ratio"
const val LOGIN_STORE = "store_login"
const val KEY_PLAYBACK_RATE = "playback_rate"
const val KEY_PLAYBACK_SPEED_PERSIST = "playback_speed"
const val KEY_VIDEO_APP_SWITCH = "video_action_switch"
const val RESULT_RESCAN = Activity.RESULT_FIRST_USER + 1
const val RESULT_RESTART = Activity.RESULT_FIRST_USER + 2
const val RESULT_RESTART_APP = Activity.RESULT_FIRST_USER + 3
const val RESULT_UPDATE_SEEN_MEDIA = Activity.RESULT_FIRST_USER + 4
const val RESULT_UPDATE_ARTISTS = Activity.RESULT_FIRST_USER + 5

const val BETA_WELCOME = "beta_welcome"
const val CRASH_DONT_ASK_AGAIN = "crash_dont_ask_again"

const val PLAYBACK_HISTORY = "playback_history"
const val RESUME_PLAYBACK = "resume_playback"
const val AUDIO_DUCKING = "audio_ducking"