/*****************************************************************************
 * VideoPlayerActivity.java
 *****************************************************************************
 * Copyright © 2017 VLC authors and VideoLAN
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

    // AudioPlayerContainerActivity
    public static final String ACTION_SHOW_PLAYER = Strings.buildPkgString("gui.ShowPlayer");

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
    public final static String ACTION_SERVICE_STARTED = "action_service_started";
    public final static String ACTION_SERVICE_ENDED = "action_service_ended";
    public final static String ACTION_NEW_STORAGE = "action_new_storage";
    public final static String ACTION_PROGRESS = "action_progress";
    public final static String ACTION_PROGRESS_TEXT = "action_progress_text";
    public final static String ACTION_PROGRESS_VALUE = "action_progress_value";

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

    // AUDIO category
    public final static String KEY_AUDIO_CURRENT_TAB = "key_audio_current_tab";
}
