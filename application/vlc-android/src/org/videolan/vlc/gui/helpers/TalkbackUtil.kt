/*
 * ************************************************************************
 *  TalkbackUtil.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

import android.content.Context
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.vlc.R

object TalkbackUtil {

    fun getArtist(context: Context, artist: String) = context.getString(R.string.talkback_artist, artist)
    fun getDuration(context: Context, duration: String) = context.getString(R.string.talkback_duration, duration)
    fun getAlbumTitle(context: Context, album: String) = context.getString(R.string.talkback_album, album)
    fun getReleaseDate(context: Context, date: String) = context.getString(R.string.talkback_release_date, date)
    fun getVideo(context: Context, video: MediaWrapper) =
            context.getString(R.string.talkback_video).talkbackAppend(getDuration(context, millisToString(context, video.length)))

    fun getVideoGroup(context: Context, video: VideoGroup) =
            context.getString(R.string.talkback_video_group, video.title)
                    .talkbackAppend(context.resources.getQuantityString(R.plurals.videos_quantity, video.mediaCount(), video.mediaCount()))

    fun getFolder(context: Context, folder: Folder): String {
        val mediaCount = folder.mediaCount(Folder.TYPE_FOLDER_VIDEO)
        return context.getString(R.string.talkback_folder, folder.title)
                .talkbackAppend(context.resources.getQuantityString(R.plurals.videos_quantity, mediaCount, mediaCount))
    }

    fun millisToString(context: Context, duration: Long): String {
        var millis = duration
        val sb = StringBuilder()
        if (millis < 0) {
            millis = -millis
            sb.append("-")
        }
        millis /= 1000
        val sec = (millis % 60).toInt()
        millis /= 60
        val min = (millis % 60).toInt()
        millis /= 60
        val hours = millis.toInt()
        if (hours > 0) sb.append(hours).append(" ").append(context.getString(R.string.talkback_hours)).append(" ")
        if (min > 0) sb.append(min).append(" ").append(context.getString(R.string.talkback_minutes)).append(" ")
        if (sec > 0) sb.append(sec).append(" ").append(context.getString(R.string.talkback_seconds))
        return sb.toString()
    }

}

fun String.talkbackAppend(other: String?, longPause: Boolean = false) = if (other.isNullOrBlank()) this else "$this${if (longPause) "." else ","} $other"