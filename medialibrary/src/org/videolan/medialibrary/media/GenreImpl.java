package org.videolan.medialibrary.media;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.Album;
import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.Genre;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class GenreImpl extends Genre {

    public GenreImpl(long id, String title, int nbTracks, int nbPresentTracks, boolean isFavorite) {
        super(id, title, nbTracks, nbPresentTracks, isFavorite);
    }


    public Album[] getAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbums(ml, mId, sort, desc, includeMissing, onlyFavorites) : new Album[0];
    }

    @NonNull
    public Album[] getPagedAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedAlbums(ml, mId, sort, desc, includeMissing, onlyFavorites, nbItems, offset) : new Album[0];
    }

    public Artist[] getArtists(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetArtists(ml, mId, sort, desc, includeMissing, onlyFavorites) : new Artist[0];
    }

    public MediaWrapper[] getTracks(boolean withThumbnail, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId, withThumbnail, sort, desc, includeMissing, onlyFavorites) : Medialibrary.EMPTY_COLLECTION;
    }

    public MediaWrapper[] getPagedTracks(boolean withThumbnail, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, withThumbnail, sort, desc, includeMissing, onlyFavorites, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int getAlbumsCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbumsCount(ml, mId) : 0;
    }

    public Album[] searchAlbums(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearchAlbums(ml, mId, query, sort, desc, includeMissing, onlyFavorites, nbItems, offset) : new Album[0];
    }

    public int searchAlbumsCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchAlbumCount(ml, mId, query) : 0;
    }

    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, includeMissing, onlyFavorites, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
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

    private native Album[] nativeGetAlbums(Medialibrary ml, long mId, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    private native Artist[] nativeGetArtists(Medialibrary ml, long mId, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    private native MediaWrapper[] nativeGetTracks(Medialibrary ml, long mId, boolean withThumbnail, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);

    private native Album[] nativeGetPagedAlbums(Medialibrary ml, long mId, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    private native Artist[] nativeGetPagedArtists(Medialibrary ml, long mId, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    private native MediaWrapper[] nativeGetPagedTracks(Medialibrary ml, long mId, boolean withThumbnail, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    private native int nativeGetAlbumsCount(Medialibrary ml, long mId);
    private native int nativeGetArtistsCount(Medialibrary ml, long mId);
    private native Album[] nativeSearchAlbums(Medialibrary ml, long mId, String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query);
    private native int nativeGetSearchAlbumCount(Medialibrary ml, long mId, String query);
    private native boolean nativeSetFavorite(Medialibrary ml, long id, boolean favorite);

    public GenreImpl(Parcel in) {
        super(in);
    }
}
