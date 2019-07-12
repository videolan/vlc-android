package org.videolan.medialibrary.media;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.videolan.medialibrary.interfaces.AbstractMedialibrary;
import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractGenre;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class Genre extends AbstractGenre {

    public Genre(long id, String title) {
        super(id, title);
    }


    public AbstractAlbum[] getAlbums(int sort, boolean desc) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbums(ml, mId, sort, desc) : new AbstractAlbum[0];
    }

    @NonNull
    public AbstractAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedAlbums(ml, mId, sort, desc, nbItems, offset) : new AbstractAlbum[0];
    }

    public AbstractArtist[] getArtists(int sort, boolean desc) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetArtists(ml, mId, sort, desc) : new AbstractArtist[0];
    }

    public AbstractMediaWrapper[] getTracks(boolean withThumbnail, int sort, boolean desc) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId, withThumbnail, sort, desc) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    public AbstractMediaWrapper[] getPagedTracks(boolean withThumbnail, int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, withThumbnail, sort, desc, nbItems, offset) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    @Override
    public int getTracksCount() {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    public int getAlbumsCount() {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbumsCount(ml, mId) : 0;
    }

    public AbstractAlbum[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSearchAlbums(ml, mId, query, sort, desc, nbItems, offset) : new AbstractAlbum[0];
    }

    public int searchAlbumsCount(String query) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchAlbumCount(ml, mId, query) : 0;
    }

    public AbstractMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    private native AbstractAlbum[] nativeGetAlbums(AbstractMedialibrary ml, long mId, int sort, boolean desc);
    private native AbstractArtist[] nativeGetArtists(AbstractMedialibrary ml, long mId, int sort, boolean desc);
    private native AbstractMediaWrapper[] nativeGetTracks(AbstractMedialibrary ml, long mId, boolean withThumbnail, int sort, boolean desc);

    private native AbstractAlbum[] nativeGetPagedAlbums(AbstractMedialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native AbstractArtist[] nativeGetPagedArtists(AbstractMedialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native AbstractMediaWrapper[] nativeGetPagedTracks(AbstractMedialibrary ml, long mId, boolean withThumbnail, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(AbstractMedialibrary ml, long id);
    private native int nativeGetAlbumsCount(AbstractMedialibrary ml, long mId);
    private native int nativeGetArtistsCount(AbstractMedialibrary ml, long mId);
    private native AbstractAlbum[] nativeSearchAlbums(AbstractMedialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native AbstractMediaWrapper[] nativeSearch(AbstractMedialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(AbstractMedialibrary ml, long mId, String query);
    private native int nativeGetSearchAlbumCount(AbstractMedialibrary ml, long mId, String query);

    public Genre(Parcel in) {
        super(in);
    }
}
