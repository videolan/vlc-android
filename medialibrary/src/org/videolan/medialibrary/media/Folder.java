package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.AbstractFolder;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class Folder extends AbstractFolder {

    public Folder(long id, String name, String mrl) {
        super(id, name, mrl);
    }

    public Folder(Parcel in) {
        super(in);
    }

    public MediaWrapper[] media(int type, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeMedia(ml, mId, type, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int mediaCount(int type) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeMediaCount(ml, mId, type) : 0;
    }

    public AbstractFolder[] subfolders(int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSubfolders(ml, mId, sort, desc, nbItems, offset) : new Folder[0];
    }

    public int subfoldersCount(int type) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSubfoldersCount(ml, mId, type) : 0;
    }

    public MediaWrapper[] searchTracks(String query, int mediaType, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, mediaType, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query, int mediaType) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query, mediaType) : 0;
    }

    //    private native MediaWrapper[] nativeGetTracks();
//    private native int nativeGetTracksCount();
    private native MediaWrapper[] nativeMedia(Medialibrary ml, long mId, int type, int sort, boolean desc, int nbItems, int offset);
    private native int nativeMediaCount(Medialibrary ml, long mId, int type);
    private native AbstractFolder[] nativeSubfolders(Medialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native int nativeSubfoldersCount(Medialibrary ml, long mId, int type);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int mediaType, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query, int mediaType);
}
