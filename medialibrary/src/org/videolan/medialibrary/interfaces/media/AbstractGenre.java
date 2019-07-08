package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.interfaces.AbstractMedialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;

public abstract class AbstractGenre extends MediaLibraryItem {

    public AbstractGenre(long id, String title) { super(id, title); }
    public AbstractGenre(Parcel in) { super(in); }

    abstract public AbstractAlbum[] getAlbums(int sort, boolean desc);
    abstract public AbstractAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset);
    abstract public AbstractArtist[] getArtists(int sort, boolean desc);
    abstract public AbstractMediaWrapper[] getTracks(boolean withThumbnail, int sort, boolean desc);
    abstract public AbstractMediaWrapper[] getPagedTracks(boolean withThumbnail, int sort, boolean desc, int nbItems, int offset);
    abstract public int getTracksCount();
    abstract public int getAlbumsCount();
    abstract public AbstractAlbum[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchAlbumsCount(String query);
    abstract public AbstractMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchTracksCount(String query);

    public AbstractMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        return getPagedTracks(false, sort, desc, nbItems, offset);
    }

    public AbstractMediaWrapper[] getTracks(int sort, boolean desc) {
        return getTracks(false, sort, desc);
    }

    public AbstractAlbum[] getAlbums() {
        return getAlbums(AbstractMedialibrary.SORT_DEFAULT, false);
    }
    public AbstractArtist[] getArtists() {
        return getArtists(AbstractMedialibrary.SORT_DEFAULT, false);
    }
    public AbstractMediaWrapper[] getTracks() {
        return getTracks(false, AbstractMedialibrary.SORT_ALBUM, false);
    }
    @Override
    public int getItemType() {
        return TYPE_GENRE;
    }

    public static Parcelable.Creator<AbstractGenre> CREATOR
            = new Parcelable.Creator<AbstractGenre>() {
        @Override
        public AbstractGenre createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractGenre(in);
        }

        @Override
        public AbstractGenre[] newArray(int size) {
            return new AbstractGenre[size];
        }
    };

}
