package org.videolan.medialibrary.media;


import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

public class HistoryItem extends MediaLibraryItem {
    private String mrl, title;
    private boolean favorite;
    private long insertionDate;

    public HistoryItem (String mrl, String title, long insertionDate, boolean favorite) {
        this.mrl = mrl;
        this.title = title;
        this.favorite = favorite;
        this.insertionDate = insertionDate;
    }

    public MediaWrapper getMedia() {
        MediaWrapper mw = new MediaWrapper(Uri.parse(mrl));
        mw.setTitle(title);
        mw.setType(MediaWrapper.TYPE_STREAM);
        return mw;
    }
    @Override
    public MediaWrapper[] getTracks() {
        return new MediaWrapper[]{getMedia()};
    }

    @Override
    public int getTracksCount() {
        return 1;
    }

    @Override
    public int getItemType() {
        return TYPE_HISTORY;
    }

    public String getMrl() {
        return mrl;
    }

    public String getTitle() {
        return title;
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
