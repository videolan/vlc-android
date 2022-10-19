package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.media.MediaLibraryItem;

import java.util.List;

public abstract class Playlist extends MediaLibraryItem {

    protected int mTracksCount;
    protected long mDuration;
    protected long mNbVideo;
    protected long mNbAudio;
    protected long mNbUnknown;
    protected long mNbDurationUnknown;

    protected Playlist(long id, String name, int trackCount, long duration, int nbVideo, int nbAudio, int nbUnknown, int nbDurationUnknown) {
        super(id, name);
        mTracksCount = trackCount;
        mDuration = duration;
        mNbVideo = nbVideo;
        mNbAudio = nbAudio;
        mNbUnknown = nbUnknown;
        mNbDurationUnknown = nbDurationUnknown;
    }

    public enum Type {
        /// Include all kind of playlist, regarding of the media types
        All,
        /// Only include audio playlists
        AudioOnly,
        /// Only include video playlist
        VideoOnly
    }

    abstract public MediaWrapper[] getTracks(boolean includeMissing);
    abstract public MediaWrapper[] getPagedTracks(int nbItems, int offset, boolean includeMissing);
    abstract public int getRealTracksCount(boolean includeMissing);
    abstract public boolean append(long mediaId);
    abstract public boolean append(long[] mediaIds);
    abstract public boolean append(List<Long> mediaIds);
    abstract public boolean add(long mediaId, int position);
    abstract public boolean move(int oldPosition, int newPosition);
    abstract public boolean remove(int position);
    abstract public boolean delete();
    abstract public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, int nbItems, int offset);
    abstract public int searchTracksCount(String query);

    @Override
    public int getTracksCount() {
        return mTracksCount;
    }

    public long getDuration() {
        return mDuration;
    }

    public long getNbVideo() {
        return mNbVideo;
    }

    public long getNbAudio() {
        return mNbAudio;
    }

    public long getNbUnknown() {
        return mNbUnknown;
    }

    public long getNbDurationUnknown() {
        return mNbDurationUnknown;
    }

    @Override
    public int getItemType() {
        return TYPE_PLAYLIST;
    }

    public static Parcelable.Creator<Playlist> CREATOR
            = new Parcelable.Creator<Playlist>() {
        @Override
        public Playlist createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractPlaylist(in);
        }

        @Override
        public Playlist[] newArray(int size) {
            return new Playlist[size];
        }
    };

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeInt(mTracksCount);
    }

    public Playlist(Parcel in) {
        super(in);
        this.mTracksCount = in.readInt();
    }
}
