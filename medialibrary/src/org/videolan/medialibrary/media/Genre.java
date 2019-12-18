package org.videolan.medialibrary.media;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractGenre;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class Genre extends AbstractGenre {

    public Genre(long id, String title) {
        super(id, title);
    }


    public AbstractAlbum[] getAlbums(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbums(ml, mId, sort, desc) : new AbstractAlbum[0];
    }

    @NonNull
    public AbstractAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedAlbums(ml, mId, sort, desc, nbItems, offset) : new AbstractAlbum[0];
    }

    public AbstractArtist[] getArtists(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetArtists(ml, mId, sort, desc) : new AbstractArtist[0];
    }

    public MediaWrapper[] getTracks(boolean withThumbnail, int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId, withThumbnail, sort, desc) : Medialibrary.EMPTY_COLLECTION;
    }

    public MediaWrapper[] getPagedTracks(boolean withThumbnail, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, withThumbnail, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    @Override
    public int getTracksCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    public int getAlbumsCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbumsCount(ml, mId) : 0;
    }

    public AbstractAlbum[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearchAlbums(ml, mId, query, sort, desc, nbItems, offset) : new AbstractAlbum[0];
    }

    public int searchAlbumsCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchAlbumCount(ml, mId, query) : 0;
    }

    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    private native AbstractAlbum[] nativeGetAlbums(Medialibrary ml, long mId, int sort, boolean desc);
    private native AbstractArtist[] nativeGetArtists(Medialibrary ml, long mId, int sort, boolean desc);
    private native MediaWrapper[] nativeGetTracks(Medialibrary ml, long mId, boolean withThumbnail, int sort, boolean desc);

    private native AbstractAlbum[] nativeGetPagedAlbums(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native AbstractArtist[] nativeGetPagedArtists(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeGetPagedTracks(Medialibrary ml, long mId, boolean withThumbnail, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(Medialibrary ml, long id);
    private native int nativeGetAlbumsCount(Medialibrary ml, long mId);
    private native int nativeGetArtistsCount(Medialibrary ml, long mId);
    private native AbstractAlbum[] nativeSearchAlbums(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query);
    private native int nativeGetSearchAlbumCount(Medialibrary ml, long mId, String query);

    public Genre(Parcel in) {
        super(in);
    }
}
