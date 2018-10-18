package org.videolan.vlc.interfaces

import androidx.recyclerview.widget.RecyclerView
import android.view.View

import org.videolan.medialibrary.media.MediaLibraryItem

interface IEventsHandler {
    fun onClick(v: View, position: Int, item: MediaLibraryItem)
    fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean
    fun onCtxClick(v: View, position: Int, item: MediaLibraryItem)
    fun onUpdateFinished(adapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>)
}
