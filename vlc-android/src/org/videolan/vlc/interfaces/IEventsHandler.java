package org.videolan.vlc.interfaces;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import org.videolan.medialibrary.media.MediaLibraryItem;

public interface IEventsHandler {
    void onClick(View v, int position, MediaLibraryItem item);
    boolean onLongClick(View v, int position, MediaLibraryItem item);
    void onCtxClick(View v, int position, MediaLibraryItem item);
    void onUpdateFinished(RecyclerView.Adapter adapter);
}
