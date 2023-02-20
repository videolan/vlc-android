package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.VideoGroup;

public class StubVideoGroup extends VideoGroup {
    public StubVideoGroup(String name, int count, int presentCount, int presentSeen, boolean isFavorite) {
        super(0L, name, count, presentCount, presentSeen, isFavorite);
    }

    public StubVideoGroup(Parcel in) {
        super(in);
    }

    @Override
    public MediaWrapper[] media(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return new MediaWrapper[0];
    }

    @Override
    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return new MediaWrapper[0];
    }

    @Override
    public int searchTracksCount(String query) {
        return 0;
    }

    @Override
    public boolean add(long mediaId) {
        return false;
    }

    @Override
    public boolean remove(long mediaId) {
        return false;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public boolean rename(String name) {
        return false;
    }

    @Override
    public boolean userInteracted() {
        return false;
    }

    @Override
    public long duration() {
        return 0L;
    }

    @Override
    public boolean destroy() {
        return false;
    }
}
