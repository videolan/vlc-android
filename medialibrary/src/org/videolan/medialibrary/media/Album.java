package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.AMedialibrary;
import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AArtist;
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
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    public AMediaWrapper[] getTracks(int sort, boolean desc) {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId, sort, desc) : AMedialibrary.EMPTY_COLLECTION;
    }

    public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, sort, desc, nbItems, offset) : AMedialibrary.EMPTY_COLLECTION;
    }

    public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : AMedialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    public AArtist getAlbumArtist() {
        //TODO
        return null;
    }

    private native AMediaWrapper[] nativeGetTracks(AMedialibrary ml, long mId, int sort, boolean desc);
    private native AMediaWrapper[] nativeGetPagedTracks(AMedialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native AMediaWrapper[] nativeSearch(AMedialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(AMedialibrary ml, long id);
    private native int nativeGetSearchCount(AMedialibrary ml, long mId, String query);
}
