package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.R;

public class Artist extends MediaLibraryItem {

    private String shortBio;
    private String artworkMrl;
    private String musicBrainzId;

    static class SpecialRes {
        static String UNKNOWN_ARTIST = Medialibrary.getContext().getString(R.string.unknown_artist);
        static String VARIOUS_ARTISTS = Medialibrary.getContext().getString(R.string.various_artists);
    }

    public Artist(long id, String name, String shortBio, String artworkMrl, String musicBrainzId) {
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

    public String getShortBio() {
        return shortBio;
    }

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
        return getAlbums(Medialibrary.SORT_DEFAULT, false);
    }

    public Album[] getAlbums(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() ? nativeGetAlbumsFromArtist(ml, mId, sort, desc) : new Album[0];
    }

    public MediaWrapper[] getTracks() {
        return getTracks(Medialibrary.SORT_DEFAULT, false);
    }

    public MediaWrapper[] getTracks(int sort, boolean desc) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() ? nativeGetMediaFromArtist(ml, mId, sort, desc) : Medialibrary.EMPTY_COLLECTION;
    }

    @Override
    public int getItemType() {
        return TYPE_ARTIST;
    }

    private native Album[] nativeGetAlbumsFromArtist(Medialibrary ml, long mId, int sort, boolean desc);
    private native MediaWrapper[] nativeGetMediaFromArtist(Medialibrary ml, long mId, int sort, boolean desc);

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeString(shortBio);
        parcel.writeString(artworkMrl);
        parcel.writeString(musicBrainzId);
    }

    public static Parcelable.Creator<Artist> CREATOR
            = new Parcelable.Creator<Artist>() {
        public Artist createFromParcel(Parcel in) {
            return new Artist(in);
        }

        public Artist[] newArray(int size) {
            return new Artist[size];
        }
    };

    private Artist(Parcel in) {
        super(in);
        this.shortBio = in.readString();
        this.artworkMrl = in.readString();
        this.musicBrainzId = in.readString();
    }
}
