package org.videolan.vlc.gui

import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.databinding.SimpleItemBinding

private val cb = object : DiffUtil.ItemCallback<MediaLibraryItem>() {
    override fun areItemsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem) = oldItem == newItem
    override fun areContentsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem) = true
}

class SimpleAdapter(val handler: ClickHandler) : ListAdapter<MediaLibraryItem, SimpleAdapter.ViewHolder>(cb) {

    interface ClickHandler {
        fun onClick(item: MediaLibraryItem)
    }

    private lateinit var inflater : LayoutInflater

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (!this::inflater.isInitialized) inflater = LayoutInflater.from(parent.context)
        return ViewHolder(handler, SimpleItemBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.item = getItem(position)
    }

    fun isEmpty() = itemCount == 0

    class ViewHolder(handler: ClickHandler, val binding: SimpleItemBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        init {
            binding.handler = handler
        }
    }

}