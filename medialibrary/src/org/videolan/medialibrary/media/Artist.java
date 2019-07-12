package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.AbstractMedialibrary;
import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;

import androidx.annotation.NonNull;

@SuppressWarnings("JniMissingFunction")
public class Artist extends AbstractArtist {

    public Artist(long id, String name, String shortBio, String artworkMrl, String musicBrainzId) {
        super(id, name, shortBio, artworkMrl, musicBrainzId);
    }

    public Artist(Parcel in) {
        super(in);
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

    public int getAlbumsCount() {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbumsCount(ml, mId) : 0;
    }

    public AbstractMediaWrapper[] getTracks(int sort, boolean desc) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetMedia(ml, mId, sort, desc) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    public AbstractMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedMedia(ml, mId, sort, desc, nbItems, offset) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    @Override
    public int getTracksCount() {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    private native AbstractAlbum[] nativeGetAlbums(AbstractMedialibrary ml, long mId, int sort, boolean desc);
    private native AbstractMediaWrapper[] nativeGetMedia(AbstractMedialibrary ml, long mId, int sort, boolean desc);
    private native AbstractAlbum[] nativeGetPagedAlbums(AbstractMedialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native AbstractMediaWrapper[] nativeGetPagedMedia(AbstractMedialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native AbstractAlbum[] nativeSearchAlbums(AbstractMedialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native AbstractMediaWrapper[] nativeSearch(AbstractMedialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(AbstractMedialibrary ml, long mId);
    private native int nativeGetAlbumsCount(AbstractMedialibrary ml, long mId);
    private native int nativeGetSearchCount(AbstractMedialibrary ml, long mId, String query);
    private native int nativeGetSearchAlbumCount(AbstractMedialibrary ml, long mId, String query);
}
