package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class Album extends AAlbum {
    public static final String TAG = "VLC/Album";

    public Album(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, int duration) {
        super(id, title, releaseYear, artworkMrl, albumArtist, albumArtistId, nbTracks, duration);
    }

    public Album(Parcel in) {
        super(in);
    }

    public int getRealTracksCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    public AMediaWrapper[] getTracks(int sort, boolean desc) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId, sort, desc) : Medialibrary.EMPTY_COLLECTION;
    }

    public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    private native AMediaWrapper[] nativeGetTracks(Medialibrary ml, long mId, int sort, boolean desc);
    private native AMediaWrapper[] nativeGetPagedTracks(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native AMediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(Medialibrary ml, long id);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query);
}
