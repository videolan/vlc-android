package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

public class Folder extends MediaLibraryItem {

    private String mMrl;

    public Folder(long id, String name, String mrl) {
        super(id, name);
        mMrl = mrl;
    }

    @Override
    public MediaWrapper[] getTracks() {
        return new MediaWrapper[0];
    }

    @Override
    public int getTracksCount() {
        return 0;
    }

    @Override
    public int getItemType() {
        return MediaLibraryItem.TYPE_FOLDER;
    }

    public MediaWrapper[] media(int type, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public Folder[] subfolders(int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

//    private native MediaWrapper[] nativeGetTracks();
//    private native int nativeGetTracksCount();
    private native MediaWrapper[] nativeMedia(Medialibrary ml, long mId, int type, int sort, boolean desc, int nbItems, int offset);
    private native Folder[] nativeSubfolders(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeString(mMrl);
    }

    public static Parcelable.Creator<Folder> CREATOR
            = new Parcelable.Creator<Folder>() {
        public Folder createFromParcel(Parcel in) {
            return new Folder(in);
        }

        public Folder[] newArray(int size) {
            return new Folder[size];
        }
    };

    private Folder(Parcel in) {
        super(in);
        this.mMrl = in.readString();
    }
}
