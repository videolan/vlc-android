package org.videolan.vlc.interfaces

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.media.MediaLibraryItem

interface IEventsHandler {
    fun onClick(v: View, position: Int, item: MediaLibraryItem)
    fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean
    fun onImageClick(v: View, position: Int, item: MediaLibraryItem)
    fun onCtxClick(v: View, position: Int, item: MediaLibraryItem)
    fun onUpdateFinished(adapter: RecyclerView.Adapter<*>)
    fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem)
}
