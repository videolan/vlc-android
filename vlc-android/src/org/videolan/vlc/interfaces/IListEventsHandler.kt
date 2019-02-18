package org.videolan.vlc.interfaces

import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.media.MediaLibraryItem

interface IListEventsHandler {
    fun onRemove(position: Int, item: MediaLibraryItem)
    fun onMove(position: Int, item: MediaLibraryItem)
    fun onStartDrag(viewHolder: RecyclerView.ViewHolder )
}