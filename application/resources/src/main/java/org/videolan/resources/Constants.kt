/*****************************************************************************
 * Constants.kt
 *
 * Copyright © 2018 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 */

@file:JvmName("Constants")
package org.videolan.resources

// StartActivity
const val PREF_FIRST_RUN = "first_run"
const val EXTRA_FIRST_RUN = "extra_first_run"
const val EXTRA_UPGRADE = "extra_upgrade"
const val EXTRA_PARSE = "extra_parse"
const val EXTRA_TARGET = "extra_parse"
const val EXTRA_FOR_ESPRESSO = "extra_for_espresso"
const val EXTRA_REMOVE_DEVICE = "extra_remove_device"
const val EXTRA_PLAY_ONLY = "extra_play_only"

//UI Navigation
const val ID_VIDEO = "video"
const val ID_VIDEO_FOLDERS = "video_folders"
const val ID_AUDIO = "audio"
const val ID_PLAYLISTS = "playlists"
const val ID_NETWORK = "network"
const val ID_DIRECTORIES = "directories"
const val ID_HISTORY = "history"
const val ID_MRL = "mrl"
const val ID_PREFERENCES = "preferences"

const val ACTIVITY_RESULT_PREFERENCES = 1
const val ACTIVITY_RESULT_OPEN = 2
const val ACTIVITY_RESULT_SECONDARY = 3

// PlaybackService
@JvmField
val ACTION_REMOTE_GENERIC = "remote.".buildPkgString()
@JvmField val EXTRA_SEARCH_BUNDLE = "${ACTION_REMOTE_GENERIC}extra_search_bundle"
@JvmField val ACTION_PLAY_FROM_SEARCH = "${ACTION_REMOTE_GENERIC}play_from_search"
@JvmField val ACTION_REMOTE_SWITCH_VIDEO = "${ACTION_REMOTE_GENERIC}SwitchToVideo"
@JvmField val ACTION_REMOTE_LAST_PLAYLIST = "${ACTION_REMOTE_GENERIC}LastPlaylist"
@JvmField val ACTION_REMOTE_FORWARD = "${ACTION_REMOTE_GENERIC}Forward"
@JvmField val ACTION_REMOTE_STOP = "${ACTION_REMOTE_GENERIC}Stop"
@JvmField val ACTION_REMOTE_PLAYPAUSE = "${ACTION_REMOTE_GENERIC}PlayPause"
@JvmField val ACTION_REMOTE_PLAY = "${ACTION_REMOTE_GENERIC}Play"
@JvmField val ACTION_REMOTE_BACKWARD = "${ACTION_REMOTE_GENERIC}Backward"
@JvmField val ACTION_REMOTE_SEEK_FORWARD = "${ACTION_REMOTE_GENERIC}SeekForward"
@JvmField val ACTION_REMOTE_SEEK_BACKWARD = "${ACTION_REMOTE_GENERIC}SeekBackward"
@JvmField val CUSTOM_ACTION = "CustomAction".buildPkgString()
@JvmField val CUSTOM_ACTION_BOOKMARK = "bookmark".buildPkgString()
@JvmField val CUSTOM_ACTION_FAST_FORWARD = "fast_forward".buildPkgString()
@JvmField val CUSTOM_ACTION_SHUFFLE = "shuffle".buildPkgString()
@JvmField val CUSTOM_ACTION_SPEED = "speed".buildPkgString()
@JvmField val CUSTOM_ACTION_REPEAT = "repeat".buildPkgString()
@JvmField val CUSTOM_ACTION_REWIND = "rewind".buildPkgString()
@JvmField val EXTRA_CUSTOM_ACTION_ID = "EXTRA_CUSTOM_ACTION_ID".buildPkgString()
@JvmField val EXTRA_SEEK_DELAY = "EXTRA_CUSTOM_ACTION_ID".buildPkgString()
@JvmField val EXTRA_RELATIVE_MEDIA_ID = "EXTRA_RELATIVE_MEDIA_ID".buildPkgString()
const val PLAYLIST_TYPE_AUDIO = 0
const val PLAYLIST_TYPE_VIDEO = 1
const val PLAYLIST_TYPE_ALL = 2
const val MEDIALIBRARY_PAGE_SIZE = 500
const val QUICK_SEARCH_BOX_APP_PKG = "com.google.android.googlequicksearchbox"
const val ANDROID_AUTO_APP_PKG = "com.google.android.projection.gearhead"
const val EXTRA_BROWSER_ICON_SIZE = "com.google.android.gms.car.media.BrowserIconSize"
const val WEARABLE_SHOW_CUSTOM_ACTION = "android.support.wearable.media.extra.CUSTOM_ACTION_SHOW_ON_WEAR"
const val WEARABLE_RESERVE_SLOT_SKIP_TO_NEXT = "android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_NEXT"
const val WEARABLE_RESERVE_SLOT_SKIP_TO_PREV = "android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_PREVIOUS"
const val PLAYBACK_SLOT_RESERVATION_SKIP_TO_NEXT = "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT"
const val PLAYBACK_SLOT_RESERVATION_SKIP_TO_PREV = "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS"
const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
const val EXTRA_CONTENT_STYLE_GROUP_TITLE_HINT = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
const val EXTRA_CONTENT_STYLE_SINGLE_ITEM = "android.media.browse.CONTENT_STYLE_SINGLE_ITEM_HINT"
const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
const val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2
const val CONTENT_STYLE_CATEGORY_ITEM_HINT_VALUE = 3

// MediaParsingService
const val ACTION_INIT = "medialibrary_init"
const val ACTION_RELOAD = "medialibrary_reload"
const val ACTION_FORCE_RELOAD = "medialibrary_force_reload"
const val ACTION_DISCOVER = "medialibrary_discover"
const val ACTION_DISCOVER_DEVICE = "medialibrary_discover_device"
const val ACTION_CHECK_STORAGES = "medialibrary_check_storages"
const val EXTRA_PATH = "extra_path"
const val EXTRA_UUID = "extra_uuid"
const val ACTION_RESUME_SCAN = "action_resume_scan"
const val ACTION_PAUSE_SCAN = "action_pause_scan"
const val ACTION_STOP_SERVER = "action_stop_server"
const val ACTION_START_SERVER = "action_start_server"
const val ACTION_DISABLE_SERVER = "action_disable_server"
const val ACTION_RESTART_SERVER = "action_restart_server"
const val ACTION_CONTENT_INDEXING = "action_content_indexing"

// VideoPlayerActivity
@JvmField val PLAY_FROM_VIDEOGRID = "gui.video.PLAY_FROM_VIDEOGRID".buildPkgString()
@JvmField val PLAY_FROM_SERVICE = "gui.video.PLAY_FROM_SERVICE".buildPkgString()
@JvmField val EXIT_PLAYER = "gui.video.EXIT_PLAYER".buildPkgString()
const val PLAY_EXTRA_ITEM_LOCATION = "item_location"
const val PLAY_EXTRA_SUBTITLES_LOCATION = "subtitles_location"
const val PLAY_EXTRA_ITEM_TITLE = "title"
const val PLAY_EXTRA_FROM_START = "from_start"
const val PLAY_EXTRA_START_TIME = "position"
const val PLAY_EXTRA_OPENED_POSITION = "opened_position"
const val PLAY_DISABLE_HARDWARE = "disable_hardware"

// MRLPanelFragment
const val KEY_MRL = "mrl"

// Info Activity
const val TAG_ITEM = "ML_ITEM"

//TV
const val HEADER_VIDEO = 0L
const val HEADER_CATEGORIES = 1L
const val HEADER_HISTORY = 2L
const val HEADER_NETWORK = 3L
const val HEADER_DIRECTORIES = 4L
const val HEADER_MISC = 5L
const val HEADER_STREAM = 6L
const val HEADER_SERVER = 7L
const val HEADER_PLAYLISTS = 8L
const val HEADER_MOVIES = 30L
const val HEADER_TV_SHOW = 31L
const val HEADER_RECENTLY_PLAYED = 32L
const val HEADER_RECENTLY_ADDED = 33L
const val HEADER_NOW_PLAYING = 34L
const val HEADER_PERMISSION = 35L
const val HEADER_FAVORITES = 36L
const val HEADER_ADD_STREAM = 37L
const val ID_SETTINGS = 10L
const val ID_ABOUT_TV = 11L
const val ID_REFRESH = 13L
const val ID_ALL_MOVIES = 14L
const val ID_ALL_TVSHOWS = 15L
const val ID_SPONSOR = 16L
const val ID_PIN_LOCK = 17L
const val ID_REMOTE_ACCESS = 18L
const val ID_NEW_UI = 19L
const val CATEGORY_NOW_PLAYING = 20L
const val CATEGORY_NOW_PLAYING_PAUSED = 28L
const val CATEGORY_ARTISTS = 21L
const val CATEGORY_ALBUMS = 22L
const val CATEGORY_GENRES = 23L
const val CATEGORY_SONGS = 24L
const val CATEGORY_VIDEOS = 25L
const val CATEGORY_PLAYLISTS = 27L
const val CATEGORY_NOW_PLAYING_PIP = 26L
const val CATEGORY_NOW_PLAYING_PIP_PAUSED = 29L

const val CATEGORY = "category"
const val DRAWABLE = "drawable"
const val ITEM = "item"
const val KEY_GROUP = "key_group"
const val KEY_FOLDER = "key_folder"
const val KEY_GROUPING = "key_grouping"
const val KEY_ANIMATED = "key_animated"
const val FAVORITE_TITLE = "favorite_title"

const val GROUP_VIDEOS_NONE = "-1"
const val GROUP_VIDEOS_FOLDER = "0"
const val GROUP_VIDEOS_NAME = "6"

// Items updates
const val UPDATE_SELECTION = 0
const val UPDATE_THUMB = 1
const val UPDATE_TIME = 2
const val UPDATE_SEEN = 3
const val UPDATE_DESCRIPTION = 4
const val UPDATE_PAYLOAD = 5
const val UPDATE_VIDEO_GROUP = 6
const val UPDATE_REORDER = 7
const val UPDATE_FAVORITE_STATE = 8

const val KEY_URI = "uri"
const val SELECTED_ITEM = "selected"
const val CURRENT_BROWSER_LIST = "CURRENT_BROWSER_LIST"
const val CURRENT_BROWSER_MAP = "CURRENT_BROWSER_MAP"

//Dummy items
const val DUMMY_NEW_GROUP = 0L

//Moviepedia
const val MOVIEPEDIA_MEDIA: String = "moviepedia_media"

// Database
const val TYPE_NETWORK_FAV = 0
const val TYPE_LOCAL_FAV = 1

//Crash reporting
const val CRASH_ML_CTX = "crash_ml_ctx"
const val CRASH_ML_MSG = "crash_ml_msg"
const val CRASH_HAPPENED = "crash_happened"

fun String.buildPkgString() = "${BuildConfig.APP_ID}.$this"

const val ACTION_VIEW_ARC = "org.chromium.arc.intent.action.VIEW"
const val ACTION_SEARCH_GMS = "com.google.android.gms.actions.SEARCH_ACTION"

const val CONTENT_PREFIX = "content_"
const val CONTENT_RESUME = "${CONTENT_PREFIX}resume_"
const val CONTENT_EPISODE = "${CONTENT_PREFIX}episode_"
const val ACTION_OPEN_CONTENT = "action_open_content"
const val EXTRA_CONTENT_ID = "extra_content_id"
const val SCHEME_PACKAGE = "package"

// Class names
const val START_ACTIVITY = "org.videolan.vlc.StartActivity"
const val COMPATERROR_ACTIVITY = "org.videolan.vlc.gui.CompatErrorActivity"
const val TV_SEARCH_ACTIVITY = "org.videolan.television.ui.SearchActivity"
const val MOBILE_SEARCH_ACTIVITY = "org.videolan.vlc.gui.SearchActivity"
//const val TV_MAIN_ACTIVITY = "org.videolan.television.ui.MainTvActivity"
const val TV_MAIN_ACTIVITY = "org.videolan.television.ui.MainActivity"
const val TV_CONFIRMATION_ACTIVITY = "org.videolan.television.ui.dialogs.ConfirmationTvActivity"
const val TV_PREFERENCE_ACTIVITY = "org.videolan.television.ui.preferences.PreferencesActivity"
const val MOBILE_MAIN_ACTIVITY = "org.videolan.vlc.gui.MainActivity"
const val MOVIEPEDIA_ACTIVITY = "org.videolan.moviepedia.ui.MoviepediaActivity"
const val TV_AUDIOPLAYER_ACTIVITY = "org.videolan.television.ui.audioplayer.AudioPlayerActivity"
const val MEDIAPARSING_SERVICE = "org.videolan.vlc.MediaParsingService"
const val TV_ONBOARDING_ACTIVITY = "org.videolan.television.ui.OnboardingActivity"
const val REMOTE_ACCESS_SERVICE = "org.videolan.vlc.remoteaccessserver.RemoteAccessService"
const val REMOTE_ACCESS_ONBOARDING = "org.videolan.vlc.remoteaccessserver.gui.remoteaccess.onboarding.RemoteAccessOnboardingActivity"

const val ROOM_DATABASE = "/vlc_database.zip"
const val EXPORT_SETTINGS_FILE = "/vlc_exported_settings.json"
const val EXPORT_EQUALIZERS_FILE = "/vlc_exported_equalizers.json"