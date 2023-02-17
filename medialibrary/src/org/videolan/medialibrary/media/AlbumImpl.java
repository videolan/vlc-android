package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.Album;
import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class AlbumImpl extends Album {
    public static final String TAG = "VLC/Album";

    public AlbumImpl(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, int nbPresentTracks, long duration, boolean isFavorite) {
        super(id, title, releaseYear, artworkMrl, albumArtist, albumArtistId, nbTracks, nbPresentTracks, duration, isFavorite);
    }

    public AlbumImpl(Parcel in) {
        super(in);
    }

    public int getRealTracksCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    public MediaWrapper[] getTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId, sort, desc, includeMissing, onlyFavorites) : Medialibrary.EMPTY_COLLECTION;
    }

    @Override
    public MediaWrapper[] getPagedTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, sort, desc, includeMissing, onlyFavorites, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, includeMissing, onlyFavorites, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    public Artist retrieveAlbumArtist() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.getArtist(this.albumArtistId);
    }

    @Override
    public boolean setFavorite(boolean favorite) {
        if (mId == 0L) return false;
        final Medialibrary ml = Medialibrary.getInstance();
        boolean ret = false;
        if (ml.isInitiated())
            ret = nativeSetFavorite(ml, mId, favorite);
        return ret;
    }

    private native MediaWrapper[] nativeGetTracks(Medialibrary ml, long mId, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    private native MediaWrapper[] nativeGetPagedTracks(Medialibrary ml, long mId, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    private native int nativeGetTracksCount(Medialibrary ml, long id);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query);
    private native boolean nativeSetFavorite(Medialibrary ml, long id, boolean favorite);
}
