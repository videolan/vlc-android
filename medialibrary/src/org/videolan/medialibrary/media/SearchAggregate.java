package org.videolan.medialibrary.media;

import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AArtist;
import org.videolan.medialibrary.interfaces.media.AGenre;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;

public class SearchAggregate {
    private static final String TAG = "VLC/SearchAggregate";

    private final AAlbum[] albums;
    private final AArtist[] artists;
    private final AGenre[] genres;
    private final AMediaWrapper[] videos;
    private final AMediaWrapper[] tracks;
    private final Playlist[] playlists;

    public SearchAggregate() {
        this.albums = null;
        this.artists = null;
        this.genres = null;
        this.videos = null;
        this.tracks = null;
        this.playlists = null;
    }

    public SearchAggregate(AAlbum[] albums, AArtist[] artists, AGenre[] genres, AMediaWrapper[] videos, AMediaWrapper[] tracks, Playlist[] playlists) {
        this.albums = albums;
        this.artists = artists;
        this.genres = genres;
        this.videos = videos;
        this.tracks = tracks;
        this.playlists = playlists;
    }

    public AAlbum[] getAlbums() {
        return albums;
    }

    public AArtist[] getArtists() {
        return artists;
    }

    public AGenre[] getGenres() {
        return genres;
    }

    public AMediaWrapper[] getVideos() {
        return videos;
    }

    public AMediaWrapper[] getTracks() {
        return tracks;
    }

    public Playlist[] getPlaylists() {
        return playlists;
    }

    public boolean isEmpty() {
        return Tools.isArrayEmpty(videos) && Tools.isArrayEmpty(tracks) && Tools.isArrayEmpty(albums) && Tools.isArrayEmpty(artists) && Tools.isArrayEmpty(genres) && Tools.isArrayEmpty(playlists);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (albums.length > 0) {
            sb.append("Albums:\n");
            for (AAlbum album : albums)
                sb.append(album.getTitle()).append("\n");
        }
        if (artists.length > 0) {
            sb.append("Artists:\n");
            for (AArtist artist : artists)
                sb.append(artist.getTitle()).append("\n");
        }
        if (genres.length > 0) {
            sb.append("Genres:\n");
            for (AGenre genre : genres)
                sb.append(genre.getTitle()).append("\n");
        }
        if (tracks.length > 0) {
            sb.append("Tracks:\n");
            for (AMediaWrapper m : tracks)
                sb.append(m.getTitle()).append("\n");
        }
        if (videos.length > 0) {
            sb.append("Videos:\n");
            for (AMediaWrapper m : videos)
                sb.append(m.getTitle()).append("\n");
        }
        if (playlists.length > 0) {
            sb.append("Playlists:\n");
            for (Playlist playlist : playlists)
                sb.append(playlist.getTitle()).append("\n");
        }
        return sb.toString();
    }
}
