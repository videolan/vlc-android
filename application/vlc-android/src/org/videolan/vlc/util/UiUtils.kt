/*
 * ************************************************************************
 *  UiUtils.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.util

import android.content.Context
import org.videolan.tools.KEY_VIDEOS_CARDS
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.gui.helpers.DefaultPlaybackAction
import org.videolan.vlc.gui.helpers.DefaultPlaybackActionMediaType

enum class MediaListEntry(
    val inCardsKey: String,
    val defaultInCard: Boolean,
    val onlyFavsKey: String,
    val defaultPlaybackActionMediaType: DefaultPlaybackActionMediaType
) {
    ARTISTS(
        inCardsKey = "display_mode_audio_browser_artists",
        defaultInCard = true,
        onlyFavsKey = "ArtistsProvider_only_favs",
        defaultPlaybackActionMediaType = DefaultPlaybackActionMediaType.ARTIST
    ),
    ALBUMS(
        inCardsKey = "display_mode_audio_browser_albums",
        defaultInCard = true,
        onlyFavsKey = "AlbumsProvider_only_favs",
        defaultPlaybackActionMediaType = DefaultPlaybackActionMediaType.ALBUM

    ),
    TRACKS(
        inCardsKey = "display_mode_audio_browser_track",
        defaultInCard = false,
        onlyFavsKey = "TracksProvider_only_favs",
        defaultPlaybackActionMediaType = DefaultPlaybackActionMediaType.TRACK
    ),
    GENRES(
        inCardsKey = "display_mode_audio_browser_genres",
        defaultInCard = false,
        onlyFavsKey = "GenresProvider_only_favs",
        defaultPlaybackActionMediaType = DefaultPlaybackActionMediaType.GENRE
    ),
    AUDIO_PLAYLISTS(
        inCardsKey = "display_mode_playlists_AudioOnly",
        defaultInCard = true,
        onlyFavsKey = "PlaylistsProvider_only_favs",
        defaultPlaybackActionMediaType = DefaultPlaybackActionMediaType.PLAYLIST
    ),
    VIDEO_PLAYLISTS(
        inCardsKey = "display_mode_playlists_Video",
        defaultInCard = true,
        onlyFavsKey = "PlaylistsProvider_only_favs",
        defaultPlaybackActionMediaType = DefaultPlaybackActionMediaType.PLAYLIST
    ),
    ALL_PLAYLISTS(
        inCardsKey = "display_mode_playlists_All",
        defaultInCard = false,
        onlyFavsKey = "PlaylistsProvider_only_favs",
        defaultPlaybackActionMediaType = DefaultPlaybackActionMediaType.PLAYLIST
    ),
    VIDEO(
        inCardsKey = KEY_VIDEOS_CARDS,
        defaultInCard = true,
        onlyFavsKey = "VideosProvider_only_favs",
        defaultPlaybackActionMediaType = DefaultPlaybackActionMediaType.VIDEO
    ),
    VIDEO_GROUPS(
        inCardsKey = KEY_VIDEOS_CARDS,
        defaultInCard = true,
        onlyFavsKey = "VideoGroupsProvider_only_favs",
        defaultPlaybackActionMediaType = DefaultPlaybackActionMediaType.VIDEO
    ),
    VIDEO_FOLDER(
        inCardsKey = KEY_VIDEOS_CARDS,
        defaultInCard = true,
        onlyFavsKey = "FoldersProvider_only_favs",
        defaultPlaybackActionMediaType = DefaultPlaybackActionMediaType.VIDEO
    ),
    BROWSER(
        inCardsKey = KEY_VIDEOS_CARDS,
        defaultInCard = true,
        onlyFavsKey = "",
        defaultPlaybackActionMediaType = DefaultPlaybackActionMediaType.FILE
    );

    /**
     * Display this entry in cards
     *
     * @param context Context used to retrieve the SharedPreferences
     * @return True if this entry should be displayed in cards, false otherwise
     */
    fun displayInCard(context: Context): Boolean {
        return Settings.getInstance(context).getBoolean(inCardsKey, defaultInCard)
    }

    /**
     * Save if this entry should be displayed in card
     *
     * @param context Context used to retrieve the SharedPreferences
     * @param value True if this entry should be displayed in cards, false otherwise
     */
    fun saveDisplayInCard(context: Context, value: Boolean) {
        Settings.getInstance(context).putSingle(inCardsKey, value)
    }

    /**
     * Display only favorites
     *
     * @param context Context used to retrieve the SharedPreferences
     * @return True if only favorites should be displayed, false otherwise
     */
    fun onlyFavs(context: Context): Boolean {
        return Settings.getInstance(context).getBoolean(onlyFavsKey, false)
    }

    /**
     * Save if only favorites should be displayed
     *
     * @param context Context used to retrieve the SharedPreferences
     * @param value True if only favorites should be displayed, false otherwise
     */
    fun saveOnlyFavs(context: Context, value: Boolean) {
        Settings.getInstance(context).putSingle(onlyFavsKey, value)
    }

    /**
     * Get current playback action
     *
     * @param context Context used to retrieve the SharedPreferences
     * @return Current playback action
     */
    fun playbackAction(context: Context): DefaultPlaybackAction = defaultPlaybackActionMediaType.getCurrentPlaybackAction(Settings.getInstance(context))

    /**
     * Default playback actions
     *
     * @param context Context used to retrieve the SharedPreferences
     * @return Default playback actions
     */
    fun defaultPlaybackActions(context: Context): List<DefaultPlaybackAction> = defaultPlaybackActionMediaType.getDefaultPlaybackActions(Settings.getInstance(context))

    /**
     * Save the default playback action
     *
     * @param context Context used to retrieve the SharedPreferences
     * @param action Default playback action to save
     */
    fun saveDefaultPlaybackAction(context: Context, action: DefaultPlaybackAction) {
        Settings.getInstance(context).putSingle(defaultPlaybackActionMediaType.defaultActionKey, action.name)
    }
}