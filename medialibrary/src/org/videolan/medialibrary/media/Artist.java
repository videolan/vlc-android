package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.Medialibrary;
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

    public Album[] getAlbums(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbums(ml, mId, sort, desc) : new Album[0];
    }

    @NonNull
    public Album[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedAlbums(ml, mId, sort, desc, nbItems, offset) : new Album[0];
    }

    public Album[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearchAlbums(ml, mId, query, sort, desc, nbItems, offset) : new Album[0];
    }

    public int searchAlbumsCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchAlbumCount(ml, mId, query) : 0;
    }

    public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    public int getAlbumsCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbumsCount(ml, mId) : 0;
    }

    public AMediaWrapper[] getTracks(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetMedia(ml, mId, sort, desc) : Medialibrary.EMPTY_COLLECTION;
    }

    public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedMedia(ml, mId, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    @Override
    public int getTracksCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    private native Album[] nativeGetAlbums(Medialibrary ml, long mId, int sort, boolean desc);
    private native AMediaWrapper[] nativeGetMedia(Medialibrary ml, long mId, int sort, boolean desc);
    private native Album[] nativeGetPagedAlbums(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native AMediaWrapper[] nativeGetPagedMedia(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native Album[] nativeSearchAlbums(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native AMediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(Medialibrary ml, long mId);
    private native int nativeGetAlbumsCount(Medialibrary ml, long mId);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query);
    private native int nativeGetSearchAlbumCount(Medialibrary ml, long mId, String query);
}
