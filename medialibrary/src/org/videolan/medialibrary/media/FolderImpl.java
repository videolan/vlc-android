package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.Folder;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class FolderImpl extends Folder {

    public FolderImpl(long id, String name, String mrl, int count) {
        super(id, name, mrl, count);
    }

    public FolderImpl(Parcel in) {
        super(in);
    }

    public MediaWrapper[] media(int type, int sort, boolean desc, boolean includeMissing, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeMedia(ml, mId, type, sort, desc, includeMissing, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int mediaCount(int type) {
        if (type == TYPE_FOLDER_VIDEO) return mMediaCount;
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeMediaCount(ml, mId, type) : 0;
    }

    public Folder[] subfolders(int sort, boolean desc, boolean includeMissing, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSubfolders(ml, mId, sort, desc, includeMissing, nbItems, offset) : new FolderImpl[0];
    }

    public int subfoldersCount(int type) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSubfoldersCount(ml, mId, type) : 0;
    }

    public MediaWrapper[] searchTracks(String query, int mediaType, int sort, boolean desc, boolean includeMissing, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, mediaType, sort, desc, includeMissing, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query, int mediaType) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query, mediaType) : 0;
    }

    //    private native MediaWrapper[] nativeGetTracks();
//    private native int nativeGetTracksCount();
    private native MediaWrapper[] nativeMedia(Medialibrary ml, long mId, int type, int sort, boolean desc, boolean includeMissing, int nbItems, int offset);
    private native int nativeMediaCount(Medialibrary ml, long mId, int type);
    private native Folder[] nativeSubfolders(Medialibrary ml, long mId, int sort, boolean desc, boolean includeMissing, int nbItems, int offset);
    private native int nativeSubfoldersCount(Medialibrary ml, long mId, int type);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int mediaType, int sort, boolean desc, boolean includeMissing, int nbItems, int offset);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query, int mediaType);
}
