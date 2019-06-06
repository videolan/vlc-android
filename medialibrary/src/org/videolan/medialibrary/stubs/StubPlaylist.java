package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AMediaWrapper;
import org.videolan.medialibrary.interfaces.media.APlaylist;

import java.util.List;

public class StubPlaylist extends APlaylist {

    public StubPlaylist(long id, String name, int trackCount) {
        super(id, name, trackCount);
    }

    public StubPlaylist(Parcel in) {
        super(in);
    }

    public AMediaWrapper[] getTracks() {
        return null;
    }

    public AMediaWrapper[] getPagedTracks(int nbItems, int offset) {
        return null;
    }

    public int getRealTracksCount() {
        return 0;
    }

    public boolean append(long mediaId) {
        return true;
    }

    public boolean append(long[] mediaIds) {
        return true;
    }

    public boolean append(List<Long> mediaIds) {
        return true;
    }

    public boolean add(long mediaId, int position) {
        return true;
    }

    public boolean move(int oldPosition, int newPosition) {
        return true;
    }

    public boolean remove(int position) {
        return true;
    }

    public boolean delete() {
        return true;
    }

    public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int searchTracksCount(String query) {
        return 0;
    }
}
