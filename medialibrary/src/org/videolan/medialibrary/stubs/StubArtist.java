package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AArtist;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;
import org.videolan.medialibrary.media.Album;

public class StubArtist extends AArtist {

    public StubArtist(long id, String name, String shortBio, String artworkMrl, String musicBrainzId) {
        super(id, name, shortBio, artworkMrl, musicBrainzId);
    }


    public StubArtist(Parcel in) {
        super(in);
    }

    public Album[] getAlbums(int sort, boolean desc) {
        return null;
    }

    public Album[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public Album[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int searchAlbumsCount(String query) {
        return 0;
    }

    public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int searchTracksCount(String query) {
        return 0;
    }

    public int getAlbumsCount() {
        return 0;
    }

    public AMediaWrapper[] getTracks(int sort, boolean desc) {
        return null;
    }

    public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int getTracksCount() {
        return 0;
    }
}
