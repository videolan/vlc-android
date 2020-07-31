/*
 * *************************************************************************
 *  StartActivity.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */
package org.videolan.vlc.util


import android.os.Bundle
import android.provider.MediaStore
import org.videolan.libvlc.util.AndroidUtil

class VoiceSearchParams(val query: String, extras: Bundle?) {
    var isAny: Boolean = false
    var isUnstructured: Boolean = false
    var isGenreFocus: Boolean = false
    var isArtistFocus: Boolean = false
    var isAlbumFocus: Boolean = false
    var isPlaylistFocus: Boolean = false
    var isSongFocus: Boolean = false
    var genre: String? = null
    var artist: String? = null
    var album: String? = null
    var playlist: String? = null
    var song: String? = null

    init {
        if (query.isEmpty()) {
            isAny = true
        } else if (extras == null || !extras.containsKey(MediaStore.EXTRA_MEDIA_FOCUS)) {
            isUnstructured = true
        } else {
            val genreKey = if (AndroidUtil.isLolliPopOrLater)
                MediaStore.EXTRA_MEDIA_GENRE
            else
                "android.intent.extra.genre"
            when (extras.getString(MediaStore.EXTRA_MEDIA_FOCUS)) {
                MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
                    isGenreFocus = true
                    genre = extras.getString(genreKey)
                    if (genre.isNullOrEmpty())
                        genre = query
                }
                MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> {
                    isArtistFocus = true
                    genre = extras.getString(genreKey)
                    artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                }
                MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
                    isAlbumFocus = true
                    album = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)
                    genre = extras.getString(genreKey)
                    artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                }
                MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> {
                    isPlaylistFocus = true
                    playlist = extras.getString(MediaStore.EXTRA_MEDIA_PLAYLIST)
                }
                MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> {
                    isSongFocus = true
                    song = extras.getString(MediaStore.EXTRA_MEDIA_TITLE)
                    if (song!!.contains("("))
                        song = song!!.substring(0, song!!.indexOf('('))
                    album = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)
                    genre = extras.getString(genreKey)
                    artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                }
            }
        }
    }

    override fun toString(): String {
        return ("""
            query=$query
            isAny=$isAny
            isUnstructured=$isUnstructured
            isGenreFocus=$isGenreFocus
            isArtistFocus=$isArtistFocus
            isAlbumFocus=$isAlbumFocus
            isPlaylistFocus=$isPlaylistFocus
            isSongFocus=$isSongFocus
            genre=$genre
            artist=$artist
            album=$album
            playlist=$playlist
            song=$song""".trimIndent())
    }
}
