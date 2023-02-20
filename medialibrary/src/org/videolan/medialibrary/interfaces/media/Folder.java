package org.videolan.medialibrary.interfaces.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.media.MediaLibraryItem;

public abstract class Folder extends MediaLibraryItem {
    public static int TYPE_FOLDER_UNKNOWN = 0;
    public static int TYPE_FOLDER_VIDEO = 1;
    public static int TYPE_FOLDER_AUDIO = 2;
    public static int TYPE_FOLDER_EXTERNAL = 3;
    public static int TYPE_FOLDER_STREAM = 4;

    public String mMrl;
    protected int mMediaCount;

    public Folder(long id, String name, String mrl, int count, boolean isFavorite) {
        super(id, name);
        mMrl = mrl;
        mMediaCount = count;
        mFavorite = isFavorite;
    }

    abstract public MediaWrapper[] media(int type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int mediaCount(int type);
    abstract public Folder[] subfolders(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int subfoldersCount(int type);
    abstract public MediaWrapper[] searchTracks(String query, int mediaType, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int searchTracksCount(String query, int mediaType);

    public String getDisplayTitle() {
        return super.getTitle();
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
        return TYPE_FOLDER;
    }

    @Override
    public boolean setFavorite(boolean favorite) {
        return false;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeString(mMrl);
        parcel.writeInt(mMediaCount);
        parcel.writeInt(mFavorite ? 1 : 0);
    }

    public static Parcelable.Creator<Folder> CREATOR = new Parcelable.Creator<Folder>() {
        @Override
        public Folder createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractFolder(in);
        }

        @Override
        public Folder[] newArray(int size) {
            return new Folder[size];
        }
    };

    public Folder(Parcel in) {
        super(in);
        this.mMrl = in.readString();
        this.mMediaCount = in.readInt();
        this.mFavorite = in.readInt() == 1;
    }

    public boolean equals(Folder other) {
        return mId == other.getId();
    }

    @Override
    public boolean equals(MediaLibraryItem other) {
        if (other instanceof Folder) return equals((Folder)other);
        return super.equals(other);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Folder) return equals((Folder)obj);
        return super.equals(obj);
    }
}
