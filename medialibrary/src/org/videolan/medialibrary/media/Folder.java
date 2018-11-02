package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

@SuppressWarnings("JniMissingFunction")
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
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeMedia(ml, mId, type, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int mediaCount(int type) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeMediaCount(ml, mId, type) : 0;
    }

    public Folder[] subfolders(int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSubfolders(ml, mId, sort, desc, nbItems, offset) : new Folder[0];
    }

    public int subfoldersCount() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSubfoldersCount(ml, mId) : 0;
    }

//    private native MediaWrapper[] nativeGetTracks();
//    private native int nativeGetTracksCount();
    private native MediaWrapper[] nativeMedia(Medialibrary ml, long mId, int type, int sort, boolean desc, int nbItems, int offset);
    private native int nativeMediaCount(Medialibrary ml, long mId, int type);
    private native Folder[] nativeSubfolders(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native int nativeSubfoldersCount(Medialibrary ml, long mId);

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
