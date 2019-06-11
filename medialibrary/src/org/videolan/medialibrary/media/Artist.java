package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.AMedialibrary;
import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AArtist;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;

import androidx.annotation.NonNull;

@SuppressWarnings("JniMissingFunction")
public class Artist extends AArtist {

    public Artist(long id, String name, String shortBio, String artworkMrl, String musicBrainzId) {
        super(id, name, shortBio, artworkMrl, musicBrainzId);
    }

    public Artist(Parcel in) {
        super(in);
    }

    public AAlbum[] getAlbums(int sort, boolean desc) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbums(ml, mId, sort, desc) : new AAlbum[0];
    }

    @NonNull
    public AAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedAlbums(ml, mId, sort, desc, nbItems, offset) : new AAlbum[0];
    }

    public AAlbum[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSearchAlbums(ml, mId, query, sort, desc, nbItems, offset) : new AAlbum[0];
    }

    public int searchAlbumsCount(String query) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchAlbumCount(ml, mId, query) : 0;
    }

    public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : AMedialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    public int getAlbumsCount() {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbumsCount(ml, mId) : 0;
    }

    public AMediaWrapper[] getTracks(int sort, boolean desc) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetMedia(ml, mId, sort, desc) : AMedialibrary.EMPTY_COLLECTION;
    }

    public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedMedia(ml, mId, sort, desc, nbItems, offset) : AMedialibrary.EMPTY_COLLECTION;
    }

    @Override
    public int getTracksCount() {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    private native AAlbum[] nativeGetAlbums(AMedialibrary ml, long mId, int sort, boolean desc);
    private native AMediaWrapper[] nativeGetMedia(AMedialibrary ml, long mId, int sort, boolean desc);
    private native AAlbum[] nativeGetPagedAlbums(AMedialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native AMediaWrapper[] nativeGetPagedMedia(AMedialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native AAlbum[] nativeSearchAlbums(AMedialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native AMediaWrapper[] nativeSearch(AMedialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(AMedialibrary ml, long mId);
    private native int nativeGetAlbumsCount(AMedialibrary ml, long mId);
    private native int nativeGetSearchCount(AMedialibrary ml, long mId, String query);
    private native int nativeGetSearchAlbumCount(AMedialibrary ml, long mId, String query);
}
