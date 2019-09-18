package org.videolan.medialibrary.media;

import androidx.annotation.Nullable;

import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractGenre;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;
import org.videolan.medialibrary.interfaces.media.AbstractPlaylist;

public class SearchAggregate {
    private static final String TAG = "VLC/SearchAggregate";

    private final AbstractAlbum[] albums;
    private final AbstractArtist[] artists;
    private final AbstractGenre[] genres;
    private final AbstractMediaWrapper[] videos;
    private final AbstractMediaWrapper[] tracks;
    private final AbstractPlaylist[] playlists;

    public SearchAggregate() {
        this.albums = null;
        this.artists = null;
        this.genres = null;
        this.videos = null;
        this.tracks = null;
        this.playlists = null;
    }

    public SearchAggregate(AbstractAlbum[] albums, AbstractArtist[] artists, AbstractGenre[] genres, AbstractMediaWrapper[] videos, AbstractMediaWrapper[] tracks, AbstractPlaylist[] playlists) {
        this.albums = albums;
        this.artists = artists;
        this.genres = genres;
        this.videos = videos;
        this.tracks = tracks;
        this.playlists = playlists;
    }

    @Nullable
    public AbstractAlbum[] getAlbums() {
        return albums;
    }

    @Nullable
    public AbstractArtist[] getArtists() {
        return artists;
    }

    @Nullable
    public AbstractGenre[] getGenres() {
        return genres;
    }

    @Nullable
    public AbstractMediaWrapper[] getVideos() {
        return videos;
    }

    @Nullable
    public AbstractMediaWrapper[] getTracks() {
        return tracks;
    }

    @Nullable
    public AbstractPlaylist[] getPlaylists() {
        return playlists;
    }

    public boolean isEmpty() {
        return Tools.isArrayEmpty(videos) && Tools.isArrayEmpty(tracks) && Tools.isArrayEmpty(albums) && Tools.isArrayEmpty(artists) && Tools.isArrayEmpty(genres) && Tools.isArrayEmpty(playlists);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!Tools.isArrayEmpty(albums)) {
            sb.append("Albums:\n");
            for (AbstractAlbum album : albums)
                sb.append(album.getTitle()).append("\n");
        }
        if (!Tools.isArrayEmpty(artists)) {
            sb.append("Artists:\n");
            for (AbstractArtist artist : artists)
                sb.append(artist.getTitle()).append("\n");
        }
        if (!Tools.isArrayEmpty(genres)) {
            sb.append("Genres:\n");
            for (AbstractGenre genre : genres)
                sb.append(genre.getTitle()).append("\n");
        }
        if (!Tools.isArrayEmpty(tracks)) {
            sb.append("Tracks:\n");
            for (AbstractMediaWrapper m : tracks)
                sb.append(m.getTitle()).append("\n");
        }
        if (!Tools.isArrayEmpty(videos)) {
            sb.append("Videos:\n");
            for (AbstractMediaWrapper m : videos)
                sb.append(m.getTitle()).append("\n");
        }
        if (!Tools.isArrayEmpty(playlists)) {
            sb.append("Playlists:\n");
            for (AbstractPlaylist playlist : playlists)
                sb.append(playlist.getTitle()).append("\n");
        }
        return sb.toString();
    }
}
