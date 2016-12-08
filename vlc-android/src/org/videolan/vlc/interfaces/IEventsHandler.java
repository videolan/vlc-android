package org.videolan.vlc.interfaces;

import android.view.View;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.gui.audio.AudioBrowserAdapter;

public interface IEventsHandler {
    void onClick(View v, int position, MediaLibraryItem item);
    boolean onLongClick(View v, int position, MediaLibraryItem item);
    void onCtxClick(View v, int position, MediaLibraryItem item);
    void startActionMode();
    void invalidateActionMode();
    void onUpdateFinished(AudioBrowserAdapter adapter);
}
