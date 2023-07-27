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
const val EXTRA_REMOVE_DEVICE = "extra_remove_device"

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
const val PLAYLIST_TYPE_VIDEO_RESUME = 2
const val MEDIALIBRARY_PAGE_SIZE = 500
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
const val EXTRA_MEDIA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
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

// AUDIO category
const val KEY_AUDIO_CURRENT_TAB = "key_audio_current_tab"

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
const val ID_SETTINGS = 10L
const val ID_ABOUT_TV = 11L
const val ID_REFRESH = 13L
const val ID_ALL_MOVIES = 14L
const val ID_ALL_TVSHOWS = 15L
const val ID_SPONSOR = 16L
const val ID_PIN_LOCK = 17L
const val CATEGORY_NOW_PLAYING = 20L
const val CATEGORY_ARTISTS = 21L
const val CATEGORY_ALBUMS = 22L
const val CATEGORY_GENRES = 23L
const val CATEGORY_SONGS = 24L
const val CATEGORY_VIDEOS = 25L
const val CATEGORY_PLAYLISTS = 27L
const val CATEGORY_NOW_PLAYING_PIP = 26L

const val CATEGORY = "category"
const val DRAWABLE = "drawable"
const val ITEM = "item"
const val KEY_GROUP = "key_group"
const val KEY_FOLDER = "key_folder"
const val KEY_GROUPING = "key_grouping"
const val KEY_ANIMATED = "key_animated"
const val FAVORITE_TITLE = "favorite_title"

const val KEY_VIDEOS_CARDS = "video_display_in_cards"
const val KEY_GROUP_VIDEOS = "video_min_group_length"
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

// Context options
const val CTX_PLAY_ALL = 1L
const val CTX_APPEND = 1L shl 1
const val CTX_PLAY_AS_AUDIO = 1L shl 2
const val CTX_INFORMATION = 1L shl 3
const val CTX_DELETE = 1L shl 4
const val CTX_DOWNLOAD_SUBTITLES = 1L shl 5
const val CTX_PLAY_FROM_START = 1L shl 6
const val CTX_PLAY_SHUFFLE = 1L shl 7
const val CTX_PLAY = 1L shl 8
const val CTX_PLAY_NEXT = 1L shl 9
const val CTX_ADD_TO_PLAYLIST = 1L shl 10
const val CTX_SET_RINGTONE = 1L shl 11
const val CTX_FAV_ADD = 1L shl 12
const val CTX_FAV_EDIT = 1L shl 13
const val CTX_FAV_REMOVE = 1L shl 14
const val CTX_CUSTOM_REMOVE = 1L shl 15
const val CTX_ITEM_DL = 1L shl 16
const val CTX_REMOVE_FROM_PLAYLIST = 1L shl 17
const val CTX_STOP_AFTER_THIS = 1L shl 18
const val CTX_RENAME = 1L shl 19
const val CTX_ADD_SCANNED = 1L shl 25
const val CTX_COPY = 1L shl 26
const val CTX_SHARE = 1L shl 27
const val CTX_FIND_METADATA = 1L shl 28
const val CTX_ADD_FOLDER_PLAYLIST = 1L shl 29
const val CTX_ADD_FOLDER_AND_SUB_PLAYLIST = 1L shl 30
const val CTX_ADD_GROUP = 1L shl 31
const val CTX_REMOVE_GROUP = 1L shl 32
const val CTX_RENAME_GROUP = 1L shl 33
const val CTX_UNGROUP = 1L shl 34
const val CTX_GROUP_SIMILAR = 1L shl 35
const val CTX_MARK_AS_PLAYED = 1L shl 36
const val CTX_MARK_ALL_AS_PLAYED = 1L shl 37
const val CTX_GO_TO_FOLDER = 1L shl 38
const val CTX_MARK_AS_UNPLAYED = 1L shl 39
const val CTX_ADD_SHORTCUT = 1L shl 40
const val CTX_BAN_FOLDER = 1L shl 41

const val CTX_VIDEO_FLAGS = CTX_APPEND or CTX_SET_RINGTONE or CTX_PLAY_NEXT or CTX_DELETE or CTX_DOWNLOAD_SUBTITLES or CTX_INFORMATION or CTX_PLAY or CTX_PLAY_ALL or CTX_PLAY_AS_AUDIO or CTX_ADD_TO_PLAYLIST or CTX_SHARE or CTX_ADD_SHORTCUT
const val CTX_TRACK_FLAGS = CTX_APPEND or CTX_PLAY_NEXT or CTX_DELETE or CTX_INFORMATION or CTX_PLAY_ALL or CTX_ADD_TO_PLAYLIST or CTX_SET_RINGTONE or CTX_SHARE or CTX_GO_TO_FOLDER or CTX_ADD_SHORTCUT
const val CTX_AUDIO_FLAGS = CTX_PLAY or CTX_APPEND or CTX_PLAY_NEXT or CTX_ADD_TO_PLAYLIST or CTX_INFORMATION or CTX_ADD_SHORTCUT
const val CTX_PLAYLIST_ALBUM_FLAGS = CTX_AUDIO_FLAGS or CTX_DELETE or CTX_ADD_SHORTCUT
const val CTX_PLAYLIST_ITEM_FLAGS = CTX_APPEND or CTX_PLAY_NEXT or CTX_ADD_TO_PLAYLIST or CTX_INFORMATION or CTX_DELETE or CTX_SET_RINGTONE or CTX_ADD_SHORTCUT
const val CTX_VIDEO_GROUP_FLAGS = CTX_PLAY_ALL or CTX_APPEND or CTX_ADD_TO_PLAYLIST or CTX_MARK_ALL_AS_PLAYED or CTX_RENAME_GROUP or CTX_UNGROUP or CTX_ADD_GROUP
const val CTX_FOLDER_FLAGS = CTX_PLAY_ALL or CTX_APPEND or CTX_ADD_TO_PLAYLIST or CTX_MARK_ALL_AS_PLAYED or CTX_BAN_FOLDER

//Moviepedia
const val MOVIEPEDIA_MEDIA: String = "moviepedia_media"

// Database
const val TYPE_NETWORK_FAV = 0
const val TYPE_LOCAL_FAV = 1

//Crash reporting
const val CRASH_ML_CTX = "crash_ml_ctx"
const val CRASH_ML_MSG = "crash_ml_msg"

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
const val TV_MAIN_ACTIVITY = "org.videolan.television.ui.MainTvActivity"
const val MOBILE_MAIN_ACTIVITY = "org.videolan.vlc.gui.MainActivity"
const val MOVIEPEDIA_ACTIVITY = "org.videolan.moviepedia.ui.MoviepediaActivity"
const val TV_AUDIOPLAYER_ACTIVITY = "org.videolan.television.ui.audioplayer.AudioPlayerActivity"
const val MEDIAPARSING_SERVICE = "org.videolan.vlc.MediaParsingService"
const val TV_ONBOARDING_ACTIVITY = "org.videolan.television.ui.OnboardingActivity"

const val ROOM_DATABASE = "/vlc_database.zip"