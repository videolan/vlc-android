package org.videolan.vlc.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.gui.DiffUtilAdapter


@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class MediaItemDiffCallback<T : MediaLibraryItem> : DiffUtilAdapter.DiffCallback<T>() {

    override fun getOldListSize(): Int {
        return oldList.size
    }

    override fun getNewListSize(): Int {
        return newList.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        return oldItem === newItem || oldItem == null == (newItem == null) && oldItem.equals(newItem)
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return true
    }

    companion object {
        private val TAG = "MediaItemDiffCallback"
    }
}
