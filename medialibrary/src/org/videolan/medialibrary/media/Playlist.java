package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.AbstractMedialibrary;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;
import org.videolan.medialibrary.interfaces.media.AbstractPlaylist;

import java.util.List;

@SuppressWarnings("JniMissingFunction")
public class Playlist extends AbstractPlaylist {

    public Playlist(long id, String name, int trackCount) {
        super(id, name, trackCount);
    }

    public Playlist(Parcel in) {
        super(in);
    }

    @Override
    public AbstractMediaWrapper[] getTracks() {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    public AbstractMediaWrapper[] getPagedTracks(int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, nbItems, offset) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    public int getRealTracksCount() {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }


    public boolean append(long mediaId) {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAppend(ml, mId, mediaId);
    }

    public boolean append(long[] mediaIds) {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAppendGroup(ml, mId, mediaIds);
    }

    public boolean append(List<Long> mediaIds) {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        if (ml == null || !ml.isInitiated())
            return false;
        long[] ids = new long[mediaIds.size()];
        for (int i = 0; i < ids.length; ++i)
            ids[i] = mediaIds.get(i);
        return nativePlaylistAppendGroup(ml, mId, ids);
    }

    public boolean add(long mediaId, int position) {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAdd(ml, mId, mediaId, position);
    }

    public boolean move(int oldPosition, int newPosition) {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistMove(ml, mId, oldPosition, newPosition);
    }

    public boolean remove(int position) {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistRemove(ml, mId, position);
    }

    public boolean delete() {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistDelete(ml, mId);
    }

    public AbstractMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : AbstractMedialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    private native AbstractMediaWrapper[] nativeGetTracks(AbstractMedialibrary ml, long id);
    private native AbstractMediaWrapper[] nativeGetPagedTracks(AbstractMedialibrary ml, long id, int nbItems, int offset);
    private native int nativeGetTracksCount(AbstractMedialibrary ml, long id);
    private native AbstractMediaWrapper[] nativeSearch(AbstractMedialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(AbstractMedialibrary ml, long mId, String query);
    private native boolean nativePlaylistAppend(AbstractMedialibrary ml, long id, long mediaId);
    private native boolean nativePlaylistAppendGroup(AbstractMedialibrary ml, long id, long[] mediaIds);
    private native boolean nativePlaylistAdd(AbstractMedialibrary ml, long id, long mediaId, int position);

    private native boolean nativePlaylistMove(AbstractMedialibrary ml, long id, int oldPosition, int position);

    private native boolean nativePlaylistRemove(AbstractMedialibrary ml, long id, int position);
    private native boolean nativePlaylistDelete(AbstractMedialibrary ml, long id);
}
