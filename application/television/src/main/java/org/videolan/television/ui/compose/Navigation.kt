/*
 * ************************************************************************
 *  Navigation.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.television.ui.compose

import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.videolan.television.R

@Serializable
sealed interface MainDestination : NavKey {
    @Serializable data class Video(val subDestination: VideoDestination = VideoDestination.Videos) : MainDestination
    @Serializable data class Audio(val subDestination: AudioDestination = AudioDestination.Artists) : MainDestination
    @Serializable data object Browse : MainDestination
    @Serializable data object Playlists : MainDestination
    @Serializable data object More : MainDestination
}

@Serializable
enum class VideoDestination(@StringRes val titleRes: Int) {
    Videos(R.string.video),
    Playlists(R.string.playlists)
}

@Serializable
enum class AudioDestination(@StringRes val titleRes: Int) {
    Artists(R.string.artists),
    Albums(R.string.albums),
    Tracks(R.string.tracks),
    Genres(R.string.genres),
    Playlists(R.string.playlists)
}
