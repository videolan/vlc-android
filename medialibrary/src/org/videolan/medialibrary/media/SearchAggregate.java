package org.videolan.medialibrary.media;

import org.videolan.medialibrary.Tools;

public class SearchAggregate {
    private static final String TAG = "VLC/SearchAggregate";

    private final Album[] albums;
    private final Artist[] artists;
    private final Genre[] genres;
    private final MediaWrapper[] videos;
    private final MediaWrapper[] tracks;
    private final Playlist[] playlists;

    public SearchAggregate() {
        this.albums = null;
        this.artists = null;
        this.genres = null;
        this.videos = null;
        this.tracks = null;
        this.playlists = null;}

    public SearchAggregate(Album[] albums, Artist[] artists, Genre[] genres, MediaWrapper[] videos, MediaWrapper[] tracks, Playlist[] playlists) {
        this.albums = albums;
        this.artists = artists;
        this.genres = genres;
        this.videos = videos;
        this.tracks = tracks;
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

    public MediaWrapper[] getVideos() {
        return videos;
    }

    public MediaWrapper[] getTracks() {
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
        if (tracks.length > 0) {
            sb.append("Tracks:\n");
            for (MediaWrapper m : tracks)
                sb.append(m.getTitle()).append("\n");
        }
        if (videos.length > 0) {
            sb.append("Videos:\n");
            for (MediaWrapper m : videos)
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
