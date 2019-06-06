package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.R;
import org.videolan.medialibrary.ServiceLocator;
import org.videolan.medialibrary.media.MediaLibraryItem;

public abstract class AAlbum extends MediaLibraryItem {
    public static class SpecialRes {
        public static String UNKNOWN_ALBUM = Medialibrary.getContext().getString(R.string.unknown_album);
    }

    protected int releaseYear;
    protected String artworkMrl;
    protected String albumArtist;
    protected long albumArtistId;
    protected int mTracksCount;
    protected int duration;

    public AAlbum(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, int duration) {
        super(id, title);
        this.releaseYear = releaseYear;
        this.artworkMrl = artworkMrl != null ? VLCUtil.UriFromMrl(artworkMrl).getPath() : null;
        this.albumArtist = albumArtist != null ? albumArtist.trim(): null;
        this.albumArtistId = albumArtistId;
        this.mTracksCount = nbTracks;
        this.duration = duration;
        if (TextUtils.isEmpty(title)) mTitle = SpecialRes.UNKNOWN_ALBUM;
        if (albumArtistId == 1L) {
            this.albumArtist = AArtist.SpecialRes.UNKNOWN_ARTIST;
        } else if (albumArtistId == 2L) {
            this.albumArtist = AArtist.SpecialRes.VARIOUS_ARTISTS;
        }
    }

    protected AAlbum(Parcel in) {
        super(in);
        this.releaseYear = in.readInt();
        this.artworkMrl = in.readString();
        this.albumArtist = in.readString();
        this.albumArtistId = in.readLong();
        this.mTracksCount = in.readInt();
        this.duration = in.readInt();
    }

    abstract public int getRealTracksCount();
    abstract public AMediaWrapper[] getTracks(int sort, boolean desc);
    abstract public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset);
    abstract public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchTracksCount(String query);

    @Override
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

    @Override
    public String getArtworkMrl() {
        return artworkMrl;
    }

    public AArtist getAlbumArtist() {
        //TODO
        return null;
    }

    @Override
    public int getTracksCount() {
        return mTracksCount;
    }

    public int getDuration() {
        return duration;
    }

    @Override
    public AMediaWrapper[] getTracks() {
        return getTracks(Medialibrary.SORT_ALBUM, false);
    }

    @Override
    public int getItemType() {
        return TYPE_ALBUM;
    }

    public static Parcelable.Creator<AAlbum> CREATOR
            = new Parcelable.Creator<AAlbum>() {
        @Override
        public AAlbum createFromParcel(Parcel in) {
            return ServiceLocator.getAAlbum(in);
        }

        @Override
        public AAlbum[] newArray(int size) {
            return new AAlbum[size];
        }
    };

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeInt(releaseYear);
        parcel.writeString(artworkMrl);
        parcel.writeString(albumArtist);
        parcel.writeLong(albumArtistId);
        parcel.writeInt(mTracksCount);
        parcel.writeInt(duration);
    }
}
