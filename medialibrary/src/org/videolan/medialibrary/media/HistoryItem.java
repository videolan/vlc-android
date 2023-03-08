package org.videolan.medialibrary.media;


import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

import java.util.Date;

public class HistoryItem extends MediaLibraryItem {
    private String mrl, title;
    private long insertionDate;

    public HistoryItem (String mrl, String title, long insertionDate, boolean favorite) {
        this.mrl = mrl;
        this.title = title;
        this.mFavorite = favorite;
        this.insertionDate = insertionDate;
    }

    public MediaWrapper getMedia() {
        MediaWrapper mw = MLServiceLocator.getAbstractMediaWrapper(Uri.parse(mrl));
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

    @Override
    public boolean setFavorite(boolean favorite) {
        return false;
    }

    public String getMrl() {
        return mrl;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public boolean isFavorite() {
        return mFavorite;
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
        parcel.writeByte((byte) (mFavorite ? 1 : 0));
        parcel.writeLong(insertionDate);
    }

    public static Parcelable.Creator<HistoryItem> CREATOR
            = new Parcelable.Creator<HistoryItem>() {
        @Override
        public HistoryItem createFromParcel(Parcel in) {
            return new HistoryItem(in);
        }

        @Override
        public HistoryItem[] newArray(int size) {
            return new HistoryItem[size];
        }
    };

    private HistoryItem(Parcel in) {
        super(in);
        this.mrl = in.readString();
        this.mFavorite = in.readByte() != 0;
        this.insertionDate = in.readLong();
    }
}
