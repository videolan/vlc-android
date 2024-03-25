package org.videolan.tools

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import org.videolan.tools.Settings.init

object Settings : SingletonHolder<SharedPreferences, Context>({ init(it.applicationContext) }) {

    var firstRun: Boolean = false
    var showVideoThumbs = true
    var tvUI = false
    var listTitleEllipsize = 0
    var overrideTvUI = false
    var videoHudDelay = 2
    var includeMissing = true
    var showHeaders = true
    var showAudioTrackInfo = false
    var videoJumpDelay = 10
    var videoLongJumpDelay = 20
    var videoDoubleTapJumpDelay = 10
    var audioJumpDelay = 10
    var audioLongJumpDelay = 20
    var showHiddenFiles = false
    var showTrackNumber = true
    var tvFoldersFirst = true
    var incognitoMode = false
    var safeMode = false
    var remoteAccessEnabled = MutableLiveData(false)
    private var audioControlsChangeListener: (() -> Unit)? = null
    lateinit var device : DeviceInfo
        private set

    fun init(context: Context) : SharedPreferences{
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        showVideoThumbs = prefs.getBoolean(SHOW_VIDEO_THUMBNAILS, true)
        tvUI = prefs.getBoolean(PREF_TV_UI, false)
        listTitleEllipsize = prefs.getString(LIST_TITLE_ELLIPSIZE, "0")?.toInt() ?: 0
        videoHudDelay = prefs.getInt(VIDEO_HUD_TIMEOUT, 4).coerceInOrDefault(1, 15, -1)
        device = DeviceInfo(context)
        includeMissing = prefs.getBoolean(KEY_INCLUDE_MISSING, true)
        showHeaders = prefs.getBoolean(KEY_SHOW_HEADERS, true)
        showAudioTrackInfo = prefs.getBoolean(KEY_SHOW_TRACK_INFO, false)
        videoJumpDelay = prefs.getInt(KEY_VIDEO_JUMP_DELAY, 10)
        videoLongJumpDelay = prefs.getInt(KEY_VIDEO_LONG_JUMP_DELAY, 20)
        videoDoubleTapJumpDelay = prefs.getInt(KEY_VIDEO_DOUBLE_TAP_JUMP_DELAY, 10)
        audioJumpDelay = prefs.getInt(KEY_AUDIO_JUMP_DELAY, 10)
        audioLongJumpDelay = prefs.getInt(KEY_AUDIO_LONG_JUMP_DELAY, 20)
        showHiddenFiles = prefs.getBoolean(BROWSER_SHOW_HIDDEN_FILES, !tvUI)
        showTrackNumber = prefs.getBoolean(ALBUMS_SHOW_TRACK_NUMBER, true)
        tvFoldersFirst = prefs.getBoolean(TV_FOLDERS_FIRST, true)
        incognitoMode = prefs.getBoolean(KEY_INCOGNITO, false)
        safeMode = prefs.getBoolean(KEY_SAFE_MODE, false) && prefs.getString(KEY_SAFE_MODE_PIN, "")?.isNotBlank() == true
        remoteAccessEnabled.postValue(prefs.getBoolean(KEY_ENABLE_REMOTE_ACCESS, false))
        return prefs
    }

    fun Context.isPinCodeSet() = Settings.getInstance(this).getString(KEY_SAFE_MODE_PIN, "")?.isNotBlank() == true


    /**
     * Trigger the [audioControlsChangeListener] to update the UI
     */
    fun onAudioControlsChanged() {
        audioControlsChangeListener?.invoke()
    }

    fun setAudioControlsChangeListener(listener:() -> Unit) {
        audioControlsChangeListener = listener
    }

    fun removeAudioControlsChangeListener() {
        audioControlsChangeListener = null
    }

    val showTvUi : Boolean
        get() = !overrideTvUI && device.isTv || tvUI
}

const val KEY_CURRENT_SETTINGS_VERSION = "current_settings_version"
const val KEY_CURRENT_MAJOR_VERSION = "key_current_major_version"

// Keys
const val KEY_ARTISTS_SHOW_ALL = "artists_show_all"
const val KEY_SHOW_HEADERS = "show_headers"
const val KEY_APP_THEME = "app_theme"
const val KEY_BLACK_THEME = "enable_black_theme"
const val KEY_DAYNIGHT = "daynight"
const val SHOW_VIDEO_THUMBNAILS = "show_video_thumbnails"
const val KEY_VIDEO_CONFIRM_RESUME = "video_confirm_resume"
const val KEY_MEDIALIBRARY_AUTO_RESCAN = "auto_rescan"
const val KEY_TV_ONBOARDING_DONE = "key_tv_onboarding_done"
const val KEY_INCLUDE_MISSING = "include_missing"
const val KEY_INCOGNITO = "incognito_mode"
const val KEY_LAST_WHATS_NEW = "last_whats_new"
const val KEY_SHOW_WHATS_NEW = "show_whats_new"

//UI
const val LIST_TITLE_ELLIPSIZE = "list_title_ellipsize"
const val KEY_VIDEO_JUMP_DELAY = "video_jump_delay"
const val KEY_VIDEO_LONG_JUMP_DELAY = "video_long_jump_delay"
const val KEY_VIDEO_DOUBLE_TAP_JUMP_DELAY = "video_double_tap_jump_delay"
const val KEY_AUDIO_JUMP_DELAY = "audio_jump_delay"
const val KEY_AUDIO_LONG_JUMP_DELAY = "audio_long_jump_delay"
const val KEY_AUDIO_FORCE_SHUFFLE = "audio_force_shuffle"


// AudioPlayer
const val AUDIO_SHUFFLING = "audio_shuffling"
const val MEDIA_SHUFFLING = "media_shuffling"
const val POSITION_IN_SONG = "position_in_song"
const val POSITION_IN_MEDIA = "position_in_media"
const val POSITION_IN_AUDIO_LIST = "position_in_audio_list"
const val POSITION_IN_MEDIA_LIST = "position_in_media_list"
const val SHOW_REMAINING_TIME = "show_remaining_time"
const val PREF_PLAYLIST_TIPS_SHOWN = "playlist_tips_shown"
const val PREF_AUDIOPLAYER_TIPS_SHOWN = "audioplayer_tips_shown"
const val KEY_MEDIALIBRARY_SCAN = "ml_scan"
const val KEY_SHOW_TRACK_INFO = "show_track_info"
const val ML_SCAN_ON = 0
const val ML_SCAN_OFF = 1

//Remote access
const val KEY_ENABLE_REMOTE_ACCESS = "enable_remote_access"
const val KEY_REMOTE_ACCESS_ML_CONTENT = "remote_access_medialibrary_content"
const val REMOTE_ACCESS_FILE_BROWSER_CONTENT = "remote_access_file_browser_content"
const val REMOTE_ACCESS_NETWORK_BROWSER_CONTENT = "remote_access_network_browser_content"
const val REMOTE_ACCESS_PLAYBACK_CONTROL = "remote_access_playback_control"
const val REMOTE_ACCESS_LOGS = "remote_access_logs"
const val KEYSTORE_PASSWORD = "keystore_encrypted_password"
const val KEYSTORE_PASSWORD_IV = "keystore_encrypted_password_iv"
const val ENCRYPTED_KEY_NAME = "encryption_key"


//Tips

const val PREF_TIPS_SHOWN = "video_player_tips_shown"
const val PREF_WIDGETS_TIPS_SHOWN = "widgets_tips_shown"
const val PREF_RESTORE_VIDEO_TIPS_SHOWN = "pref_restore_video_tips_shown"

const val PREF_TV_UI = "tv_ui"
const val FORCE_PLAY_ALL_VIDEO = "force_play_all_video"
const val FORCE_PLAY_ALL_AUDIO = "force_play_all_audio"

const val SCREEN_ORIENTATION = "screen_orientation"
const val VIDEO_RESUME_TIME = "VideoResumeTime"
const val VIDEO_RESUME_URI = "VideoResumeUri"
const val AUDIO_BOOST = "audio_boost"
const val ENABLE_SEEK_BUTTONS = "enable_seek_buttons"
const val SHOW_SEEK_IN_COMPACT_NOTIFICATION = "show_seek_in_compact_notification"
const val LOCKSCREEN_COVER = "lockscreen_cover"
const val ENABLE_DOUBLE_TAP_SEEK = "enable_double_tap_seek"
const val ENABLE_SWIPE_SEEK = "enable_swipe_seek"
const val ENABLE_DOUBLE_TAP_PLAY = "enable_double_tap_play"
const val ENABLE_VOLUME_GESTURE = "enable_volume_gesture"
const val ENABLE_BRIGHTNESS_GESTURE = "enable_brightness_gesture"
const val SCREENSHOT_MODE = "screenshot_mode"
const val ENABLE_SCALE_GESTURE = "enable_scale_gesture"
const val SAVE_BRIGHTNESS = "save_brightness"
const val BRIGHTNESS_VALUE = "brightness_value"
const val POPUP_KEEPSCREEN = "popup_keepscreen"
const val POPUP_FORCE_LEGACY = "popup_force_legacy"
const val RESTORE_BACKGROUND_VIDEO = "restore_background_video"
const val LOCK_USE_SENSOR = "lock_use_sensor"
const val DISPLAY_UNDER_NOTCH = "display_under_notch"
const val ALLOW_FOLD_AUTO_LAYOUT = "allow_fold_auto_layout"
const val HINGE_ON_RIGHT = "hinge_on_right"
const val AUDIO_HINGE_ON_RIGHT = "audio_hinge_on_right"
const val TV_FOLDERS_FIRST = "tv_folders_first"

const val VIDEO_PAUSED = "VideoPaused"
const val VIDEO_SPEED = "VideoSpeed"
const val VIDEO_RATIO = "video_ratio"
const val LOGIN_STORE = "store_login"
const val KEY_PLAYBACK_RATE = "playback_rate"
const val KEY_PLAYBACK_RATE_VIDEO = "playback_rate_video"
const val KEY_PLAYBACK_SPEED_PERSIST = "playback_speed"
const val KEY_PLAYBACK_SPEED_PERSIST_VIDEO = "playback_speed_video"
const val KEY_VIDEO_APP_SWITCH = "video_action_switch"
const val VIDEO_TRANSITION_SHOW = "video_transition_show"
const val VIDEO_HUD_TIMEOUT = "video_hud_timeout_in_s"
const val RESULT_RESCAN = Activity.RESULT_FIRST_USER + 1
const val RESULT_RESTART = Activity.RESULT_FIRST_USER + 2
const val RESULT_RESTART_APP = Activity.RESULT_FIRST_USER + 3
const val RESULT_UPDATE_SEEN_MEDIA = Activity.RESULT_FIRST_USER + 4
const val RESULT_UPDATE_ARTISTS = Activity.RESULT_FIRST_USER + 5

const val BETA_WELCOME = "beta_welcome"
const val CRASH_DONT_ASK_AGAIN = "crash_dont_ask_again"

const val PLAYBACK_HISTORY = "playback_history"
const val AUDIO_RESUME_PLAYBACK = "audio_resume_playback"
const val VIDEO_RESUME_PLAYBACK = "video_resume_playback"
const val RESUME_PLAYBACK = "resume_playback"
const val AUDIO_DUCKING = "audio_ducking"

const val AUDIO_DELAY_GLOBAL = "audio_delay_global"
const val AUDIO_PLAY_PROGRESS_MODE = "audio_play_progress_mode"
const val AUDIO_STOP_AFTER = "audio_stop_after"
const val AUDIO_PREFERRED_LANGUAGE = "audio_preferred_language"
const val SUBTITLE_PREFERRED_LANGUAGE = "subtitle_preferred_language"

const val LAST_LOCK_ORIENTATION = "last_lock_orientation"
const val INITIAL_PERMISSION_ASKED = "initial_permission_asked"
const val PERMISSION_NEVER_ASK = "permission_never_ask"
const val PERMISSION_NEXT_ASK = "permission_next_ask"

const val WIDGETS_BACKGROUND_LAST_COLORS = "widgets_background_last_colors"
const val WIDGETS_FOREGROUND_LAST_COLORS = "widgets_foreground_last_colors"
const val CUSTOM_POPUP_HEIGHT = "custom_popup_height"

const val SLEEP_TIMER_WAIT = "sleep_timer_wait"

const val NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
const val PLAYLIST_REPLACE = "playlist_replace"
const val HTTP_USER_AGENT = "http_user_agent"

//files
const val BROWSER_SHOW_HIDDEN_FILES = "browser_show_hidden_files"
const val BROWSER_SHOW_ONLY_MULTIMEDIA = "browser_show_only_multimedia"
const val BROWSER_DISPLAY_IN_CARDS = "browser_display_in_cards"

// Albums
const val ALBUMS_SHOW_TRACK_NUMBER = "albums_show_track_number"

//widgets
const val WIDGETS_PREVIEW_PLAYING = "widgets_preview_playing"

const val KEY_SAFE_MODE_PIN = "safe_mode_pin"
const val KEY_RESTRICT_SETTINGS = "restrict_settings"
const val KEY_SAFE_MODE = "safe_mode"

const val ENABLE_ANDROID_AUTO_SPEED_BUTTONS = "enable_android_auto_speed_buttons"
const val ENABLE_ANDROID_AUTO_SEEK_BUTTONS = "enable_android_auto_seek_buttons"

class DeviceInfo(context: Context) {
    val pm = context.packageManager
    val tm = context.getSystemService<TelephonyManager>()!!
    val isPhone = tm.phoneType != TelephonyManager.PHONE_TYPE_NONE
    val hasTsp = pm.hasSystemFeature("android.hardware.touchscreen")
    val isAndroidTv = pm.hasSystemFeature("android.software.leanback")
    val watchDevices = isAndroidTv && Build.MODEL.startsWith("Bouygtel")
    val isChromeBook = pm.hasSystemFeature("org.chromium.arc.device_management")
    val isTv = isAndroidTv || !isChromeBook && !hasTsp
    val isAmazon = "Amazon" == Build.MANUFACTURER
    val hasPiP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pm.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            || Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isAndroidTv
    val pipAllowed = hasPiP || hasTsp && Build.VERSION.SDK_INT < Build.VERSION_CODES.O
}

@Suppress("UNCHECKED_CAST")
fun SharedPreferences.putSingle(key: String, value: Any) {
    when(value) {
        is Boolean -> edit { putBoolean(key, value) }
        is Int -> edit { putInt(key, value) }
        is Float -> edit { putFloat(key, value) }
        is Long -> edit { putLong(key, value) }
        is String -> edit { putString(key, value) }
        is List<*> -> edit { putStringSet(key, value.toSet() as Set<String>) }
        else -> throw IllegalArgumentException("value class is invalid!")
    }
}

/**
 * Force an [Int] to be in a reange else set it to a default value
 *
 * @param min the minimum value to accept
 * @param max the maximum value to accept
 * @param defautValue the default value to return if it's not in the range
 * @return an [Int] in the range
 */
fun Int.coerceInOrDefault(min: Int, max: Int, defautValue: Int) = if (this < min || this > max) defautValue else this