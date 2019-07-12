package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.AbstractMedialibrary;
import org.videolan.medialibrary.interfaces.media.AbstractFolder;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class Folder extends AbstractFolder {

    public Folder(long id, String name, String mrl) {
        super(id, name, mrl);
    }

    public Folder(Parcel in) {
        super(in);
    }

    public AbstractMediaWrapper[] media(int type, int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeMedia(ml, mId, type, sort, desc, nbItems, offset) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    public int mediaCount(int type) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeMediaCount(ml, mId, type) : 0;
    }

    public AbstractFolder[] subfolders(int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSubfolders(ml, mId, sort, desc, nbItems, offset) : new Folder[0];
    }

    public int subfoldersCount(int type) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSubfoldersCount(ml, mId, type) : 0;
    }

    public AbstractMediaWrapper[] searchTracks(String query, int mediaType, int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, mediaType, sort, desc, nbItems, offset) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query, int mediaType) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query, mediaType) : 0;
    }

    //    private native AbstractMediaWrapper[] nativeGetTracks();
//    private native int nativeGetTracksCount();
    private native AbstractMediaWrapper[] nativeMedia(AbstractMedialibrary ml, long mId, int type, int sort, boolean desc, int nbItems, int offset);
    private native int nativeMediaCount(AbstractMedialibrary ml, long mId, int type);
    private native AbstractFolder[] nativeSubfolders(AbstractMedialibrary ml, long mId, int sort, boolean desc, int nbItems, int offset);
    private native int nativeSubfoldersCount(AbstractMedialibrary ml, long mId, int type);
    private native AbstractMediaWrapper[] nativeSearch(AbstractMedialibrary ml, long mId, String query, int mediaType, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(AbstractMedialibrary ml, long mId, String query, int mediaType);
}
