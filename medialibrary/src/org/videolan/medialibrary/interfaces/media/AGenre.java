package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.ServiceLocator;
import org.videolan.medialibrary.interfaces.AMedialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;

public abstract class AGenre extends MediaLibraryItem {

    public AGenre(long id, String title) { super(id, title); }
    public AGenre(Parcel in) { super(in); }

    abstract public AAlbum[] getAlbums(int sort, boolean desc);
    abstract public AAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset);
    abstract public AArtist[] getArtists(int sort, boolean desc);
    abstract public AMediaWrapper[] getTracks(int sort, boolean desc);
    abstract public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset);
    abstract public int getTracksCount();
    abstract public int getAlbumsCount();
    abstract public AAlbum[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchAlbumsCount(String query);
    abstract public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchTracksCount(String query);

    public AAlbum[] getAlbums() {
        return getAlbums(AMedialibrary.SORT_DEFAULT, false);
    }
    public AArtist[] getArtists() {
        return getArtists(AMedialibrary.SORT_DEFAULT, false);
    }
    public AMediaWrapper[] getTracks() {
        return getTracks(AMedialibrary.SORT_ALBUM, false);
    }
    @Override
    public int getItemType() {
        return TYPE_GENRE;
    }

    public static Parcelable.Creator<AGenre> CREATOR
            = new Parcelable.Creator<AGenre>() {
        @Override
        public AGenre createFromParcel(Parcel in) {
            return ServiceLocator.getAGenre(in);
        }

        @Override
        public AGenre[] newArray(int size) {
            return new AGenre[size];
        }
    };

}
