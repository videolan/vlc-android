package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

public class DummyItem extends MediaLibraryItem {

    public DummyItem(String title) {
        super(0, title);
    }

    @Override
    public MediaWrapper[] getTracks(Medialibrary ml) {
        return new MediaWrapper[0];
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
    }

    public static Parcelable.Creator<DummyItem> CREATOR
            = new Parcelable.Creator<DummyItem>() {
        public DummyItem createFromParcel(Parcel in) {
            return new DummyItem(in);
        }

        public DummyItem[] newArray(int size) {
            return new DummyItem[size];
        }
    };

    public DummyItem(Parcel in) {
        super(in);
    }
}
