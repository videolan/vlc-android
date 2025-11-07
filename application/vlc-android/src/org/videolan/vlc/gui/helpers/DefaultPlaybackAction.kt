/*
 * ************************************************************************
 *  DefaultAction.kt
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

package org.videolan.vlc.gui.helpers

import android.content.SharedPreferences
import androidx.annotation.StringRes
import org.videolan.resources.AppContextProvider
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.DefaultPlaybackAction.entries

/**
 * Default playback action for items
 *
 * @property title the action title
 * @property selected true if this action is selected
 * @constructor Create empty Default action
 */
enum class DefaultPlaybackAction(@StringRes val title: Int, var selected: Boolean = false) {
    PLAY(R.string.play), PLAY_ALL(R.string.play_all), ADD_TO_QUEUE(R.string.append), INSERT_NEXT(R.string.insert_next);

    override fun toString(): String {
        return AppContextProvider.appContext.getString(title)
    }

    companion object {
        /**
         * Get entries without play all. Select the given [DefaultPlaybackAction]
         *
         * @param selection the [DefaultPlaybackAction] to select
         */
        fun getEntriesWithoutPlayAll(selection: DefaultPlaybackAction) = entries.filter { it != PLAY_ALL }.map { it.apply { selected = it == selection } }

        /**
         * Get entries with selection
         *
         * @param selection the [DefaultPlaybackAction] to select
         */
        fun getEntriesWithSelection(selection: DefaultPlaybackAction) = entries.map { it.apply { selected = it == selection } }
    }
}

/**
 * Default playback action media type
 *
 * @property title the title of the media type to display in the Display Settings
 * @property defaultActionKey the key of the default action in the SharedPreferences
 * @property allowPlayAll true if the "Play all" action is allowed for this media type
 * @constructor Create empty Default playback action media type
 */
enum class DefaultPlaybackActionMediaType(@StringRes val title: Int, val defaultActionKey: String, val allowPlayAll: Boolean = true) {
    VIDEO(R.string.default_action_videos, "default_playback_action_video"),
    ARTIST(R.string.artists, "default_playback_action_artist", false),
    ALBUM(R.string.albums, "default_playback_action_album", false),
    TRACK(R.string.tracks, "default_playback_action_track"),
    GENRE(R.string.genres, "default_playback_action_genre", false),
    PLAYLIST(R.string.playlists, "default_playback_action_playlist", false),
    FILE(R.string.files, "default_playback_action_file");

    /**
     * Get default playback actions for this media type
     *
     * @param prefs the shared preferences to use
     * @return the default playback actions
     */
    fun getDefaultPlaybackActions(prefs: SharedPreferences): List<DefaultPlaybackAction> {
        return if (allowPlayAll)
            DefaultPlaybackAction.getEntriesWithSelection(getCurrentPlaybackAction(prefs))
        else
            DefaultPlaybackAction.getEntriesWithoutPlayAll(getCurrentPlaybackAction(prefs))
    }

    /**
     * Get current playback action for this media type
     *
     * @param prefs the shared preferences to use
     */
    fun getCurrentPlaybackAction(prefs: SharedPreferences) = DefaultPlaybackAction.valueOf(prefs.getString(defaultActionKey, DefaultPlaybackAction.PLAY.name) ?: DefaultPlaybackAction.PLAY.name)

    fun saveCurrentPlaybackAction(prefs: SharedPreferences, value: DefaultPlaybackAction) = prefs.putSingle(defaultActionKey, value.name)
}