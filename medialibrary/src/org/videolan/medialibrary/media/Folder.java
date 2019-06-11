package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.AMedialibrary;
import org.videolan.medialibrary.interfaces.media.AFolder;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class Folder extends AFolder {

    public Folder(long id, String name, String mrl) {
        super(id, name, mrl);
    }

    public Folder(Parcel in) {
        super(in);
    }

    public AMediaWrapper[] media(int type, int sort, boolean desc, int nbItems, int offset) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeMedia(ml, mId, type, sort, desc, nbItems, offset) : AMedialibrary.EMPTY_COLLECTION;
    }

    public int mediaCount(int type) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeMediaCount(ml, mId, type) : 0;
    }

    public AFolder[] subfolders(int sort, boolean desc, int nbItems, int offset) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSubfolders(ml, mId, sort, desc, nbItems, offset) : new Folder[0];
    }

    public int subfoldersCount(int type) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSubfoldersCount(ml, mId, type) : 0;
    }

    public AMediaWrapper[] searchTracks(String query, int mediaType, int sort, boolean desc, int nbItems, int offset) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, mediaType, sort, desc, nbItems, offset) : AMedialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query, int mediaType) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query, mediaType) : 0;
    }

    //    private native AMediaWrapper[] nativeGetTracks();
//    private native int nativeGetTracksCount();
    private native AMediaWrapper[] nativeMedia(AMedialibrary ml, long mId, int type, int sort, boolean desc, int nbItems, int offset);
    private native int nativeMediaCount(AMedialibrary ml, long mId, int type);
    private native AFolder[] nativeSubfolders(AMedialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native int nativeSubfoldersCount(AMedialibrary ml, long mId, int type);
    private native AMediaWrapper[] nativeSearch(AMedialibrary ml, long mId, String query, int mediaType, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(AMedialibrary ml, long mId, String query, int mediaType);
}
