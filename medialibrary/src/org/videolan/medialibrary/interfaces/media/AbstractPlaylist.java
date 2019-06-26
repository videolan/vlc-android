package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.media.MediaLibraryItem;

import java.util.List;

public abstract class AbstractPlaylist extends MediaLibraryItem {

    protected int mTracksCount;

    protected AbstractPlaylist(long id, String name, int trackCount) {
        super(id, name);
        mTracksCount = trackCount;
    }

    abstract public AbstractMediaWrapper[] getTracks();
    abstract public AbstractMediaWrapper[] getPagedTracks(int nbItems, int offset);
    abstract public int getRealTracksCount();
    abstract public boolean append(long mediaId);
    abstract public boolean append(long[] mediaIds);
    abstract public boolean append(List<Long> mediaIds);
    abstract public boolean add(long mediaId, int position);
    abstract public boolean move(int oldPosition, int newPosition);
    abstract public boolean remove(int position);
    abstract public boolean delete();
    abstract public AbstractMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchTracksCount(String query);

    @Override
    public int getTracksCount() {
        return mTracksCount;
    }

    @Override
    public int getItemType() {
        return TYPE_PLAYLIST;
    }


    public static Parcelable.Creator<AbstractPlaylist> CREATOR
            = new Parcelable.Creator<AbstractPlaylist>() {
        @Override
        public AbstractPlaylist createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractPlaylist(in);
        }

        @Override
        public AbstractPlaylist[] newArray(int size) {
            return new AbstractPlaylist[size];
        }
    };

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeInt(mTracksCount);
    }

    public AbstractPlaylist(Parcel in) {
        super(in);
        this.mTracksCount = in.readInt();
    }

}
