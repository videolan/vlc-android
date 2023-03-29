package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.media.MediaLibraryItem;


public abstract class VideoGroup extends MediaLibraryItem {

    public int mCount;
    public int mPresentCount;
    public int mPresentSeen;
    public boolean isNetwork = false;

    public VideoGroup(long id, String name, int count, int presentCount, int presentSeen, boolean isFavorite) {
        super(id, name);
        mCount = count;
        mPresentCount = presentCount;
        mPresentSeen = presentSeen;
        mFavorite = isFavorite;
    }

    abstract public MediaWrapper[] media(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int searchTracksCount(String query);
    abstract public boolean add(long mediaId);
    abstract public boolean remove(long mediaId);
    @Nullable abstract public String getName();
    abstract public boolean  rename(String name);
    abstract public boolean userInteracted();
    abstract public long duration();
    abstract public boolean destroy();

    public String getDisplayTitle() {
        return super.getTitle();
    }

    public int mediaCount() {
        return mCount;
    }

    public int getPresentCount() {
        return mPresentCount;
    }

    public int getPresentSeen() {
        return mPresentSeen;
    }

    @Override
    public MediaWrapper[] getTracks() {
        return new MediaWrapper[0];
    }

    @Override
    public int getTracksCount() {
        return mCount;
    }

    @Override
    public int getItemType() {
        return TYPE_VIDEO_GROUP;
    }

    @Override
    public boolean setFavorite(boolean favorite) {
        return false;
    }

    public boolean isNetwork() {
        return isNetwork;
    }

    public void setNetwork(boolean network) {
        isNetwork = network;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeInt(mCount);
        parcel.writeInt(mFavorite ? 1 : 0);
    }

    public static Parcelable.Creator<VideoGroup> CREATOR = new Parcelable.Creator<VideoGroup>() {
        @Override
        public VideoGroup createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractVideoGroup(in);
        }

        @Override
        public VideoGroup[] newArray(int size) {
            return new VideoGroup[size];
        }
    };

    public VideoGroup(Parcel in) {
        super(in);
        this.mCount = in.readInt();
        this.mFavorite = in.readInt() == 1;
    }

    public boolean equals(VideoGroup other) {
        return TextUtils.equals(mTitle, other.getTitle());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VideoGroup) return equals((VideoGroup)obj);
        return super.equals(obj);
    }

    @Override
    public boolean equals(MediaLibraryItem other) {
        if (other instanceof VideoGroup) return equals((VideoGroup)other);
        return super.equals(other);
    }
}
