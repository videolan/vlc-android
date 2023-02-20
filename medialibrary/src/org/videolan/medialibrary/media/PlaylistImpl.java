package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.Playlist;

import java.util.List;

@SuppressWarnings("JniMissingFunction")
public class PlaylistImpl extends Playlist {

    public PlaylistImpl(long id, String name, int trackCount, long duration, int nbVideo, int nbAudio, int nbUnknown, int nbDurationUnknown, boolean isFavorite) {
        super(id, name, trackCount, duration, nbVideo, nbAudio, nbUnknown, nbDurationUnknown, isFavorite);
    }

    public PlaylistImpl(Parcel in) {
        super(in);
    }

    @Override
    public MediaWrapper[] getTracks() {
        return getTracks(true, false);
    }

    @Override
    public MediaWrapper[] getTracks(boolean includeMissing, boolean onlyFavorites) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId, includeMissing, onlyFavorites) : Medialibrary.EMPTY_COLLECTION;
    }

    public MediaWrapper[] getPagedTracks(int nbItems, int offset, boolean includeMissing, boolean onlyFavorites) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, nbItems, offset, includeMissing, onlyFavorites) : Medialibrary.EMPTY_COLLECTION;
    }

    public int getRealTracksCount(boolean includeMissing, boolean onlyFavorites) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId, includeMissing, onlyFavorites) : 0;
    }

    public boolean append(long mediaId) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAppend(ml, mId, mediaId);
    }

    public boolean append(long[] mediaIds) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAppendGroup(ml, mId, mediaIds);
    }

    public boolean append(List<Long> mediaIds) {
        final Medialibrary ml = Medialibrary.getInstance();
        if (ml == null || !ml.isInitiated())
            return false;
        long[] ids = new long[mediaIds.size()];
        for (int i = 0; i < ids.length; ++i)
            ids[i] = mediaIds.get(i);
        return nativePlaylistAppendGroup(ml, mId, ids);
    }

    public boolean add(long mediaId, int position) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAdd(ml, mId, mediaId, position);
    }

    public boolean move(int oldPosition, int newPosition) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistMove(ml, mId, oldPosition, newPosition);
    }

    public boolean remove(int position) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistRemove(ml, mId, position);
    }

    public boolean delete() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistDelete(ml, mId);
    }

    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, includeMissing, onlyFavorites, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    @Override
    public boolean setFavorite(boolean favorite) {
        if (mId == 0L) return false;
        final Medialibrary ml = Medialibrary.getInstance();
        boolean ret = false;
        if (ml.isInitiated())
            ret = nativeSetFavorite(ml, mId, favorite);
        return ret;
    }

    private native MediaWrapper[] nativeGetTracks(Medialibrary ml, long id, boolean includeMissing, boolean onlyFavorites);
    private native MediaWrapper[] nativeGetPagedTracks(Medialibrary ml, long id, int nbItems, int offset, boolean includeMissing, boolean onlyFavorites);
    private native int nativeGetTracksCount(Medialibrary ml, long id, boolean includeMissing, boolean onlyFavorites);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query);
    private native boolean nativePlaylistAppend(Medialibrary ml, long id, long mediaId);
    private native boolean nativePlaylistAppendGroup(Medialibrary ml, long id, long[] mediaIds);
    private native boolean nativePlaylistAdd(Medialibrary ml, long id, long mediaId, int position);

    private native boolean nativePlaylistMove(Medialibrary ml, long id, int oldPosition, int position);

    private native boolean nativePlaylistRemove(Medialibrary ml, long id, int position);
    private native boolean nativePlaylistDelete(Medialibrary ml, long id);
    private native boolean nativeSetFavorite(Medialibrary ml, long id, boolean favorite);
}
