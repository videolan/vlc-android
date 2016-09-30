package org.videolan.medialibrary.media;

import org.videolan.medialibrary.Tools;

public class SearchAggregate {

    private final Album[] albums;
    private final Artist[] artists;
    private final Genre[] genres;
    private final MediaSearchAggregate mediaSearchAggregate;
    private final Playlist[] playlists;

    public SearchAggregate() {
        this.albums = null;
        this.artists = null;
        this.genres = null;
        this.mediaSearchAggregate = null;
        this.playlists = null;}

    public SearchAggregate(Album[] albums, Artist[] artists, Genre[] genres, MediaSearchAggregate mediaSearchAggregate, Playlist[] playlists) {
        this.albums = albums;
        this.artists = artists;
        this.genres = genres;
        this.mediaSearchAggregate = mediaSearchAggregate;
        this.playlists = playlists;
    }

    public Album[] getAlbums() {
        return albums;
    }

    public Artist[] getArtists() {
        return artists;
    }

    public Genre[] getGenres() {
        return genres;
    }

    public MediaSearchAggregate getMediaSearchAggregate() {
        return mediaSearchAggregate;
    }

    public Playlist[] getPlaylists() {
        return playlists;
    }

    public boolean isEmpty() {
        return Tools.isArrayEmpty(albums) && Tools.isArrayEmpty(artists) && Tools.isArrayEmpty(genres) && Tools.isArrayEmpty(playlists) && (mediaSearchAggregate == null || mediaSearchAggregate.isEmpty());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (albums.length > 0) {
            sb.append("Albums:\n");
            for (Album album : albums)
                sb.append(album.getTitle()).append("\n");
        }
        if (artists.length > 0) {
            sb.append("Artists:\n");
            for (Artist artist : artists)
                sb.append(artist.getTitle()).append("\n");
        }
        if (genres.length > 0) {
            sb.append("Genres:\n");
            for (Genre genre : genres)
                sb.append(genre.getTitle()).append("\n");
        }
        if (mediaSearchAggregate != null)
            sb.append(mediaSearchAggregate.toString());
        if (playlists.length > 0) {
            sb.append("Playlists:\n");
            for (Playlist playlist : playlists)
                sb.append(playlist.getTitle()).append("\n");
        }
        return sb.toString();
    }
}
