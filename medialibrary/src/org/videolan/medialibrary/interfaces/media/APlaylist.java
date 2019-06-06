package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.ServiceLocator;
import org.videolan.medialibrary.media.MediaLibraryItem;

import java.util.List;

public abstract class APlaylist extends MediaLibraryItem {

    protected int mTracksCount;

    protected APlaylist(long id, String name, int trackCount) {
        super(id, name);
        mTracksCount = trackCount;
    }

    abstract public AMediaWrapper[] getTracks();
    abstract public AMediaWrapper[] getPagedTracks(int nbItems, int offset);
    abstract public int getRealTracksCount();
    abstract public boolean append(long mediaId);
    abstract public boolean append(long[] mediaIds);
    abstract public boolean append(List<Long> mediaIds);
    abstract public boolean add(long mediaId, int position);
    abstract public boolean move(int oldPosition, int newPosition);
    abstract public boolean remove(int position);
    abstract public boolean delete();
    abstract public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchTracksCount(String query);

    @Override
    public int getTracksCount() {
        return mTracksCount;
    }

    @Override
    public int getItemType() {
        return TYPE_PLAYLIST;
    }


    public static Parcelable.Creator<APlaylist> CREATOR
            = new Parcelable.Creator<APlaylist>() {
        @Override
        public APlaylist createFromParcel(Parcel in) {
            return ServiceLocator.getAPlaylist(in);
        }

        @Override
        public APlaylist[] newArray(int size) {
            return new APlaylist[size];
        }
    };

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeInt(mTracksCount);
    }

    public APlaylist(Parcel in) {
        super(in);
        this.mTracksCount = in.readInt();
    }

}
