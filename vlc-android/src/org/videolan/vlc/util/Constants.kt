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
package org.videolan.vlc.util

// StartActivity
const val PREF_FIRST_RUN = "first_run"
const val EXTRA_FIRST_RUN = "extra_first_run"
const val EXTRA_UPGRADE = "extra_upgrade"
const val EXTRA_PARSE = "extra_parse"
const val EXTRA_TARGET = "extra_parse"

//UI Navigation
const val ID_VIDEO = "video"
const val ID_AUDIO = "audio"
const val ID_PLAYLISTS = "playlists"
const val ID_NETWORK = "network"
const val ID_DIRECTORIES = "directories"
const val ID_HISTORY = "history"
const val ID_MRL = "mrl"
const val ID_PREFERENCES = "preferences"
const val ID_ABOUT = "about"

const val ACTIVITY_RESULT_PREFERENCES = 1
const val ACTIVITY_RESULT_OPEN = 2
const val ACTIVITY_RESULT_SECONDARY = 3

// PlaybackService
@JvmField val ACTION_REMOTE_GENERIC = "remote.".buildPkgString()!!
@JvmField val EXTRA_SEARCH_BUNDLE = "${ACTION_REMOTE_GENERIC}extra_search_bundle"
@JvmField val ACTION_PLAY_FROM_SEARCH = "${ACTION_REMOTE_GENERIC}play_from_search"
@JvmField val ACTION_REMOTE_SWITCH_VIDEO = "${ACTION_REMOTE_GENERIC}SwitchToVideo"
@JvmField val ACTION_REMOTE_LAST_VIDEO_PLAYLIST = "${ACTION_REMOTE_GENERIC}LastVideoPlaylist"
@JvmField val ACTION_REMOTE_LAST_PLAYLIST = "${ACTION_REMOTE_GENERIC}LastPlaylist"
@JvmField val ACTION_REMOTE_FORWARD = "${ACTION_REMOTE_GENERIC}Forward"
@JvmField val ACTION_REMOTE_STOP = "${ACTION_REMOTE_GENERIC}Stop"
@JvmField val ACTION_REMOTE_PAUSE = "${ACTION_REMOTE_GENERIC}Pause"
@JvmField val ACTION_REMOTE_PLAYPAUSE = "${ACTION_REMOTE_GENERIC}PlayPause"
@JvmField val ACTION_REMOTE_PLAY = "${ACTION_REMOTE_GENERIC}Play"
@JvmField val ACTION_REMOTE_BACKWARD = "${ACTION_REMOTE_GENERIC}Backward"
const val ACTION_CAR_MODE_EXIT = "android.app.action.EXIT_CAR_MODE"
const val PLAYLIST_TYPE_AUDIO = 0
const val PLAYLIST_TYPE_VIDEO = 1
const val REPEAT_NONE = 0
const val REPEAT_ONE = 1
const val REPEAT_ALL = 2
const val PLAYBACK_LOAD_SIZE = 500


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

// AudioPlayer
const val PREF_PLAYLIST_TIPS_SHOWN = "playlist_tips_shown"
const val PREF_AUDIOPLAYER_TIPS_SHOWN = "audioplayer_tips_shown"

// Preferences
const val KEY_ARTISTS_SHOW_ALL = "artists_show_all"
const val KEY_MEDIALIBRARY_SCAN = "ml_scan"
const val ML_SCAN_ON = 0
const val ML_SCAN_OFF = 1

// AUDIO category
const val KEY_AUDIO_CURRENT_TAB = "key_audio_current_tab"

//TV
const val HEADER_VIDEO = 0L
const val HEADER_CATEGORIES = 1L
const val HEADER_HISTORY = 2L
const val HEADER_NETWORK = 3L
const val HEADER_DIRECTORIES = 4L
const val HEADER_MISC = 5L
const val HEADER_STREAM = 6L
const val HEADER_SERVER = 7L
const val ID_SETTINGS = 10L
const val ID_ABOUT_TV = 11L
const val ID_LICENCE = 12L
const val CATEGORY_NOW_PLAYING = 20L
const val CATEGORY_ARTISTS = 21L
const val CATEGORY_ALBUMS = 22L
const val CATEGORY_GENRES = 23L
const val CATEGORY_SONGS = 24L

const val AUDIO_CATEGORY = "category"
const val AUDIO_ITEM = "item"
const val KEY_GROUP = "key_group"

// Items updates
const val UPDATE_SELECTION = 0
const val UPDATE_THUMB = 1
const val UPDATE_TIME = 2
const val UPDATE_SEEN = 3
const val UPDATE_DESCRIPTION = 4

const val KEY_URI = "uri"
const val SELECTED_ITEM = "selected"
const val CURRENT_BROWSER_LIST = "CURRENT_BROWSER_LIST"
const val CURRENT_BROWSER_MAP = "CURRENT_BROWSER_MAP"

// Context options
const val CTX_PLAY_ALL = 1
const val CTX_APPEND = 1 shl 1
const val CTX_PLAY_AS_AUDIO = 1 shl 2
const val CTX_INFORMATION = 1 shl 3
const val CTX_DELETE = 1 shl 4
const val CTX_DOWNLOAD_SUBTITLES = 1 shl 5
const val CTX_PLAY_FROM_START = 1 shl 6
const val CTX_PLAY_GROUP = 1 shl 7
const val CTX_PLAY = 1 shl 8
const val CTX_PLAY_NEXT = 1 shl 9
const val CTX_ADD_TO_PLAYLIST = 1 shl 10
const val CTX_SET_RINGTONE = 1 shl 11
const val CTX_FAV_ADD = 1 shl 12
const val CTX_FAV_EDIT = 1 shl 13
const val CTX_FAV_REMOVE = 1 shl 14
const val CTX_CUSTOM_REMOVE = 1 shl 15
const val CTX_ITEM_DL = 1 shl 16
const val CTX_REMOVE_FROM_PLAYLIST = 1 shl 17
const val CTX_STOP_AFTER_THIS = 1 shl 18
const val CTX_RENAME = 1 shl 19
const val CTX_AUDIO_TRACK = 1 shl 20
const val CTX_SUBS_TRACK = 1 shl 21
const val CTX_PICK_SUBS = 1 shl 22
const val CTX_VIDEO_TRACK = 1 shl 23
const val CTX_DOWNLOAD_SUBTITLES_PLAYER = 1 shl 24

const val CTX_VIDEO_FLAGS = CTX_APPEND or CTX_DELETE or CTX_DOWNLOAD_SUBTITLES or CTX_INFORMATION or CTX_PLAY_ALL or CTX_PLAY_AS_AUDIO or CTX_ADD_TO_PLAYLIST
const val CTX_TRACK_FLAGS = CTX_APPEND or CTX_PLAY_NEXT or CTX_DELETE or CTX_INFORMATION or CTX_PLAY_ALL or CTX_ADD_TO_PLAYLIST or CTX_SET_RINGTONE
const val CTX_AUDIO_FLAGS = CTX_PLAY or CTX_APPEND or CTX_PLAY_NEXT or CTX_ADD_TO_PLAYLIST
const val CTX_PLAYLIST_FLAGS = CTX_AUDIO_FLAGS or CTX_DELETE
const val CTX_PLAYLIST_ITEM_FLAGS = CTX_APPEND or CTX_PLAY_NEXT or CTX_ADD_TO_PLAYLIST or CTX_INFORMATION or CTX_DELETE or CTX_SET_RINGTONE
const val CTX_VIDEO_GOUP_FLAGS = CTX_APPEND or CTX_PLAY_GROUP

// Database
const val TYPE_NETWORK_FAV = 0
const val TYPE_LOCAL_FAV = 1



