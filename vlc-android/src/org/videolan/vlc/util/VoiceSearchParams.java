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
package org.videolan.vlc.util;

import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;


import org.videolan.libvlc.util.AndroidUtil;

public class VoiceSearchParams {

    public final String query;
    public boolean isAny;
    public boolean isUnstructured;
    public boolean isGenreFocus;
    public boolean isArtistFocus;
    public boolean isAlbumFocus;
    public boolean isSongFocus;
    public String genre;
    public String artist;
    public String album;
    public String song;

    public VoiceSearchParams(String query, Bundle extras) {
        this.query = query;
        if (TextUtils.isEmpty(query)) {
            isAny = true;
        } else if (extras == null || !extras.containsKey(MediaStore.EXTRA_MEDIA_FOCUS)) {
            isUnstructured = true;
        } else {
            String genreKey = AndroidUtil.isLolliPopOrLater
                    ? MediaStore.EXTRA_MEDIA_GENRE : "android.intent.extra.genre";
            switch (extras.getString(MediaStore.EXTRA_MEDIA_FOCUS)) {
                case MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE:
                    isGenreFocus = true;
                    genre = extras.getString(genreKey);
                    if (TextUtils.isEmpty(genre))
                        genre = query;
                    break;
                case MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE:
                    isArtistFocus = true;
                    genre = extras.getString(genreKey);
                    artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST);
                    break;
                case MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE:
                    isAlbumFocus = true;
                    album = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM);
                    genre = extras.getString(genreKey);
                    artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST);
                    break;
                case MediaStore.Audio.Media.ENTRY_CONTENT_TYPE:
                    isSongFocus = true;
                    song = extras.getString(MediaStore.EXTRA_MEDIA_TITLE);
                    if (song.contains("("))
                        song = song.substring(0, song.indexOf('('));
                    album = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM);
                    genre = extras.getString(genreKey);
                    artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST);
            }
        }
    }

    @Override
    public String toString() {
        return "query=" + query
            + " isAny=" + isAny
            + " isUnstructured=" + isUnstructured
            + " isGenreFocus=" + isGenreFocus
            + " isArtistFocus=" + isArtistFocus
            + " isAlbumFocus=" + isAlbumFocus
            + " isSongFocus=" + isSongFocus
            + " genre=" + genre
            + " artist=" + artist
            + " album=" + album
            + " song=" + song;
    }
}
