package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.AbstractMedialibrary;
import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class Album extends AbstractAlbum {
    public static final String TAG = "VLC/Album";

    public Album(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, int duration) {
        super(id, title, releaseYear, artworkMrl, albumArtist, albumArtistId, nbTracks, duration);
    }

    public Album(Parcel in) {
        super(in);
    }

    public int getRealTracksCount() {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    public AbstractMediaWrapper[] getTracks(int sort, boolean desc) {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId, sort, desc) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    public AbstractMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, sort, desc, nbItems, offset) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    public AbstractMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    public AbstractArtist getAlbumArtist() {
        //TODO
        return null;
    }

    private native AbstractMediaWrapper[] nativeGetTracks(AbstractMedialibrary ml, long mId, int sort, boolean desc);
    private native AbstractMediaWrapper[] nativeGetPagedTracks(AbstractMedialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native AbstractMediaWrapper[] nativeSearch(AbstractMedialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(AbstractMedialibrary ml, long id);
    private native int nativeGetSearchCount(AbstractMedialibrary ml, long mId, String query);
}
