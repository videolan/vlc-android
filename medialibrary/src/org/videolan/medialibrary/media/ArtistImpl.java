package org.videolan.medialibrary.media;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.Album;
import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class ArtistImpl extends Artist {

    public ArtistImpl(long id, String name, String shortBio, String artworkMrl, String musicBrainzId) {
        super(id, name, shortBio, artworkMrl, musicBrainzId);
    }

    public ArtistImpl(Parcel in) {
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

    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    public int getAlbumsCount() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbumsCount(ml, mId) : 0;
    }

    public MediaWrapper[] getTracks(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetMedia(ml, mId, sort, desc) : Medialibrary.EMPTY_COLLECTION;
    }

    public MediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedMedia(ml, mId, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    @Override
    public int getTracksCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    private native Album[] nativeGetAlbums(Medialibrary ml, long mId, int sort, boolean desc);
    private native MediaWrapper[] nativeGetMedia(Medialibrary ml, long mId, int sort, boolean desc);
    private native Album[] nativeGetPagedAlbums(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeGetPagedMedia(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native Album[] nativeSearchAlbums(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(Medialibrary ml, long mId);
    private native int nativeGetAlbumsCount(Medialibrary ml, long mId);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query);
    private native int nativeGetSearchAlbumCount(Medialibrary ml, long mId, String query);
}
