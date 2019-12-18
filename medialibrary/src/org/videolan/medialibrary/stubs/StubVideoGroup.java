package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.VideoGroup;

public class StubVideoGroup extends VideoGroup {
    public StubVideoGroup(String name, int count) {
        super(name, count);
    }

    public StubVideoGroup(Parcel in) {
        super(in);
    }

    @Override
    public MediaWrapper[] media(int sort, boolean desc, int nbItems, int offset) {
        return new MediaWrapper[0];
    }

    @Override
    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        return new MediaWrapper[0];
    }

    @Override
    public int searchTracksCount(String query) {
        return 0;
    }
}
