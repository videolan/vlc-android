package org.videolan.medialibrary.interfaces;

import org.videolan.medialibrary.media.MediaWrapper;

public interface MediaAddedCb {
    void onMediaAdded(MediaWrapper[] mediaList);
}
