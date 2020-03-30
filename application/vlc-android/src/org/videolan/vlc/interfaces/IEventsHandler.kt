package org.videolan.vlc.interfaces

import android.view.View
import androidx.recyclerview.widget.RecyclerView

interface IEventsHandler<T> {
    fun onClick(v: View, position: Int, item: T)
    fun onLongClick(v: View, position: Int, item: T): Boolean
    fun onImageClick(v: View, position: Int, item: T)
    fun onCtxClick(v: View, position: Int, item: T)
    fun onUpdateFinished(adapter: RecyclerView.Adapter<*>)
    fun onMainActionClick(v: View, position: Int, item: T)
    fun onItemFocused(v: View, item: T)
}