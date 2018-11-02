package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

import androidx.annotation.NonNull;

@SuppressWarnings("JniMissingFunction")
public class Genre extends MediaLibraryItem {

    public Genre(long id, String title) {
        super(id, title);
    }

    public Album[] getAlbums() {
        return getAlbums(Medialibrary.SORT_DEFAULT, false);
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

    public Artist[] getArtists() {
        return getArtists(Medialibrary.SORT_DEFAULT, false);
    }

    public Artist[] getArtists(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetArtists(ml, mId, sort, desc) : new Artist[0];
    }

    public MediaWrapper[] getTracks() {
        return getTracks(Medialibrary.SORT_DEFAULT, false);
    }

    public MediaWrapper[] getTracks(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId, sort, desc) : Medialibrary.EMPTY_COLLECTION;
    }

    public MediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int getTracksCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    public int getAlbumsCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetAlbumsCount(ml, mId) : 0;
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

    @Override
    public int getItemType() {
        return TYPE_GENRE;
    }

    private native Album[] nativeGetAlbums(Medialibrary ml, long mId, int sort, boolean desc);
    private native Artist[] nativeGetArtists(Medialibrary ml, long mId, int sort, boolean desc);
    private native MediaWrapper[] nativeGetTracks(Medialibrary ml, long mId, int sort, boolean desc);

    private native Album[] nativeGetPagedAlbums(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native Artist[] nativeGetPagedArtists(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeGetPagedTracks(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(Medialibrary ml, long id);
    private native int nativeGetAlbumsCount(Medialibrary ml, long mId);
    private native int nativeGetArtistsCount(Medialibrary ml, long mId);
    private native Album[] nativeSearchAlbums(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query);
    private native int nativeGetSearchAlbumCount(Medialibrary ml, long mId, String query);

    public static Parcelable.Creator<Genre> CREATOR
            = new Parcelable.Creator<Genre>() {
        public Genre createFromParcel(Parcel in) {
            return new Genre(in);
        }

        public Genre[] newArray(int size) {
            return new Genre[size];
        }
    };
    public Genre(Parcel in) {
        super(in);
    }
}
