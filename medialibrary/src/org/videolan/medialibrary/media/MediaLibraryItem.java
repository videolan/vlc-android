package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

public abstract class MediaLibraryItem implements Parcelable {

    public abstract MediaWrapper[] getTracks(Medialibrary ml);

    protected long mId;
    protected String mTitle;

    protected MediaLibraryItem() {}

    protected MediaLibraryItem(long id, String name) {
        mId = id;
        mTitle = name;
    }

    public long getId() {
        return mId;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getArtworkMrl() {
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public String getDescription() {
        return null;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(mId);
        parcel.writeString(mTitle);
    }

    protected MediaLibraryItem(Parcel in) {
        mId = in.readLong();
        mTitle = in.readString();
    }
}
