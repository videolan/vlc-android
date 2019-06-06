package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;

public class StubAlbum extends AAlbum {

    public StubAlbum(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, int duration) {
        super(id, title, releaseYear, artworkMrl, albumArtist, albumArtistId, nbTracks, duration);
    }

    public StubAlbum(Parcel in) {
        super(in);
    }

    public int getRealTracksCount() {
        return 0;
    }

    public AMediaWrapper[] getTracks(int sort, boolean desc) {
        return null;
    }

    public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int searchTracksCount(String query) {
        return 0;
    }
}
