package org.videolan.medialibrary.media;


import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import org.videolan.medialibrary.Medialibrary;

import java.util.Date;

public class HistoryItem extends MediaLibraryItem {
    String mrl;
    boolean favorite;
    long insertionDate;

    public HistoryItem (String mrl, long insertionDate, boolean favorite) {
        this.mrl = mrl;
        this.favorite = favorite;
        this.insertionDate = insertionDate;
    }

    public MediaWrapper getMedia() {
        return new MediaWrapper(Uri.parse(mrl));
    }
    @Override
    public MediaWrapper[] getTracks(@Nullable Medialibrary ml) {
        return new MediaWrapper[]{getMedia()};
    }

    @Override
    public int getItemType() {
        return TYPE_HISTORY;
    }

    public String getMrl() {
        return mrl;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public long getInsertionDate() {
        return insertionDate;
    }

    public Date getDate() {
        return new Date(insertionDate);
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeString(mrl);
        parcel.writeByte((byte) (favorite ? 1 : 0));
        parcel.writeLong(insertionDate);
    }

    public static Parcelable.Creator<HistoryItem> CREATOR
            = new Parcelable.Creator<HistoryItem>() {
        public HistoryItem createFromParcel(Parcel in) {
            return new HistoryItem(in);
        }

        public HistoryItem[] newArray(int size) {
            return new HistoryItem[size];
        }
    };

    private HistoryItem(Parcel in) {
        super(in);
        this.mrl = in.readString();
        this.favorite = in.readByte() != 0;
        this.insertionDate = in.readLong();
    }
}
