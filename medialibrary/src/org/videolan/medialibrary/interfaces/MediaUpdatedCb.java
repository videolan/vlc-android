package org.videolan.medialibrary.interfaces;

import org.videolan.medialibrary.media.MediaWrapper;

public interface MediaUpdatedCb {
    void onMediaUpdated(MediaWrapper[] mediaList);
}
