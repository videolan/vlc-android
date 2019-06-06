package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AArtist;
import org.videolan.medialibrary.interfaces.media.AGenre;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;
import org.videolan.medialibrary.media.Album;

public class StubGenre extends AGenre {

    public StubGenre(long id, String title) { super(id, title); }
    public StubGenre(Parcel in) { super(in); }

    public Album[] getAlbums(int sort, boolean desc) {
        return null;
    }

    public Album[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public AArtist[] getArtists(int sort, boolean desc) {
        return null;
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

    public int getAlbumsCount() {
        return 0;
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
}
