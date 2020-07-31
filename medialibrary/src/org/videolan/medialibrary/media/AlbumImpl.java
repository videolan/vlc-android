package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.Album;
import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class AlbumImpl extends Album {
    public static final String TAG = "VLC/Album";

    public AlbumImpl(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, long duration) {
        super(id, title, releaseYear, artworkMrl, albumArtist, albumArtistId, nbTracks, duration);
    }

    public AlbumImpl(Parcel in) {
        super(in);
    }

    public int getRealTracksCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    public MediaWrapper[] getTracks(int sort, boolean desc) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId, sort, desc) : Medialibrary.EMPTY_COLLECTION;
    }

    public MediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    public Artist getAlbumArtist() {
        //TODO
        return null;
    }

    private native MediaWrapper[] nativeGetTracks(Medialibrary ml, long mId, int sort, boolean desc);
    private native MediaWrapper[] nativeGetPagedTracks(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(Medialibrary ml, long id);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query);
}
