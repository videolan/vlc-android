package org.videolan.medialibrary.media;

import android.os.Parcel;

import androidx.annotation.WorkerThread;

import org.videolan.medialibrary.interfaces.AbstractMedialibrary;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup;


public class VideoGroup extends AbstractVideoGroup {

    VideoGroup(String name, int count) {
        super(name, count);
    }

    public VideoGroup(Parcel in) {
        super(in);
    }

    @Override
    @WorkerThread
    public AbstractMediaWrapper[] media(int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeMedia(ml, mTitle, sort, desc, nbItems, offset) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    @Override
    @WorkerThread
    public AbstractMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mTitle, query, sort, desc, nbItems, offset) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    @Override
    @WorkerThread
    public int searchTracksCount(String query) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mTitle, query) : 0;
    }

    private native AbstractMediaWrapper[] nativeMedia(AbstractMedialibrary ml, String name, int sort, boolean desc, int nbItems, int offset);
    private native AbstractMediaWrapper[] nativeSearch(AbstractMedialibrary ml, String name, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(AbstractMedialibrary ml, String name, String query);
}
