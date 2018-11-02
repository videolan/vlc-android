package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.R;

@SuppressWarnings("JniMissingFunction")
public class Album extends MediaLibraryItem {
    public static final String TAG = "VLC/Album";
    public static class SpecialRes {
        public static String UNKNOWN_ALBUM = Medialibrary.getContext().getString(R.string.unknown_album);
    }

    private int releaseYear;
    private String artworkMrl;
    private String albumArtist;
    private long albumArtistId;
    private int mTracksCount;
    private int duration;

    public Album(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, int duration) {
        super(id, title);
        this.releaseYear = releaseYear;
        this.artworkMrl = artworkMrl != null ? VLCUtil.UriFromMrl(artworkMrl).getPath() : null;
        this.albumArtist = albumArtist != null ? albumArtist.trim(): null;
        this.albumArtistId = albumArtistId;
        this.mTracksCount = nbTracks;
        this.duration = duration;
        if (TextUtils.isEmpty(title)) mTitle = SpecialRes.UNKNOWN_ALBUM;
        if (albumArtistId == 1L) {
            this.albumArtist = Artist.SpecialRes.UNKNOWN_ARTIST;
        } else if (albumArtistId == 2L) {
            this.albumArtist = Artist.SpecialRes.VARIOUS_ARTISTS;
        }
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

    public int getTracksCount() {
        return mTracksCount;
    }

    public int getRealTracksCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    public int getDuration() {
        return duration;
    }

    public MediaWrapper[] getTracks() {
        return getTracks(Medialibrary.SORT_DEFAULT, false);
    }

    public MediaWrapper[] getTracks(int sort, boolean desc) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId, sort, desc) : Medialibrary.EMPTY_COLLECTION;
    }

    public MediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    @Override
    public int getItemType() {
        return TYPE_ALBUM;
    }

    private native MediaWrapper[] nativeGetTracks(Medialibrary ml, long mId, int sort, boolean desc);
    private native MediaWrapper[] nativeGetPagedTracks(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetTracksCount(Medialibrary ml, long id);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query);

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
        this.mTracksCount = in.readInt();
        this.duration = in.readInt();
    }
}
