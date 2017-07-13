package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

import java.util.List;

public class Playlist extends MediaLibraryItem {

    private int mTracksCount;

    protected Playlist(long id, String name, int trackCount) {
        super(id, name);
        mTracksCount = trackCount;
    }

    public MediaWrapper[] getTracks() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() ? nativeGetTracksFromPlaylist(ml, mId) : Medialibrary.EMPTY_COLLECTION;
    }

    public int getTracksCount() {
        return mTracksCount;
    }

    @Override
    public int getItemType() {
        return TYPE_PLAYLIST;
    }

    public boolean append(long mediaId) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() && nativePlaylistAppend(ml, mId, mediaId);
    }

    public boolean append(long[] mediaIds) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() && nativePlaylistAppendGroup(ml, mId, mediaIds);
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
        return ml != null && ml.isInitiated() && nativePlaylistAdd(ml, mId, mediaId, position);
    }

    public boolean move(long mediaId, int position) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() && nativePlaylistMove(ml, mId, mediaId, position);
    }

    public boolean remove(long mediaId) {
        Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() && nativePlaylistRemove(ml, mId, mediaId);
    }

    public boolean delete() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml != null && ml.isInitiated() && nativePlaylistDelete(ml, mId);
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

    private native MediaWrapper[] nativeGetTracksFromPlaylist(Medialibrary ml, long id);
    private native boolean nativePlaylistAppend(Medialibrary ml, long id, long mediaId);
    private native boolean nativePlaylistAppendGroup(Medialibrary ml, long id, long[] mediaIds);
    private native boolean nativePlaylistAdd(Medialibrary ml, long id, long mediaId, int position);
    private native boolean nativePlaylistMove(Medialibrary ml, long id, long mediaId, int position);
    private native boolean nativePlaylistRemove(Medialibrary ml, long id, long mediaId);
    private native boolean nativePlaylistDelete(Medialibrary ml, long id);
}
