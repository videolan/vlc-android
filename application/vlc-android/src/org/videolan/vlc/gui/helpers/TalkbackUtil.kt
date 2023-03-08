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
import org.videolan.medialibrary.interfaces.media.*
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.util.getFilesNumber
import org.videolan.vlc.util.getFolderNumber

object TalkbackUtil {

    fun getDuration(context: Context, duration: Long) = context.getString(R.string.talkback_duration, millisToString(context, duration))
    fun getDuration(context: Context, duration: String) = context.getString(R.string.talkback_duration, duration)
    fun getPlayed(context: Context, video: MediaWrapper) = if (video.playCount > 0) context.getString(R.string.talkback_already_played) else null
    fun getAlbumTitle(context: Context, album: String) = context.getString(R.string.talkback_album, album)
    fun getReleaseDate(context: Context, date: String?) = if (date == null) "" else context.getString(R.string.talkback_release_date, date)
    fun getVideo(context: Context, video: MediaWrapper) = context.getString(R.string.talkback_video, video.title)
            .talkbackAppend(getPlayed(context, video))
            .talkbackAppend(getDuration(context, millisToString(context, video.length)))

    fun getStream(context: Context, stream: MediaWrapper) = context.getString(R.string.talkback_stream, stream.title)

    fun getAudioTrack(context: Context, audio: MediaWrapper) = context.getString(R.string.talkback_audio_track, audio.title)
            .talkbackAppend(getDuration(context, millisToString(context, audio.length)))
            .talkbackAppend(context.getString(R.string.talkback_album, audio.album))
            .talkbackAppend(context.getString(R.string.talkback_artist, audio.artist))

    fun getVideoGroup(context: Context, video: VideoGroup) = context.getString(R.string.talkback_video_group, video.title)
            .talkbackAppend(context.resources.getQuantityString(R.plurals.videos_quantity, video.mediaCount(), video.mediaCount()))

    fun getGenre(context: Context, genre: Genre) = context.getString(R.string.talkback_genre, genre.title)
            .talkbackAppend(context.resources.getQuantityString(R.plurals.track_quantity, genre.tracksCount, genre.tracksCount))

    fun getArtist(context: Context, artist: Artist?) = if (artist == null) null else context.getString(R.string.talkback_artist, artist.title)
            .talkbackAppend(context.resources.getQuantityString(R.plurals.albums_quantity, artist.albumsCount, artist.albumsCount))

    fun getAlbum(context: Context, album: Album) = context.getString(R.string.talkback_album, album.title)
            .talkbackAppend(context.getString(R.string.talkback_artist, album.albumArtist))
            .talkbackAppend(context.resources.getQuantityString(R.plurals.track_quantity, album.tracksCount, album.tracksCount))

    fun getPlaylist(context: Context, playlist: Playlist) = context.getString(R.string.talkback_playlist, playlist.title)
            .talkbackAppend(context.resources.getQuantityString(R.plurals.track_quantity, playlist.tracksCount, playlist.tracksCount))

    fun getArtist(context: Context, artist: String?) = if (artist == null) "" else context.getString(R.string.talkback_artist, artist)
    fun getTrackNumber(context: Context, item: MediaWrapper) = context.getString(R.string.talkback_track_number, item.trackNumber.toString())
    fun getTimeAndArtist(context: Context, item: MediaWrapper) = millisToString(context, item.length)
            .talkbackAppend(getArtist(context, item.artist))


    fun getFolder(context: Context, folder: Folder): String {
        val mediaCount = folder.mediaCount(Folder.TYPE_FOLDER_VIDEO)
        return context.getString(R.string.talkback_folder, folder.title)
                .talkbackAppend(context.resources.getQuantityString(R.plurals.videos_quantity, mediaCount, mediaCount))
    }

    fun getDir(context: Context, folder: MediaLibraryItem, favorite: Boolean): String {
        if (folder !is MediaWrapper) return context.getString(R.string.talkback_folder, folder.title)
        var text: String
        if (folder.type == MediaWrapper.TYPE_DIR) {
            val folders = folder.description?.getFolderNumber() ?: 0
            val files = folder.description?.getFilesNumber() ?: 0
            text = context.getString(if (favorite) R.string.talkback_favorite else R.string.talkback_folder, folder.title)
            if (folders > 0) text = text.talkbackAppend(context.resources.getQuantityString(R.plurals.subfolders_quantity, folders, folders))
            if (files > 0) text = text.talkbackAppend(context.resources.getQuantityString(R.plurals.mediafiles_quantity, files, files))
            if (files < 1 && folders < 1) text = text.talkbackAppend(context.getString(R.string.empty_directory))
        } else {
            text = context.getString(R.string.talkback_file, folder.title)
            if (!folder.description.isNullOrEmpty()) text = text.talkbackAppend(context.getString(R.string.talkback_file_size, folder.description))
        }
        return text
    }

    fun getAll(media: MediaLibraryItem): String  = media.title

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