/*
 * ************************************************************************
 *  MediaZipUtils.kt
 * *************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
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

package org.videolan.vlc.webserver.utils

import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.*
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.slugify
import java.io.File

object MediaZipUtils {
    /**
     * Generate a zip file for a [Genre]
     *
     * @param genre the genre to zip
     * @return the filename
     */
    fun generateGenreZip(genre: Genre, folder:String): String {
        val files = genre.tracks.mapNotNull { prepareTrackForZip(it, -1) }

        val filename = "${genre.title.slugify("_")}.zip"
        val dst = File("$folder/$filename")
        FileUtils.zipWithName(files.toTypedArray(), dst.path)
        return filename
    }

    /**
     * Generate a zip file for a [Playlist]
     *
     * @param playlist the playlist to zip
     * @return the filename
     */
    fun generatePlaylistZip(playlist: Playlist, folder:String): String {
        val files = playlist.tracks.mapNotNull { prepareTrackForZip(it, -1) }

        val filename = "${playlist.title.slugify("_")}.zip"
        val dst = File("$folder/$filename")
        FileUtils.zipWithName(files.toTypedArray(), dst.path)
        return filename
    }

    /**
     * Generate a zip file for a [VideoGroup]
     *
     * @param videoGroup the video group to zip
     * @return the filename
     */
    fun generateVideoGroupZip(videoGroup: VideoGroup, folder:String): String {
        val files = videoGroup.media(Medialibrary.SORT_DEFAULT, false, false, false, videoGroup.mediaCount(), 0).mapNotNull { prepareTrackForZip(it, -1) }

        val filename = "${videoGroup.title.slugify("_")}.zip"
        val dst = File("$folder/$filename")
        FileUtils.zipWithName(files.toTypedArray(), dst.path)
        return filename
    }

    /**
     * Generate a zip file for a [Folder]
     *
     * @param videoFolder the video folder to zip
     * @return the filename
     */
    fun generateVideoGroupZip(videoFolder: Folder, folder:String): String {
        val files = videoFolder.media(Folder.TYPE_FOLDER_VIDEO, Medialibrary.SORT_DEFAULT, false, false, false, videoFolder.mediaCount(Folder.TYPE_FOLDER_VIDEO), 0).mapNotNull { prepareTrackForZip(it, -1) }


        val filename = "${videoFolder.title.slugify("_")}.zip"
        val dst = File("$folder/$filename")
        FileUtils.zipWithName(files.toTypedArray(), dst.path)
        return filename
    }

    /**
     * Generate a zip file for a [Artist]
     *
     * @param artist the artist to zip
     * @return the filename
     */
    fun generateArtistZip(artist: Artist, folder:String): String {
        val files = ArrayList<Pair<String, String>>()
        artist.albums.forEach { album ->
            val albumName = album.title.slugify("_")
            generateAlbumFiles(album).forEach {
                val year = if (album.releaseYear <= 0) "" else album.releaseYear.toString()
                files.add(Pair(it.first, "${year}_$albumName/${it.second}"))
            }
        }

        val filename = "${artist.title.slugify("_")}.zip"
        val dst = File("$folder/$filename")
        FileUtils.zipWithName(files.toTypedArray(), dst.path)
        return filename
    }

    /**
     * Generate a zip file for a [Album]
     *
     * @param album the album to zip
     * @return the filename
     */
    fun generateAlbumZip(album: Album, folder:String): String {
        val filename = "${album.title.slugify("_")}.zip"
        val dst = File("$folder/$filename")
        val files = generateAlbumFiles(album)
        FileUtils.zipWithName(files.toTypedArray(), dst.path)
        return filename
    }

    /**
     * Generate a list of pair containing the file path and the file name
     * for an [Album]
     *
     * @param album the album to parse
     * @return a list of pair of file path / file name
     */
    private fun generateAlbumFiles(album: Album): MutableList<Pair<String, String>> {
        val tracks = album.tracks
        return tracks
                .mapNotNull { track ->
                    prepareTrackForZip(track, (tracks?.indexOf(track) ?: -2) + 1)
                }
                .toMutableList()
                .apply { album.artworkMrl?.let { add(Pair(it, "cover.jpg")) } }

    }

    /**
     * Generate a pair of file path / file name to be zipped
     * It normalizes the file name
     *
     * @param track the track to prepare
     * @param index the index of the track to be prepended
     * @return a pair of file path / file name or null if the file is not local
     */
    private fun prepareTrackForZip(track: MediaWrapper, index: Int): Pair<String, String>? {
        return if (track.uri.scheme == "file") {
            track.uri.path?.let { getFileNamePair(track, if (index < 1) "" else if (index < 10) "0$index " else "$index ") }
        } else null
    }

    /**
     * Get the Pair for a track
     *
     * @param track the track to process
     * @param number the number to prepend
     * @return a pair of file path / file name
     */
    private fun getFileNamePair(track: MediaWrapper, number: String): Pair<String, String> {
        return Pair(track.uri.path!!, "$number${track.title}".slugify("_") + track.uri.toString().substring(track.uri.toString().lastIndexOf(".")))
    }
}