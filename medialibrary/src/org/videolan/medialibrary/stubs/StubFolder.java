package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AFolder;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;

public class StubFolder extends AFolder {

    public StubFolder(long id, String name, String mrl) {
        super(id, name, mrl);
    }
    public StubFolder(Parcel in) {
        super(in);
    }

    public AMediaWrapper[] media(int type, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int mediaCount(int type) {
        return 0;
    }

    public AFolder[] subfolders(int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int subfoldersCount(int type) {
        return 0;
    }

    public AMediaWrapper[] searchTracks(String query, int mediaType, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int searchTracksCount(String query, int mediaType) {
        return 0;
    }
}
