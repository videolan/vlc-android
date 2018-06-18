/*****************************************************************************
 * Constants.java
 *****************************************************************************
 * Copyright © 2017-2018 VLC authors and VideoLAN
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
 *****************************************************************************/

package org.videolan.vlc.util;

public class Constants {

    // StartActivity
    public static final String PREF_FIRST_RUN = "first_run";
    public static final String EXTRA_FIRST_RUN = "extra_first_run";
    public static final String EXTRA_UPGRADE = "extra_upgrade";
    public static final String EXTRA_PARSE = "extra_parse";

    //UI Navigation
    public static final String ID_VIDEO = "video";
    public static final String ID_AUDIO = "audio";
    public static final String ID_NETWORK = "network";
    public static final String ID_DIRECTORIES = "directories";
    public static final String ID_HISTORY = "history";
    public static final String ID_MRL = "mrl";
    public static final String ID_PREFERENCES = "preferences";
    public static final String ID_ABOUT = "about";

    public static final int ACTIVITY_RESULT_PREFERENCES = 1;
    public static final int ACTIVITY_RESULT_OPEN = 2;
    public static final int ACTIVITY_RESULT_SECONDARY = 3;

    // PlaybackService
    public static final String ACTION_REMOTE_GENERIC =  Strings.buildPkgString("remote.");
    public static final String EXTRA_SEARCH_BUNDLE = ACTION_REMOTE_GENERIC+"extra_search_bundle";
    public static final String ACTION_PLAY_FROM_SEARCH = ACTION_REMOTE_GENERIC+"play_from_search";
    public static final String ACTION_REMOTE_SWITCH_VIDEO = ACTION_REMOTE_GENERIC+"SwitchToVideo";
    public static final String ACTION_REMOTE_LAST_VIDEO_PLAYLIST = ACTION_REMOTE_GENERIC+"LastVideoPlaylist";
    public static final String ACTION_REMOTE_LAST_PLAYLIST = ACTION_REMOTE_GENERIC+"LastPlaylist";
    public static final String ACTION_REMOTE_FORWARD = ACTION_REMOTE_GENERIC+"Forward";
    public static final String ACTION_REMOTE_STOP = ACTION_REMOTE_GENERIC+"Stop";
    public static final String ACTION_REMOTE_PAUSE = ACTION_REMOTE_GENERIC+"Pause";
    public static final String ACTION_REMOTE_PLAYPAUSE = ACTION_REMOTE_GENERIC+"PlayPause";
    public static final String ACTION_REMOTE_PLAY = ACTION_REMOTE_GENERIC+"Play";
    public static final String ACTION_REMOTE_BACKWARD = ACTION_REMOTE_GENERIC+"Backward";
    public static final String ACTION_CAR_MODE_EXIT = "android.app.action.EXIT_CAR_MODE";
    public static final int PLAYLIST_TYPE_AUDIO = 0;
    public static final int PLAYLIST_TYPE_VIDEO = 1;
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;

    // MediaParsingService
    public final static String ACTION_INIT = "medialibrary_init";
    public final static String ACTION_RELOAD = "medialibrary_reload";
    public final static String ACTION_DISCOVER = "medialibrary_discover";
    public final static String ACTION_DISCOVER_DEVICE = "medialibrary_discover_device";
    public final static String ACTION_CHECK_STORAGES = "medialibrary_check_storages";
    public final static String EXTRA_PATH = "extra_path";
    public final static String EXTRA_UUID = "extra_uuid";
    public final static String ACTION_RESUME_SCAN = "action_resume_scan";
    public final static String ACTION_PAUSE_SCAN = "action_pause_scan";

    // VideoPlayerActivity
    public final static String PLAY_FROM_VIDEOGRID = Strings.buildPkgString("gui.video.PLAY_FROM_VIDEOGRID");
    public final static String PLAY_FROM_SERVICE = Strings.buildPkgString("gui.video.PLAY_FROM_SERVICE");
    public final static String EXIT_PLAYER = Strings.buildPkgString("gui.video.EXIT_PLAYER");
    public final static String PLAY_EXTRA_ITEM_LOCATION = "item_location";
    public final static String PLAY_EXTRA_SUBTITLES_LOCATION = "subtitles_location";
    public final static String PLAY_EXTRA_ITEM_TITLE = "title";
    public final static String PLAY_EXTRA_FROM_START = "from_start";
    public final static String PLAY_EXTRA_START_TIME = "position";
    public final static String PLAY_EXTRA_OPENED_POSITION = "opened_position";
    public final static String PLAY_DISABLE_HARDWARE = "disable_hardware";

    // AudioPlayer
    public final static String PREF_PLAYLIST_TIPS_SHOWN = "playlist_tips_shown";
    public final static String PREF_AUDIOPLAYER_TIPS_SHOWN = "audioplayer_tips_shown";

    // Preferences
    public final static String KEY_ARTISTS_SHOW_ALL = "artists_show_all";
    public final static String KEY_MEDIALIBRARY_SCAN = "ml_scan";
    public static final int ML_SCAN_ON = 0;
    public static final int ML_SCAN_OFF = 1;

    // AUDIO category
    public final static String KEY_AUDIO_CURRENT_TAB = "key_audio_current_tab";

    //TV
    public static final long HEADER_VIDEO = 0L;
    public static final long HEADER_CATEGORIES = 1L;
    public static final long HEADER_HISTORY = 2L;
    public static final long HEADER_NETWORK = 3L;
    public static final long HEADER_DIRECTORIES = 4L;
    public static final long HEADER_MISC = 5L;
    public static final long HEADER_STREAM = 6L;
    public static final long ID_SETTINGS = 10L;
    public static final long ID_ABOUT_TV = 11L;
    public static final long ID_LICENCE = 12L;
    public static final long CATEGORY_NOW_PLAYING = 20L;
    public static final long CATEGORY_ARTISTS = 21L;
    public static final long CATEGORY_ALBUMS = 22L;
    public static final long CATEGORY_GENRES = 23L;
    public static final long CATEGORY_SONGS = 24L;

    public static final String AUDIO_CATEGORY = "category";
    public static final String AUDIO_ITEM = "item";
    public final static String KEY_GROUP = "key_group";

    // Items updates
    public final static int UPDATE_SELECTION = 0;
    public final static int UPDATE_THUMB = 1;
    public final static int UPDATE_TIME = 2;
    public final static int UPDATE_SEEN = 3;
    public final static int UPDATE_DESCRIPTION = 4;

    public static final String KEY_URI = "uri";
    public static final String SELECTED_ITEM = "selected";
    public static final String CURRENT_BROWSER_LIST = "CURRENT_BROWSER_LIST";
    public static final String CURRENT_BROWSER_MAP = "CURRENT_BROWSER_MAP";

    // Context options
    public final static int CTX_PLAY_ALL           = 1;
    public final static int CTX_APPEND             = 1 << 1;
    public final static int CTX_PLAY_AS_AUDIO      = 1 << 2;
    public final static int CTX_INFORMATION        = 1 << 3;
    public final static int CTX_DELETE             = 1 << 4;
    public final static int CTX_DOWNLOAD_SUBTITLES = 1 << 5;
    public final static int CTX_PLAY_FROM_START    = 1 << 6;
    public final static int CTX_PLAY_GROUP         = 1 << 7;
    public final static int CTX_PLAY               = 1 << 8;
    public final static int CTX_PLAY_NEXT          = 1 << 9;
    public final static int CTX_ADD_TO_PLAYLIST    = 1 << 10;
    public final static int CTX_SET_RINGTONE       = 1 << 11;
    public final static int CTX_NETWORK_ADD        = 1 << 12;
    public final static int CTX_NETWORK_EDIT       = 1 << 13;
    public final static int CTX_NETWORK_REMOVE     = 1 << 14;
    public final static int CTX_CUSTOM_REMOVE      = 1 << 15;
    public final static int CTX_ITEM_DL            = 1 << 16;

    public final static int CTX_VIDEO_FLAGS = Constants.CTX_APPEND|Constants.CTX_DELETE|Constants.CTX_DOWNLOAD_SUBTITLES|Constants.CTX_INFORMATION|Constants.CTX_PLAY_ALL|Constants.CTX_PLAY_AS_AUDIO;
    public final static int CTX_TRACK_FLAGS = Constants.CTX_APPEND|Constants.CTX_PLAY_NEXT|Constants.CTX_DELETE|Constants.CTX_INFORMATION|Constants.CTX_PLAY_ALL|Constants.CTX_ADD_TO_PLAYLIST|Constants.CTX_SET_RINGTONE;
    public final static int CTX_AUDIO_FLAGS = Constants.CTX_PLAY|Constants.CTX_APPEND|Constants.CTX_PLAY_NEXT|Constants.CTX_ADD_TO_PLAYLIST;
    public final static int CTX_PLAYLIST_FLAGS = Constants.CTX_APPEND|Constants.CTX_PLAY_NEXT|Constants.CTX_ADD_TO_PLAYLIST|Constants.CTX_INFORMATION|Constants.CTX_DELETE|Constants.CTX_SET_RINGTONE;
    public final static int CTX_VIDEO_GOUP_FLAGS = Constants.CTX_APPEND|Constants.CTX_PLAY;
}
