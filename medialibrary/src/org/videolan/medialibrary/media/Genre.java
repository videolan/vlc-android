package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

public class Genre extends MediaLibraryItem {

    public Genre(long id, String title) {
        super(id, title);
    }

    public Album[] getAlbums() {
        return getAlbums(Medialibrary.SORT_DEFAULT, false);
    }

    public Album[] getAlbums(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() ? nativeGetAlbumsFromGenre(ml, mId, sort, desc) : new Album[0];
    }

    public Artist[] getArtists() {
        return getArtists(Medialibrary.SORT_DEFAULT, false);
    }

    public Artist[] getArtists(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() ? nativeGetArtistsFromGenre(ml, mId, sort, desc) : new Artist[0];
    }

    public MediaWrapper[] getTracks() {
        return getTracks(Medialibrary.SORT_DEFAULT, false);
    }

    public MediaWrapper[] getTracks(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() ? nativeGetTracksFromGenre(ml, mId, sort, desc) : Medialibrary.EMPTY_COLLECTION;
    }

    @Override
    public int getItemType() {
        return TYPE_GENRE;
    }

    private native Album[] nativeGetAlbumsFromGenre(Medialibrary ml, long mId, int sort, boolean desc);
    private native Artist[] nativeGetArtistsFromGenre(Medialibrary ml, long mId, int sort, boolean desc);
    private native MediaWrapper[] nativeGetTracksFromGenre(Medialibrary ml, long mId, int sort, boolean desc);

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
