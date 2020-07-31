package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;

public abstract class Genre extends MediaLibraryItem {

    public Genre(long id, String title) { super(id, title); }
    public Genre(Parcel in) { super(in); }

    abstract public Album[] getAlbums(int sort, boolean desc);
    abstract public Album[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset);
    abstract public Artist[] getArtists(int sort, boolean desc);
    abstract public MediaWrapper[] getTracks(boolean withThumbnail, int sort, boolean desc);
    abstract public MediaWrapper[] getPagedTracks(boolean withThumbnail, int sort, boolean desc, int nbItems, int offset);
    abstract public int getTracksCount();
    abstract public int getAlbumsCount();
    abstract public Album[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchAlbumsCount(String query);
    abstract public MediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchTracksCount(String query);

    public MediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        return getPagedTracks(false, sort, desc, nbItems, offset);
    }

    public MediaWrapper[] getTracks(int sort, boolean desc) {
        return getTracks(false, sort, desc);
    }

    public Album[] getAlbums() {
        return getAlbums(Medialibrary.SORT_DEFAULT, false);
    }
    public Artist[] getArtists() {
        return getArtists(Medialibrary.SORT_DEFAULT, false);
    }
    public MediaWrapper[] getTracks() {
        return getTracks(false, Medialibrary.SORT_ALBUM, false);
    }
    @Override
    public int getItemType() {
        return TYPE_GENRE;
    }

    public static Parcelable.Creator<Genre> CREATOR
            = new Parcelable.Creator<Genre>() {
        @Override
        public Genre createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractGenre(in);
        }

        @Override
        public Genre[] newArray(int size) {
            return new Genre[size];
        }
    };

}
