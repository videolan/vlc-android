package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

public class Genre extends MediaLibraryItem {

    public Genre(long id, String title) {
        super(id, title);
    }

    public Album[] getAlbums(Medialibrary ml) {
       return nativeGetAlbumsFromGenre(ml, mId);
    }

    public Artist[] getArtists(Medialibrary ml) {
        return nativeGetArtistsFromGenre(ml, mId);
    }

    public MediaWrapper[] getTracks(Medialibrary ml) {
        return nativeGetTracksFromGenre(ml, mId);
    }

    private native Album[] nativeGetAlbumsFromGenre(Medialibrary ml, long mId);
    private native Artist[] nativeGetArtistsFromGenre(Medialibrary ml, long mId);
    private native MediaWrapper[] nativeGetTracksFromGenre(Medialibrary ml, long mId);

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
