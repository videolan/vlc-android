package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup;

public class StubVideoGroup extends AbstractVideoGroup {
    public StubVideoGroup(String name, int count) {
        super(name, count);
    }

    public StubVideoGroup(Parcel in) {
        super(in);
    }

    @Override
    public AbstractMediaWrapper[] media(int sort, boolean desc, int nbItems, int offset) {
        return new AbstractMediaWrapper[0];
    }

    @Override
    public AbstractMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        return new AbstractMediaWrapper[0];
    }

    @Override
    public int searchTracksCount(String query) {
        return 0;
    }
}
