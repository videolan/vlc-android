package org.videolan.tools

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import org.videolan.tools.Settings.audioControlsChangeListener
import org.videolan.tools.Settings.init
import org.videolan.tools.Settings.initPostMigration
import java.io.File

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
    var audioShowTrackNumbers = MutableLiveData(false)
    var showHiddenFiles = false
    var showTrackNumber = true
    var tvFoldersFirst = true
    var incognitoMode = false
    var safeMode = false
    var remoteAccessEnabled = MutableLiveData(false)
    var fastplaySpeed = 2f
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
        audioShowTrackNumbers.postValue(prefs.getBoolean(KEY_AUDIO_SHOW_TRACK_NUMBERS, false))
        showHiddenFiles = prefs.getBoolean(BROWSER_SHOW_HIDDEN_FILES, !tvUI)
        showTrackNumber = prefs.getBoolean(ALBUMS_SHOW_TRACK_NUMBER, true)
        tvFoldersFirst = prefs.getBoolean(TV_FOLDERS_FIRST, true)
        incognitoMode = prefs.getBoolean(KEY_INCOGNITO, false)
        safeMode = prefs.getBoolean(KEY_SAFE_MODE, false) && prefs.getString(KEY_SAFE_MODE_PIN, "")?.isNotBlank() == true
        remoteAccessEnabled.postValue(prefs.getBoolean(KEY_ENABLE_REMOTE_ACCESS, false))
        return prefs
    }

    /**
     * Init post migration: it can be useful when we migrate a preference by changing its type in [VersionMigration].
     * When doing so, [init] will be called before the migration is done, resulting in a [ClassCastException].
     * This method is called after the migration is done.
     * Once a preference has been moved from [init] to [initPostMigration], it should never be put back in [init].
     *
     * @param context the context
     */
    fun initPostMigration(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        fastplaySpeed = prefs.getInt(FASTPLAY_SPEED, 20) / 10f
    }

    fun Context.isPinCodeSet() = getInstance(this).getString(KEY_SAFE_MODE_PIN, "")?.isNotBlank() == true


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

    /**
     * Get the list of keys to blacklist for the backup/restore process
     *
     */
    fun getRestoreBlacklist() = arrayOf(
        //last playlist
        KEY_CURRENT_AUDIO, KEY_CURRENT_MEDIA, KEY_CURRENT_MEDIA_RESUME, KEY_AUDIO_LAST_PLAYLIST,
        KEY_MEDIA_LAST_PLAYLIST, KEY_MEDIA_LAST_PLAYLIST_RESUME, KEY_CURRENT_AUDIO_RESUME_THUMB,
        POSITION_IN_MEDIA_LIST, POSITION_IN_AUDIO_LIST, POSITION_IN_SONG, POSITION_IN_MEDIA,
        //Remote access
        KEYSTORE_PASSWORD_IV, KEY_COOKIE_ENCRYPT_KEY, KEY_COOKIE_SIGN_KEY, KEYSTORE_PASSWORD, ENCRYPTED_KEY_NAME,
        //Others
        KEY_NAVIGATOR_SCREEN_UNSTABLE, KEY_FRAGMENT_ID, KEY_DEBLOCKING, KEY_LAST_SESSION_CRASHED, KEY_METERED_CONNECTION, KEY_MEDIALIBRARY_SCAN

    )

    val showTvUi : Boolean
        get() = !overrideTvUI && device.isTv || tvUI
}

const val KEY_CURRENT_SETTINGS_VERSION = "current_settings_version"
const val KEY_CURRENT_SETTINGS_VERSION_AFTER_LIBVLC_INSTANTIATION = "current_settings_libvlc_version"
const val KEY_CURRENT_MAJOR_VERSION = "key_current_major_version"

// Keys
const val KEY_ARTISTS_SHOW_ALL = "artists_show_all"
const val KEY_SHOW_HEADERS = "show_headers"
const val KEY_APP_THEME = "app_theme"
const val KEY_BLACK_THEME = "enable_black_theme"
const val KEY_DAYNIGHT = "daynight"
const val SHOW_VIDEO_THUMBNAILS = "show_video_thumbnails"
const val KEY_VIDEO_CONFIRM_RESUME = "video_confirm_resume"
const val KEY_AUDIO_CONFIRM_RESUME = "audio_confirm_resume"
const val KEY_MEDIALIBRARY_AUTO_RESCAN = "auto_rescan"
const val KEY_TV_ONBOARDING_DONE = "key_tv_onboarding_done"
const val KEY_INCOGNITO = "incognito_mode"
const val KEY_LAST_WHATS_NEW = "last_whats_new"
const val KEY_SHOW_WHATS_NEW = "show_whats_new"
const val KEY_LAST_UPDATE_TIME = "last_update_time"
const val KEY_SHOW_UPDATE = "show_update"

// Playback settings category
const val KEY_AUDIO_LAST_PLAYLIST = "audio_list"
const val KEY_MEDIA_LAST_PLAYLIST = "media_list"
const val KEY_MEDIA_LAST_PLAYLIST_RESUME = "media_list_resume"
const val KEY_CURRENT_AUDIO = "current_song"
const val KEY_CURRENT_MEDIA = "current_media"
const val KEY_CURRENT_MEDIA_RESUME = "current_media_resume"
const val KEY_CURRENT_AUDIO_RESUME_TITLE = "key_current_audio_resume_title"
const val KEY_CURRENT_AUDIO_RESUME_ARTIST = "key_current_audio_resume_artist"
const val KEY_CURRENT_AUDIO_RESUME_THUMB = "key_current_audio_resume_thumb"
const val KEY_CURRENT_MEDIA_IS_AUDIO = "key_current_media_is_audio"

// AUDIO category
const val KEY_AUDIO_CURRENT_TAB = "key_audio_current_tab"
const val KEY_AUDIO_ALBUM_SONG_CURRENT_TAB = "key_audio_album_song_current_tab"

//UI
const val LIST_TITLE_ELLIPSIZE = "list_title_ellipsize"
const val KEY_VIDEO_JUMP_DELAY = "video_jump_delay"
const val KEY_VIDEO_LONG_JUMP_DELAY = "video_long_jump_delay"
const val KEY_VIDEO_DOUBLE_TAP_JUMP_DELAY = "video_double_tap_jump_delay"
const val KEY_AUDIO_JUMP_DELAY = "audio_jump_delay"
const val KEY_AUDIO_LONG_JUMP_DELAY = "audio_long_jump_delay"
const val KEY_AUDIO_FORCE_SHUFFLE = "audio_force_shuffle"
const val KEY_AUDIO_SHOW_TRACK_NUMBERS = "audio_show_track_numbers"
const val KEY_AUDIO_SHOW_CHAPTER_BUTTONS = "audio_show_chapter_buttons"
const val KEY_AUDIO_SHOW_BOOkMARK_BUTTONS = "audio_show_bookmark_buttons"
const val KEY_AUDIO_SHOW_BOOKMARK_MARKERS = "audio_show_bookmark_markers"
const val KEY_PERSISTENT_INCOGNITO = "persistent_incognito"
const val KEY_BROWSE_NETWORK = "browse_network"
const val KEY_VIDEOS_CARDS = "video_display_in_cards"
const val KEY_GROUP_VIDEOS = "video_min_group_length"
const val KEY_MAIN_TAB = "main_tab"
const val KEY_AUDIO_TAB = "audio_tab"
const val KEY_VIDEO_TAB = "video_tab"


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
const val KEY_REMOTE_ACCESS_LAST_STATE_STOPPED = "remote_access_last_state_stopped"
const val KEY_REMOTE_ACCESS_ML_CONTENT = "remote_access_medialibrary_content"
const val REMOTE_ACCESS_FILE_BROWSER_CONTENT = "remote_access_file_browser_content"
const val REMOTE_ACCESS_NETWORK_BROWSER_CONTENT = "remote_access_network_browser_content"
const val REMOTE_ACCESS_HISTORY_CONTENT = "remote_access_history_content"
const val REMOTE_ACCESS_PLAYBACK_CONTROL = "remote_access_playback_control"
const val REMOTE_ACCESS_LOGS = "remote_access_logs"
const val KEYSTORE_PASSWORD = "keystore_encrypted_password"
const val KEYSTORE_PASSWORD_IV = "keystore_encrypted_password_iv"
const val ENCRYPTED_KEY_NAME = "encryption_key"
const val KEY_COOKIE_ENCRYPT_KEY = "cookie_encrypt_key"
const val KEY_COOKIE_SIGN_KEY = "cookie_sign_key"
const val KEY_REMOTE_ACCESS_INFO = "remote_access_info"

//Equalizer
const val KEY_CURRENT_EQUALIZER_ID = "current_equalizer_id"
const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"


//Tips

const val PREF_TIPS_SHOWN = "video_player_tips_shown"
const val PREF_WIDGETS_TIPS_SHOWN = "widgets_tips_shown"
const val PREF_RESTORE_VIDEO_TIPS_SHOWN = "pref_restore_video_tips_shown"
const val PREF_SHOW_VIDEO_SETTINGS_DISCLAIMER = "pref_show_video_settings_disclaimer"

const val PREF_TV_UI = "tv_ui"
const val PLAYLIST_MODE_VIDEO = "playlist_mode_video"
const val PLAYLIST_MODE_AUDIO = "playlist_mode_audio"

const val SCREEN_ORIENTATION = "screen_orientation"
const val VIDEO_RESUME_TIME = "VideoResumeTime"
const val VIDEO_RESUME_URI = "VideoResumeUri"
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
const val ENABLE_FASTPLAY = "enable_fastplay"
const val FASTPLAY_SPEED = "fastplay_speed"
const val SAVE_BRIGHTNESS = "save_brightness"
const val BRIGHTNESS_VALUE = "brightness_value"
const val POPUP_KEEPSCREEN = "popup_keepscreen"
const val POPUP_FORCE_LEGACY = "popup_force_legacy"
const val SHOW_ORIENTATION_BUTTON = "show_orientation_button"
const val RESTORE_BACKGROUND_VIDEO = "restore_background_video"
const val LOCK_USE_SENSOR = "lock_use_sensor"
const val DISPLAY_UNDER_NOTCH = "display_under_notch"
const val ALLOW_FOLD_AUTO_LAYOUT = "allow_fold_auto_layout"
const val HINGE_ON_RIGHT = "hinge_on_right"
const val AUDIO_HINGE_ON_RIGHT = "audio_hinge_on_right"
const val TV_FOLDERS_FIRST = "tv_folders_first"
const val KEY_OBSOLETE_RESTORE_FILE_WARNED = "obsolete_restore_file_warned"

const val VIDEO_PAUSED = "VideoPaused"
const val VIDEO_SPEED = "VideoSpeed"
const val VIDEO_RATIO = "video_ratio"
const val LOGIN_STORE = "store_login"
const val KEY_PLAYBACK_SPEED_VIDEO_GLOBAL = "playback_speed_video_global"
const val KEY_PLAYBACK_SPEED_AUDIO_GLOBAL = "playback_speed_audio_global"
const val KEY_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE = "playback_speed_video_global_value"
const val KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE = "playback_speed_audio_global_value"
const val KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE = "incognito_playback_speed_video_global_value"
const val KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE = "incognito_playback_speed_audio_global_value"
const val KEY_VIDEO_APP_SWITCH = "video_action_switch"
const val VIDEO_TRANSITION_SHOW = "video_transition_show"
const val VIDEO_HUD_TIMEOUT = "video_hud_timeout_in_s"
const val RESULT_RESCAN = Activity.RESULT_FIRST_USER + 1
const val RESULT_RESTART = Activity.RESULT_FIRST_USER + 2
const val RESULT_RESTART_APP = Activity.RESULT_FIRST_USER + 3
const val RESULT_UPDATE_SEEN_MEDIA = Activity.RESULT_FIRST_USER + 4
const val RESULT_UPDATE_ARTISTS = Activity.RESULT_FIRST_USER + 5

const val BETA_WELCOME = "beta_welcome"

const val PLAYBACK_HISTORY = "playback_history"
const val AUDIO_RESUME_PLAYBACK = "audio_resume_playback"
const val VIDEO_RESUME_PLAYBACK = "video_resume_playback"
const val RESUME_PLAYBACK = "resume_playback"
const val AUDIO_DUCKING = "audio_ducking"

const val AUDIO_DELAY_GLOBAL = "audio_delay_global"
const val AUDIO_PLAY_PROGRESS_MODE = "audio_play_progress_mode"
const val AUDIO_STOP_AFTER = "audio_stop_after"

const val LAST_LOCK_ORIENTATION = "last_lock_orientation"
const val INITIAL_PERMISSION_ASKED = "initial_permission_asked"
const val PERMISSION_NEVER_ASK = "permission_never_ask"
const val PERMISSION_NEXT_ASK = "permission_next_ask"

const val WIDGETS_BACKGROUND_LAST_COLORS = "widgets_background_last_colors"
const val WIDGETS_FOREGROUND_LAST_COLORS = "widgets_foreground_last_colors"
const val CUSTOM_POPUP_HEIGHT = "custom_popup_height"

const val SLEEP_TIMER_DEFAULT_INTERVAL = "sleep_timer_default_interval"
const val SLEEP_TIMER_DEFAULT_WAIT = "sleep_timer_default_wait"
const val SLEEP_TIMER_DEFAULT_RESET_INTERACTION = "sleep_timer_default_reset_interaction"
const val SLEEP_TIMER_WAIT = "sleep_timer_wait"
const val SLEEP_TIMER_RESET_INTERACTION = "sleep_timer_reset_interaction"

const val NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
const val PLAYLIST_REPLACE = "playlist_replace"
const val HTTP_USER_AGENT = "http_user_agent"
const val DAV1D_THREAD_NUMBER = "dav1d_thread_number"
const val KEY_QUICK_PLAY = "quick_play"
const val KEY_QUICK_PLAY_DEFAULT = "quick_play_default"
const val KEY_AOUT = "aout"


const val KEY_HARDWARE_ACCELERATION = "hardware_acceleration"
const val KEY_ALWAYS_FAST_SEEK = "always_fast_seek"
const val KEY_AUDIO_PLAYER_SHOW_COVER = "audio_player_show_cover"
const val KEY_ENABLE_CLONE_MODE = "enable_clone_mode"
const val KEY_USER_DECLINED_STORAGE_ACCESS = "user_declined_storage_access"
const val KEY_METERED_CONNECTION = "metered_connection"

//Widgets
const val KEY_WIDGET_THEME = "widget_theme"
const val KEY_OPACITY = "opacity"
const val KEY_BACKGROUND_COLOR = "background_color"
const val KEY_FOREGROUND_COLOR = "foreground_color"


//TV
const val KEY_MEDIA_SEEN = "media_seen"

//Audio
const val KEY_IGNORE_HEADSET_MEDIA_BUTTON_PRESSES = "ignore_headset_media_button_presses"
const val KEY_ENABLE_HEADSET_DETECTION = "enable_headset_detection"
const val KEY_ENABLE_PLAY_ON_HEADSET_INSERTION = "enable_play_on_headset_insertion"
const val KEY_AUDIO_TASK_REMOVED = "audio_task_removed"
const val KEY_AUDIO_BOOST = "audio_boost"
const val KEY_SAVE_INDIVIDUAL_AUDIO_DELAY = "save_individual_audio_delay"
const val KEY_AUDIO_RESUME_CARD = "audio_resume_card"
const val KEY_AUDIO_PREFERRED_LANGUAGE = "audio_preferred_language"

//Video
const val KEY_VIDEO_MATCH_FRAME_RATE = "video_match_frame_rate"

//Subtitles
const val KEY_SUBTITLE_PREFERRED_LANGUAGE = "subtitle_preferred_language"


//UI
const val KEY_SET_LOCALE = "set_locale"
const val KEY_INCLUDE_MISSING = "include_missing"

//files
const val BROWSER_SHOW_HIDDEN_FILES = "browser_show_hidden_files"
const val BROWSER_SHOW_ONLY_MULTIMEDIA = "browser_show_only_multimedia"
const val BROWSER_DISPLAY_IN_CARDS = "browser_display_in_cards"

// Albums
const val ALBUMS_SHOW_TRACK_NUMBER = "albums_show_track_number"

//widgets
const val WIDGETS_PREVIEW_PLAYING = "widgets_preview_playing"

//OpenSubtitles
const val KEY_OPEN_SUBTITLES_USER = "open_subtitles_user"
const val KEY_OPEN_SUBTITLES_LIMIT = "open_subtitles_limit"


const val KEY_SAFE_MODE_PIN = "safe_mode_pin"
const val KEY_RESTRICT_SETTINGS = "restrict_settings"
const val KEY_SAFE_MODE = "safe_mode"


const val KEY_LAST_SESSION_CRASHED = "last_session_crashed"

const val ENABLE_ANDROID_AUTO_SPEED_BUTTONS = "enable_android_auto_speed_buttons"
const val ENABLE_ANDROID_AUTO_SEEK_BUTTONS = "enable_android_auto_seek_buttons"

//VLC options
const val KEY_CUSTOM_LIBVLC_OPTIONS = "custom_libvlc_options"
const val KEY_SUBTITLES_COLOR = "subtitles_color"
const val KEY_AUDIO_DIGITAL_OUTPUT = "audio_digital_output"
const val KEY_ENABLE_TIME_STRETCHING_AUDIO = "enable_time_stretching_audio"
const val KEY_SUBTITLE_TEXT_ENCODING = "subtitle_text_encoding"
const val KEY_ENABLE_FRAME_SKIP = "enable_frame_skip"
const val KEY_ENABLE_VERBOSE_MODE = "enable_verbose_mode"
const val KEY_ENABLE_CASTING = "enable_casting"
const val KEY_CASTING_AUDIO_ONLY = "casting_audio_only"
const val KEY_CASTING_PASSTHROUGH = "casting_passthrough"
const val KEY_CASTING_QUALITY = "casting_quality"
const val KEY_DEBLOCKING = "deblocking"
const val KEY_NETWORK_CACHING_VALUE = "network_caching_value"
const val KEY_SUBTITLES_SIZE = "subtitles_size"
const val KEY_SUBTITLES_BOLD = "subtitles_bold"
const val KEY_SUBTITLES_BACKGROUND_COLOR = "subtitles_background_color"
const val KEY_SUBTITLES_COLOR_OPACITY = "subtitles_color_opacity"
const val KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY = "subtitles_background_color_opacity"
const val KEY_SUBTITLES_BACKGROUND = "subtitles_background"
const val KEY_SUBTITLES_OUTLINE = "subtitles_outline"
const val KEY_SUBTITLES_OUTLINE_SIZE = "subtitles_outline_size"
const val KEY_SUBTITLES_OUTLINE_COLOR = "subtitles_outline_color"
const val KEY_SUBTITLES_OUTLINE_COLOR_OPACITY = "subtitles_outline_color_opacity"
const val KEY_SUBTITLES_SHADOW = "subtitles_shadow"
const val KEY_SUBTITLES_SHADOW_COLOR = "subtitles_shadow_color"
const val KEY_SUBTITLES_SHADOW_COLOR_OPACITY = "subtitles_shadow_color_opacity"
const val KEY_SUBTITLES_AUTOLOAD = "subtitles_autoload"
const val KEY_OPENGL = "opengl"

//Control settings
const val KEY_BLURRED_COVER_BACKGROUND = "blurred_cover_background"


//Advanced
const val KEY_PREFER_SMBV1 = "prefer_smbv1"
const val KEY_AUDIO_REPLAY_GAIN_ENABLE = "audio-replay-gain-enable"
const val KEY_AUDIO_REPLAY_GAIN_PEAK_PROTECTION = "audio-replay-gain-peak-protection"
const val KEY_AUDIO_REPLAY_GAIN_MODE = "audio-replay-gain-mode"
const val KEY_AUDIO_REPLAY_GAIN_DEFAULT = "audio-replay-gain-default"
const val KEY_AUDIO_REPLAY_GAIN_PREAMP = "audio-replay-gain-preamp"
const val KEY_PREFERRED_RESOLUTION = "preferred_resolution"

//Auto
const val KEY_ANDROID_AUTO_QUEUE_FORMAT_VAL = "android_auto_queue_format_val"
const val KEY_ANDROID_AUTO_QUEUE_INFO_POS_VAL = "android_auto_queue_info_pos_val"
const val KEY_ANDROID_AUTO_TITLE_SCALE_VAL = "android_auto_title_scale_val"
const val KEY_ANDROID_AUTO_SUBTITLE_SCALE_VAL = "android_auto_subtitle_scale_val"



//To exclude
const val KEY_NAVIGATOR_SCREEN_UNSTABLE = "navigator_screen_unstable"
const val KEY_FRAGMENT_ID = "fragment_id"


class DeviceInfo(context: Context) {
    val pm = context.packageManager
    val hasTsp = pm.hasSystemFeature("android.hardware.touchscreen")
    val isAndroidTv = pm.hasSystemFeature("android.software.leanback")
    val isChromeBook = pm.hasSystemFeature("org.chromium.arc.device_management")
    val isTv = isAndroidTv || !isChromeBook && !hasTsp
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
        is Set<*> -> edit { putStringSet(key, value.toSet() as Set<String>) }
        else -> throw IllegalArgumentException("value $value class is invalid!")
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

fun deleteSharedPreferences(context: Context, name: String): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return context.deleteSharedPreferences(name)
    } else {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        return File(dir, "$name.xml").delete()
    }
}