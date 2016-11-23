package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

public abstract class MediaLibraryItem implements Parcelable {

    public static final int TYPE_ALBUM = 0;
    public static final int TYPE_ARTIST = 1;
    public static final int TYPE_GENRE = 2;
    public static final int TYPE_PLAYLIST = 3;
    public static final int TYPE_MEDIA = 4;
    public static final int TYPE_DUMMY = 5;
    public static final int TYPE_STORAGE = 6;

    public abstract MediaWrapper[] getTracks(Medialibrary ml);
    public abstract int getItemType();

    long mId;
    protected String mTitle;
    protected String mDescription;

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
        return mDescription;
    }


    public void setDescription(String description) {
        mDescription = description;
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
