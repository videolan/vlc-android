package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.media.MediaLibraryItem;


public abstract class AbstractVideoGroup extends MediaLibraryItem {

    public int mCount;

    public AbstractVideoGroup(String name, int count) {
        super(0L, name);
        mCount = count;
    }

    abstract public AbstractMediaWrapper[] media(int sort, boolean desc, int nbItems, int offset);
    abstract public AbstractMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchTracksCount(String query);

    public String getDisplayTitle() {
        return super.getTitle() + "…";
    }

    public int mediaCount() {
        return mCount;
    }

    @Override
    public AbstractMediaWrapper[] getTracks() {
        return new AbstractMediaWrapper[0];
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
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeInt(mCount);
    }

    public static Parcelable.Creator<AbstractVideoGroup> CREATOR = new Parcelable.Creator<AbstractVideoGroup>() {
        @Override
        public AbstractVideoGroup createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractVideoGroup(in);
        }

        @Override
        public AbstractVideoGroup[] newArray(int size) {
            return new AbstractVideoGroup[size];
        }
    };

    public AbstractVideoGroup(Parcel in) {
        super(in);
        this.mCount = in.readInt();
    }

    public boolean equals(AbstractVideoGroup other) {
        return TextUtils.equals(mTitle, other.getTitle());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AbstractVideoGroup) return equals((AbstractVideoGroup)obj);
        return super.equals(obj);
    }

    @Override
    public boolean equals(MediaLibraryItem other) {
        if (other instanceof AbstractVideoGroup) return equals((AbstractVideoGroup)other);
        return super.equals(other);
    }
}
