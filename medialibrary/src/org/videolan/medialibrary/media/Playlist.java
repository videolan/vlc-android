package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

import java.util.List;

@SuppressWarnings("JniMissingFunction")
public class Playlist extends MediaLibraryItem {

    private int mTracksCount;

    protected Playlist(long id, String name, int trackCount) {
        super(id, name);
        mTracksCount = trackCount;
    }

    public MediaWrapper[] getTracks() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracks(ml, mId) : Medialibrary.EMPTY_COLLECTION;
    }

    public MediaWrapper[] getPagedTracks(int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetPagedTracks(ml, mId, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int getTracksCount() {
        return mTracksCount;
    }

    public int getRealTracksCount() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetTracksCount(ml, mId) : 0;
    }

    @Override
    public int getItemType() {
        return TYPE_PLAYLIST;
    }

    public boolean append(long mediaId) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAppend(ml, mId, mediaId);
    }

    public boolean append(long[] mediaIds) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAppendGroup(ml, mId, mediaIds);
    }

    public boolean append(List<Long> mediaIds) {
        Medialibrary ml = Medialibrary.getInstance();
        if (ml == null || !ml.isInitiated())
            return false;
        long[] ids = new long[mediaIds.size()];
        for (int i = 0; i < ids.length; ++i)
            ids[i] = mediaIds.get(i);
        return nativePlaylistAppendGroup(ml, mId, ids);
    }

    public boolean add(long mediaId, int position) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistAdd(ml, mId, mediaId, position);
    }

    public boolean move(long mediaId, int position) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistMove(ml, mId, mediaId, position);
    }

    public boolean remove(long mediaId) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistRemove(ml, mId, mediaId);
    }

    public boolean delete() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativePlaylistDelete(ml, mId);
    }

    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeSearch(ml, mId, query, sort, desc, nbItems, offset) : Medialibrary.EMPTY_COLLECTION;
    }

    public int searchTracksCount(String query) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSearchCount(ml, mId, query) : 0;
    }

    public static Parcelable.Creator<Playlist> CREATOR
            = new Parcelable.Creator<Playlist>() {
        public Playlist createFromParcel(Parcel in) {
            return new Playlist(in);
        }

        public Playlist[] newArray(int size) {
            return new Playlist[size];
        }
    };

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeInt(mTracksCount);
    }

    private Playlist(Parcel in) {
        super(in);
        this.mTracksCount = in.readInt();
    }

    private native MediaWrapper[] nativeGetTracks(Medialibrary ml, long id);
    private native MediaWrapper[] nativeGetPagedTracks(Medialibrary ml, long id, int nbItems, int offset);
    private native int nativeGetTracksCount(Medialibrary ml, long id);
    private native MediaWrapper[] nativeSearch(Medialibrary ml, long mId, String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchCount(Medialibrary ml, long mId, String query);
    private native boolean nativePlaylistAppend(Medialibrary ml, long id, long mediaId);
    private native boolean nativePlaylistAppendGroup(Medialibrary ml, long id, long[] mediaIds);
    private native boolean nativePlaylistAdd(Medialibrary ml, long id, long mediaId, int position);
    private native boolean nativePlaylistMove(Medialibrary ml, long id, long mediaId, int position);
    private native boolean nativePlaylistRemove(Medialibrary ml, long id, long mediaId);
    private native boolean nativePlaylistDelete(Medialibrary ml, long id);
}
