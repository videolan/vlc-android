package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.Medialibrary;

public class Artist extends MediaLibraryItem {

    private String shortBio;
    private String artworkMrl;
    private String musicBrainzId;

    public Artist(long id, String name, String shortBio, String artworkMrl, String musicBrainzId) {
        super(id, name);
        this.shortBio = shortBio;
        this.artworkMrl = artworkMrl != null ? VLCUtil.UriFromMrl(artworkMrl).getPath() : null;
        this.musicBrainzId = musicBrainzId;
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
        Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() ? nativeGetAlbumsFromArtist(ml, mId) : new Album[0];
    }

    public MediaWrapper[] getTracks() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() ? nativeGetMediaFromArtist(ml, mId) : Medialibrary.EMPTY_COLLECTION;
    }

    @Override
    public int getItemType() {
        return TYPE_ARTIST;
    }

    private native Album[] nativeGetAlbumsFromArtist(Medialibrary ml, long mId);
    private native MediaWrapper[] nativeGetMediaFromArtist(Medialibrary ml, long mId);

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
