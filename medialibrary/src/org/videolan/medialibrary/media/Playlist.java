package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.AMedialibrary;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;
import org.videolan.medialibrary.interfaces.media.APlaylist;

import java.util.List;

@SuppressWarnings("JniMissingFunction")
public class Playlist extends APlaylist {

    public Playlist(long id, String name, int trackCount) {
        super(id, name, trackCount);
    }

    public Playlist(Parcel in) {
        super(in);
    }

    @Override
    public AMediaWrapper[] getTracks() {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId) : AMedialibrary.EMPTY_COLLECTION;
    }

    public AMediaWrapper[] getPagedTracks(int nbItems, int offset) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, nbItems, offset) : AMedialibrary.EMPTY_COLLECTION;
    }

    public int getRealTracksCount() {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }


    public boolean append(long mediaId) {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAppend(ml, mId, mediaId);
    }

    public boolean append(long[] mediaIds) {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAppendGroup(ml, mId, mediaIds);
    }

    public boolean append(List<Long> mediaIds) {
        AMedialibrary ml = AMedialibrary.getInstance();
        if (ml == null || !ml.isInitiated())
            return false;
        long[] ids = new long[mediaIds.size()];
        for (int i = 0; i < ids.length; ++i)
            ids[i] = mediaIds.get(i);
        return nativePlaylistAppendGroup(ml, mId, ids);
    }

    public boolean add(long mediaId, int position) {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAdd(ml, mId, mediaId, position);
    }

    public boolean move(int oldPosition, int newPosition) {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistMove(ml, mId, oldPosition, newPosition);
    }

    public boolean remove(int position) {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistRemove(ml, mId, position);
    }

    public boolean delete() {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistDelete(ml, mId);
    }

    public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : AMedialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final AMedialibrary ml = AMedialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    private native AMediaWrapper[] nativeGetTracks(AMedialibrary ml, long id);
    private native AMediaWrapper[] nativeGetPagedTracks(AMedialibrary ml, long id, int nbItems, int offset);
    private native int nativeGetTracksCount(AMedialibrary ml, long id);
    private native AMediaWrapper[] nativeSearch(AMedialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(AMedialibrary ml, long mId, String query);
    private native boolean nativePlaylistAppend(AMedialibrary ml, long id, long mediaId);
    private native boolean nativePlaylistAppendGroup(AMedialibrary ml, long id, long[] mediaIds);
    private native boolean nativePlaylistAdd(AMedialibrary ml, long id, long mediaId, int position);

    private native boolean nativePlaylistMove(AMedialibrary ml, long id, int oldPosition, int position);

    private native boolean nativePlaylistRemove(AMedialibrary ml, long id, int position);
    private native boolean nativePlaylistDelete(AMedialibrary ml, long id);
}
