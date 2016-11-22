/*
 * ************************************************************************
 *  BrowserProvider.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
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
 *
 *  *************************************************************************
 */

package org.videolan.vlc.media;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.util.Strings;

import java.util.ArrayList;
import java.util.List;


public class BrowserProvider {

    private static final String TAG = "VLC/BrowserProvider";

    private static final Bitmap DEFAULT_AUDIO_COVER = BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_menu_audio);
    private static final String BASE_DRAWABLE_URI = "android.resource://"+VLCApplication.getAppContext().getPackageName()+"/drawable/";

    public static final String ID_ROOT = "ID_ROOT";
    private static final String ID_ARTISTS = "ID_ARTISTS";
    private static final String ID_ALBUMS = "ID_ALBUMS";
    private static final String ID_SONGS = "ID_SONGS";
    private static final String ID_GENRES = "ID_GENRES";
    private static final String ID_PLAYLISTS = "ID_PLAYLISTS";
    private static final String ID_HISTORY = "ID_HISTORY";
    public static final String ALBUM_PREFIX = "album";
    private static final String ARTIST_PREFIX = "artist";
    private static final String GENRE_PREFIX = "genre";
    public static final String PLAYLIST_PREFIX = "playlist";

    public static List<MediaBrowserCompat.MediaItem> browse(String parentId) {
        ArrayList<MediaBrowserCompat.MediaItem> results = new ArrayList<>();
        MediaLibraryItem[] list = null;
        Resources res = VLCApplication.getAppResources();
        switch (parentId) {
            case ID_ROOT:
                //Playlists
                MediaDescriptionCompat.Builder item = new MediaDescriptionCompat.Builder()
                        .setMediaId(ID_HISTORY)
                        .setTitle(res.getString(R.string.history))
                        .setIconUri(Uri.parse(BASE_DRAWABLE_URI+"ic_auto_history_normal"));
                results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                //Playlists
                item.setMediaId(ID_PLAYLISTS)
                        .setTitle(res.getString(R.string.playlists))
                        .setIconUri(Uri.parse(BASE_DRAWABLE_URI+"ic_auto_playlist_normal"));
                results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                //Artists
                item.setMediaId(ID_ARTISTS)
                        .setTitle(res.getString(R.string.artists))
                        .setIconUri(Uri.parse(BASE_DRAWABLE_URI+"ic_auto_artist_normal"));
                results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                //Albums
                item.setMediaId(ID_ALBUMS)
                        .setTitle(res.getString(R.string.albums))
                        .setIconUri(Uri.parse(BASE_DRAWABLE_URI+"ic_auto_album_normal"));
                results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                //Songs
                item.setMediaId(ID_SONGS)
                        .setTitle(res.getString(R.string.songs))
                        .setIconUri(Uri.parse(BASE_DRAWABLE_URI+"ic_auto_audio_normal"));
                results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                //Genres
                item.setMediaId(ID_GENRES)
                        .setTitle(res.getString(R.string.genres))
                        .setIconUri(Uri.parse(BASE_DRAWABLE_URI+"ic_auto_genre_normal"));
                results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                return results;
            case ID_HISTORY:
                list = VLCApplication.getMLInstance().lastMediaPlayed();
                break;
            case ID_ARTISTS:
                list = VLCApplication.getMLInstance().getArtists();
                break;
            case ID_ALBUMS:
                list = VLCApplication.getMLInstance().getAlbums();
                break;
            case ID_GENRES:
                list = VLCApplication.getMLInstance().getGenres();
                break;
            case ID_PLAYLISTS:
                list = VLCApplication.getMLInstance().getPlaylists();
                break;
            case ID_SONGS:
                list = VLCApplication.getMLInstance().getAudio();
                break;
            default:
                String[] idSections = parentId.split("_");
                Medialibrary ml = VLCApplication.getMLInstance();
                long id = Long.parseLong(idSections[1]);
                switch (idSections[0]) {
                    case ARTIST_PREFIX:
                        list = ml.getArtist(id).getAlbums(ml);
                        break;
                    case GENRE_PREFIX:
                        list = ml.getGenre(id).getAlbums(ml);
                        break;
                }
        }
        if (list != null) {
            MediaDescriptionCompat.Builder item = new MediaDescriptionCompat.Builder();
            for (MediaLibraryItem libraryItem : list) {
                if (libraryItem.getItemType() == MediaLibraryItem.TYPE_MEDIA && ((MediaWrapper)libraryItem).getType() != MediaWrapper.TYPE_AUDIO)
                    continue;
                Bitmap cover = AudioUtil.readCoverBitmap(Strings.removeFileProtocole(Uri.decode(libraryItem.getArtworkMrl())), 128);
                if (cover == null)
                    cover = DEFAULT_AUDIO_COVER;
                item.setTitle(libraryItem.getTitle())
                        .setMediaId(generateMediaId(libraryItem));
                item.setIconBitmap(cover);
                if (libraryItem.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
                    item.setMediaUri(((MediaWrapper) libraryItem).getUri())
                            .setSubtitle(MediaUtils.getMediaSubtitle((MediaWrapper) libraryItem));
                } else
                    item.setSubtitle(libraryItem.getDescription());
                boolean playable = libraryItem.getItemType() == MediaLibraryItem.TYPE_MEDIA ||
                        libraryItem.getItemType() == MediaLibraryItem.TYPE_ALBUM ||
                        libraryItem.getItemType() == MediaLibraryItem.TYPE_PLAYLIST;
                results.add(new MediaBrowserCompat.MediaItem(item.build(), playable ? MediaBrowserCompat.MediaItem.FLAG_PLAYABLE : MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
            }
        }
        return results;
    }

    @NonNull
    public static String generateMediaId(MediaLibraryItem libraryItem) {
        String prefix;
        switch (libraryItem.getItemType()) {
            case MediaLibraryItem.TYPE_ALBUM:
                prefix = ALBUM_PREFIX;
                break;
            case MediaLibraryItem.TYPE_ARTIST:
                prefix = ARTIST_PREFIX;
                break;
            case MediaLibraryItem.TYPE_GENRE:
                prefix = GENRE_PREFIX;
                break;
            case MediaLibraryItem.TYPE_PLAYLIST:
                prefix = PLAYLIST_PREFIX;
                break;
            default:
                return String.valueOf(libraryItem.getId());
        }
        return prefix+"_"+libraryItem.getId();
    }
}
