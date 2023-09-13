package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.MLContextTools;
import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.R;
import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;

abstract public class Artist extends MediaLibraryItem {

    private String shortBio;
    private String artworkMrl;
    private String musicBrainzId;
    private int tracksCount;
    private int albumsCount;
    private int presentTracksCount;

    public static class SpecialRes {
        public static String UNKNOWN_ARTIST = MLContextTools.getInstance().getContext().getString(R.string.unknown_artist);
        public static String VARIOUS_ARTISTS = MLContextTools.getInstance().getContext().getString(R.string.various_artists);
    }

    public Artist(long id, String name, String shortBio, String artworkMrl, String musicBrainzId, int albumsCount, int tracksCount, int presentTracksCount, boolean isFavorite) {
        super(id, name);
        this.shortBio = shortBio;
        this.artworkMrl = artworkMrl != null ? VLCUtil.UriFromMrl(artworkMrl).getPath() : null;
        this.musicBrainzId = musicBrainzId;
        this.tracksCount = tracksCount;
        this.albumsCount = albumsCount;
        this.presentTracksCount = presentTracksCount;
        this.mFavorite = isFavorite;
        if (id == 1L) {
            mTitle = SpecialRes.UNKNOWN_ARTIST;
        } else if (id == 2L) {
            mTitle = SpecialRes.VARIOUS_ARTISTS;
        }
    }

    abstract public Album[] getAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    abstract public Album[] getPagedAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public Album[] searchAlbums(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int searchAlbumsCount(String query);
    abstract public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int searchTracksCount(String query);
    abstract public MediaWrapper[] getTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    abstract public MediaWrapper[] getPagedTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);

    public String getShortBio() {
        return shortBio;
    }

    @Override
    public String getArtworkMrl() {
        return artworkMrl;
    }

    public String getMusicBrainzId() {
        return musicBrainzId;
    }

    public void setShortBio(String shortBio) {
        this.shortBio = shortBio;
    }

    public void setArtworkMrl(String artworkMrl) {
        this.artworkMrl = artworkMrl;
    }

    public Album[] getAlbums() {
        return getAlbums(Medialibrary.SORT_ALPHA, false, true, false);
    }

    @Override
    public int getTracksCount() {
        return tracksCount;
    }

    public int getAlbumsCount() {
        return albumsCount;
    }

    public int getPresentTracksCount() {
        return presentTracksCount;
    }


    @Override
    public MediaWrapper[] getTracks() {
        return getTracks(Medialibrary.SORT_ALBUM, false, true, false);
    }

    @Override
    public int getItemType() {
        return TYPE_ARTIST;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeString(shortBio);
        parcel.writeString(artworkMrl);
        parcel.writeString(musicBrainzId);
        parcel.writeInt(tracksCount);
        parcel.writeInt(albumsCount);
        parcel.writeInt(mFavorite ? 1 : 0);
    }

    public static Parcelable.Creator<Artist> CREATOR
            = new Parcelable.Creator<Artist>() {
        @Override
        public Artist createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractArtist(in);
        }

        @Override
        public Artist[] newArray(int size) {
            return new Artist[size];
        }
    };

    public Artist(Parcel in) {
        super(in);
        this.shortBio = in.readString();
        this.artworkMrl = in.readString();
        this.musicBrainzId = in.readString();
        this.tracksCount = in.readInt();
        this.albumsCount = in.readInt();
        this.mFavorite = in.readInt() == 1;
    }
}
