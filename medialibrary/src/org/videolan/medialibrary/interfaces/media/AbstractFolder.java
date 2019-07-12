package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.media.MediaLibraryItem;

public abstract class AbstractFolder extends MediaLibraryItem {
    public static int TYPE_FOLDER_UNKNOWN = 0;
    public static int TYPE_FOLDER_VIDEO = 1;
    public static int TYPE_FOLDER_AUDIO = 2;
    public static int TYPE_FOLDER_EXTERNAL = 3;
    public static int TYPE_FOLDER_STREAM = 4;

    public String mMrl;

    public AbstractFolder(long id, String name, String mrl) {
        super(id, name);
        mMrl = mrl;
    }

    abstract public AbstractMediaWrapper[] media(int type, int sort, boolean desc, int nbItems, int offset);
    abstract public int mediaCount(int type);
    abstract public AbstractFolder[] subfolders(int sort, boolean desc, int nbItems, int offset);
    abstract public int subfoldersCount(int type);
    abstract public AbstractMediaWrapper[] searchTracks(String query, int mediaType, int sort, boolean desc, int nbItems, int offset);
    abstract public int searchTracksCount(String query, int mediaType);

    @Override
    public AbstractMediaWrapper[] getTracks() {
        return new AbstractMediaWrapper[0];
    }

    @Override
    public int getTracksCount() {
        return 0;
    }

    @Override
    public int getItemType() {
        return TYPE_FOLDER;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeString(mMrl);
    }

    public static Parcelable.Creator<AbstractFolder> CREATOR = new Parcelable.Creator<AbstractFolder>() {
        @Override
        public AbstractFolder createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractFolder(in);
        }

        @Override
        public AbstractFolder[] newArray(int size) {
            return new AbstractFolder[size];
        }
    };

    public AbstractFolder(Parcel in) {
        super(in);
        this.mMrl = in.readString();
    }
}
