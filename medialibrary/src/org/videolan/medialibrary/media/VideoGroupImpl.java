package org.videolan.medialibrary.media;

import android.os.Parcel;

import androidx.annotation.WorkerThread;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.VideoGroup;


public class VideoGroupImpl extends VideoGroup {

    @SuppressWarnings("unused") /* Used from JNI */
    VideoGroupImpl(String name, int count) {
        super(name, count);
    }

    public VideoGroupImpl(Parcel in) {
        super(in);
    }

    @Override
    @WorkerThread
    public MediaWrapper[] media(int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeMedia(ml, mId, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    @Override
    @WorkerThread
    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    @Override
    @WorkerThread
    public int searchTracksCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    private native MediaWrapper[] nativeMedia(Medialibrary ml, long id, int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long id, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(Medialibrary ml, long id, String query);
}
