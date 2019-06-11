package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.R;
import org.videolan.medialibrary.ServiceLocator;
import org.videolan.medialibrary.interfaces.AMedialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;

abstract public class AArtist extends MediaLibraryItem {

    private String shortBio;
    private String artworkMrl;
    private String musicBrainzId;

    public static class SpecialRes {
        public static String UNKNOWN_ARTIST = AMedialibrary.getContext().getString(R.string.unknown_artist);
        public static String VARIOUS_ARTISTS = AMedialibrary.getContext().getString(R.string.various_artists);
    }

    public AArtist(long id, String name, String shortBio, String artworkMrl, String musicBrainzId) {
        super(id, name);
        this.shortBio = shortBio;
        this.artworkMrl = artworkMrl != null ? VLCUtil.UriFromMrl(artworkMrl).getPath() : null;
        this.musicBrainzId = musicBrainzId;
        if (id == 1L) {
            mTitle = SpecialRes.UNKNOWN_ARTIST;
        } else if (id == 2L) {
            mTitle = SpecialRes.VARIOUS_ARTISTS;
        }
    }

    abstract public AAlbum[] getAlbums(int sort, boolean desc);
    abstract public AAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset);
    abstract public AAlbum[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchAlbumsCount(String query);
    abstract public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchTracksCount(String query);
    abstract public int getAlbumsCount();
    abstract public AMediaWrapper[] getTracks(int sort, boolean desc);
    abstract public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset);
    abstract public int getTracksCount();

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

    public AAlbum[] getAlbums() {
        return getAlbums(AMedialibrary.SORT_DEFAULT, false);
    }

    @Override
    public AMediaWrapper[] getTracks() {
        return getTracks(AMedialibrary.SORT_ALBUM, true);
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
    }

    public static Parcelable.Creator<AArtist> CREATOR
            = new Parcelable.Creator<AArtist>() {
        @Override
        public AArtist createFromParcel(Parcel in) {
            return ServiceLocator.getAArtist(in);
        }

        @Override
        public AArtist[] newArray(int size) {
            return new AArtist[size];
        }
    };

    public AArtist(Parcel in) {
        super(in);
        this.shortBio = in.readString();
        this.artworkMrl = in.readString();
        this.musicBrainzId = in.readString();
    }
}
