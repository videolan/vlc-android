package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

public class DummyItem extends MediaLibraryItem {

    private String mArtWork = null;

    public DummyItem(long id, String title, String description) {
        super(id, title);
        mDescription = description;
    }

    public DummyItem(String title) {
        super(0, title);
    }

    @Override
    public MediaWrapper[] getTracks() {
        return Medialibrary.EMPTY_COLLECTION;
    }

    @Override
    public int getTracksCount() {
        return 1;
    }

    @Override
    public int getItemType() {
        return TYPE_DUMMY;
    }

    @Override
    public String getArtworkMrl() {
        return mArtWork;
    }

    @Override
    public boolean setFavorite(boolean favorite) {
        return false;
    }

    public void setArtWork(String artWork) {
        mArtWork = artWork;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
    }

    public static Parcelable.Creator<DummyItem> CREATOR
            = new Parcelable.Creator<DummyItem>() {
        @Override
        public DummyItem createFromParcel(Parcel in) {
            return new DummyItem(in);
        }

        @Override
        public DummyItem[] newArray(int size) {
            return new DummyItem[size];
        }
    };

    public DummyItem(Parcel in) {
        super(in);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DummyItem && TextUtils.equals(mTitle, ((DummyItem) obj).getTitle());
    }
}
