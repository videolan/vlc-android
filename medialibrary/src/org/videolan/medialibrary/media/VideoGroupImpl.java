package org.videolan.medialibrary.media;

import android.os.Parcel;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.VideoGroup;


public class VideoGroupImpl extends VideoGroup {

    @SuppressWarnings("unused") /* Used from JNI */
    VideoGroupImpl(long id, String name, int count) {
        super(id, name, count);
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

    @Override
    public boolean add(long mediaId) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeGroupAddId(ml, mId, mediaId);
    }

    @Override
    public boolean remove(long mediaId) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeGroupRemoveId(ml, mId, mediaId);
    }

    @Override
    @Nullable
    @WorkerThread
    public String getName() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGroupName(ml, mId) : null;
    }

    @Override
    public boolean rename(String name) {
        final Medialibrary ml = Medialibrary.getInstance();
        boolean newNameSet = ml.isInitiated() && nativeGroupRename(ml, mId, name);
        if (newNameSet) setTitle(name);
        return newNameSet;
    }

    @Override
    public boolean userInteracted() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeGroupUserInteracted(ml, mId);
    }

    @Override
    public long duration() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGroupDuration(ml, mId) : 0L;
    }

    @Override
    public boolean destroy() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeGroupDestroy(ml, mId);
    }

    private native MediaWrapper[] nativeMedia(Medialibrary ml, long id, int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long id, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(Medialibrary ml, long id, String query);
    private native boolean nativeGroupAddId(Medialibrary ml, long id, long mediaId);
    private native boolean nativeGroupRemoveId(Medialibrary ml, long id, long mediaId);
    private native String nativeGroupName(Medialibrary ml, long id);
    private native boolean nativeGroupRename(Medialibrary ml, long id, String name);
    private native boolean nativeGroupUserInteracted(Medialibrary ml, long id);
    private native long nativeGroupDuration(Medialibrary ml, long id);
    private native boolean nativeGroupDestroy(Medialibrary ml, long id);
}
