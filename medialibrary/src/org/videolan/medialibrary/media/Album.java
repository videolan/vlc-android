package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

public class Album extends MediaLibraryItem {
    private int releaseYear;
    private String artworkMrl;
    private String albumArtist;
    private long albumArtistId;
    private int nbTracks;
    private int duration;

    public Album(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, int duration) {
        super(id, title);
        this.releaseYear = releaseYear;
        this.artworkMrl = artworkMrl;
        this.albumArtist = albumArtist != null ? albumArtist.trim(): null;
        this.albumArtistId = albumArtistId;
        this.nbTracks = nbTracks;
        this.duration = duration;
    }

    public long getId() {
        return mId;
    }

    @Override
    public String getDescription() {
        return albumArtist;
    }

    public int getReleaseYear() {
        return releaseYear;
    }

    public String getArtworkMrl() {
        return artworkMrl;
    }

    public Artist getAlbumArtist() {
        //TODO
        return null;
    }

    public int getNbTracks() {
        return nbTracks;
    }

    public int getDuration() {
        return duration;
    }

    public MediaWrapper[] getTracks(Medialibrary ml) {
        return nativeGetTracksFromAlbum(ml, mId);
    }

    private native MediaWrapper[] nativeGetTracksFromAlbum(Medialibrary ml, long mId);

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeInt(releaseYear);
        parcel.writeString(artworkMrl);
        parcel.writeString(albumArtist);
        parcel.writeLong(albumArtistId);
        parcel.writeInt(nbTracks);
        parcel.writeInt(duration);
    }

    public static Parcelable.Creator<Album> CREATOR
            = new Parcelable.Creator<Album>() {
        public Album createFromParcel(Parcel in) {
            return new Album(in);
        }

        public Album[] newArray(int size) {
            return new Album[size];
        }
    };

    private Album(Parcel in) {
        super(in);
        this.releaseYear = in.readInt();
        this.artworkMrl = in.readString();
        this.albumArtist = in.readString();
        this.albumArtistId = in.readLong();
        this.nbTracks = in.readInt();
        this.duration = in.readInt();
    }
}
