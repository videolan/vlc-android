package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.media.Album;
import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

import java.util.ArrayList;
import java.util.Arrays;

public class StubArtist extends Artist {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubArtist(long id, String name, String shortBio, String artworkMrl, String musicBrainzId, int albumsCount, int tracksCount, int presentTracksCount, boolean isFavorite) {
        super(id, name, shortBio, artworkMrl, musicBrainzId, albumsCount, tracksCount, presentTracksCount, isFavorite);
    }

    public StubArtist(Parcel in) {
        super(in);
    }

    private ArrayList<String> getAlbumNames() {
        ArrayList<String> results = new ArrayList<>();
        for (MediaWrapper media : getTracks()) {
            if (!results.contains(media.getAlbum())) {
                results.add(media.getAlbum());
            }
        }
        return results;
    }

    public Album[] getAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        ArrayList<String> albumNames = getAlbumNames();
        ArrayList<Album> results = new ArrayList<>();
        for (Album album : dt.mAlbums) {
            if (albumNames.contains(album.getTitle()) &&
                    album.retrieveAlbumArtist().getTitle().equals(this.getTitle())) {
                results.add(album);
            }
        }
        return dt.sortAlbum(results, sort, desc);
    }


    public Album[] getPagedAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<Album> results = new ArrayList<>(Arrays.asList(getAlbums(sort, desc, includeMissing, onlyFavorites)));
        return results.toArray(new Album[0]);
    }

    public Album[] searchAlbums(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<String> albumNames = getAlbumNames();
        ArrayList<Album> results = new ArrayList<>();
        for (Album album : dt.mAlbums) {
            if (albumNames.contains(album.getTitle()) &&
                    album.retrieveAlbumArtist().getTitle().equals(this.getTitle()) &&
                    Tools.hasSubString(album.getTitle(), query)) {
                results.add(album);
            }
        }
        return dt.sortAlbum(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int searchAlbumsCount(String query) {
        int count = 0;
        ArrayList<String> albumNames = getAlbumNames();
        for (Album album : dt.mAlbums) {
            if (albumNames.contains(album.getDescription()) &&
                    album.retrieveAlbumArtist().getTitle().equals(this.getTitle()) &&
                    Tools.hasSubString(album.getTitle(), query)) {
                count++;
            }
        }
        return count;
    }

    public int searchTracksCount(String query) {
        int count = 0;
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) &&
                    media.getAlbumArtist().equals(this.getTitle()) &&
                    Tools.hasSubString(media.getTitle(), query)) {
                count++;
            }
        }
        return count;
    }

    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) &&
                    media.getAlbumArtist().equals(this.getTitle()) &&
                    Tools.hasSubString(media.getTitle(), query)) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public MediaWrapper[] getTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle())) {
                results.add(media);
            }
        }
        return dt.sortMedia(results, sort, desc);
    }

    public MediaWrapper[] getPagedTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle())) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getTracksCount() {
        int count = 0;
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) &&
                    media.getAlbumArtist().equals(this.getTitle())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean setFavorite(boolean favorite) { return true; }
}
