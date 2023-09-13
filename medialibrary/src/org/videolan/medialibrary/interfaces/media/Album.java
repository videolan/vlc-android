package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.BuildConfig;
import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.MLContextTools;
import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.R;
import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;

public abstract class Album extends MediaLibraryItem {
    public static class SpecialRes {
        public static String UNKNOWN_ALBUM = MLContextTools.getInstance().getContext().getString(R.string.unknown_album);
    }

    protected int releaseYear;
    protected String artworkMrl;
    protected String albumArtist;
    protected long albumArtistId;
    protected int mTracksCount;
    protected long duration;
    public int mPresentTracksCount;

    public Album(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, int nbPresentTracks, long duration, boolean isFavorite) {
        super(id, title);
        this.releaseYear = releaseYear;
        this.artworkMrl = artworkMrl != null ? VLCUtil.UriFromMrl(artworkMrl).getPath() : null;
        this.albumArtist = albumArtist != null ? albumArtist.trim(): null;
        this.albumArtistId = albumArtistId;
        this.mTracksCount = nbTracks;
        this.mPresentTracksCount = nbPresentTracks;
        this.duration = duration;
        this.mFavorite = isFavorite;
        if (TextUtils.isEmpty(title)) mTitle = SpecialRes.UNKNOWN_ALBUM;
        if (albumArtistId == 1L) {
            this.albumArtist = Artist.SpecialRes.UNKNOWN_ARTIST;
        } else if (albumArtistId == 2L) {
            this.albumArtist = Artist.SpecialRes.VARIOUS_ARTISTS;
        }
    }

    protected Album(Parcel in) {
        super(in);
        this.releaseYear = in.readInt();
        this.artworkMrl = in.readString();
        this.albumArtist = in.readString();
        this.albumArtistId = in.readLong();
        this.mTracksCount = in.readInt();
        this.mPresentTracksCount = in.readInt();
        this.duration = in.readLong();
        this.mFavorite = in.readInt() == 1;
        if (BuildConfig.DEBUG) Log.d("Parcel test", "During unparcel: "+mFavorite);
        this.mPresentTracksCount = in.readInt();
    }

    abstract public int getRealTracksCount();
    abstract public MediaWrapper[] getTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    abstract public MediaWrapper[] getPagedTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int searchTracksCount(String query);
    abstract public Artist retrieveAlbumArtist();

    @Override
    public long getId() {
        return mId;
    }

    // This is called getDescription and not getAlbumArtist because the description in
    // MediaLibraryItem is used for UI cards' subtitles
    // ie for the artist for the album
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

    @Override
    public int getTracksCount() {
        return mTracksCount;
    }

    public long getDuration() {
        return duration;
    }

    @Override
    public MediaWrapper[] getTracks() {
        return getTracks(Medialibrary.SORT_DEFAULT, false, true, false);
    }

    @Override
    public int getItemType() {
        return TYPE_ALBUM;
    }

    public String getAlbumArtist() {
        return albumArtist;
    }

    public int getPresentTracksCount() {
        return mPresentTracksCount;
    }


    public static Parcelable.Creator<Album> CREATOR
            = new Parcelable.Creator<Album>() {
        @Override
        public Album createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractAlbum(in);
        }

        @Override
        public Album[] newArray(int size) {
            return new Album[size];
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
        parcel.writeInt(mPresentTracksCount);
        parcel.writeLong(duration);
        parcel.writeInt(mFavorite ? 1 : 0);
        parcel.writeInt(mTracksCount);
    }
}
