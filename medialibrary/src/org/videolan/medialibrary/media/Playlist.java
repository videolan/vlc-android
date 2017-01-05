package org.videolan.medialibrary.media;

import android.os.Parcel;
import android.os.Parcelable;

import org.videolan.medialibrary.Medialibrary;

import java.util.List;

public class Playlist extends MediaLibraryItem {

    protected Playlist(long id, String name) {
        super(id, name);
    }

    protected Playlist(Parcel in) {
        super(in);
    }

    public MediaWrapper[] getTracks(Medialibrary ml) {
        return nativeGetTracksFromPlaylist(ml, mId);
    }

    @Override
    public int getItemType() {
        return TYPE_PLAYLIST;
    }

    public boolean append(Medialibrary ml, long mediaId) {
        return nativePlaylistAppend(ml, mId, mediaId);
    }

    public boolean append(Medialibrary ml, long[] mediaIds) {
        return nativePlaylistAppendGroup(ml, mId, mediaIds);
    }

    public boolean append(Medialibrary ml, List<Long> mediaIds) {
        long[] ids = new long[mediaIds.size()];
        for (int i = 0; i < ids.length; ++i)
            ids[i] = mediaIds.get(i).longValue();
        return nativePlaylistAppendGroup(ml, mId, ids);
    }

    public boolean add(Medialibrary ml, long mediaId, int position) {
        return nativePlaylistAdd(ml, mId, mediaId, position);
    }

    public boolean move(Medialibrary ml, long mediaId, int position) {
        return nativePlaylistMove(ml, mId, mediaId, position);
    }

    public boolean remove(Medialibrary ml, long mediaId) {
        return nativePlaylistRemove(ml, mId, mediaId);
    }

    public boolean delete(Medialibrary ml) {
        return nativePlaylistDelete(ml, mId);
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

    private native MediaWrapper[] nativeGetTracksFromPlaylist(Medialibrary ml, long id);
    private native boolean nativePlaylistAppend(Medialibrary ml, long id, long mediaId);
    private native boolean nativePlaylistAppendGroup(Medialibrary ml, long id, long[] mediaIds);
    private native boolean nativePlaylistAdd(Medialibrary ml, long id, long mediaId, int position);
    private native boolean nativePlaylistMove(Medialibrary ml, long id, long mediaId, int position);
    private native boolean nativePlaylistRemove(Medialibrary ml, long id, long mediaId);
    private native boolean nativePlaylistDelete(Medialibrary ml, long id);
}
