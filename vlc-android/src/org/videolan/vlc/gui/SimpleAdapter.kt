package org.videolan.vlc.gui

import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.databinding.SimpleItemBinding


val cb = object : DiffUtil.ItemCallback<MediaLibraryItem>() {
    override fun areItemsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem) = oldItem == newItem

    override fun areContentsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem) = true
}

class SimpleAdapter(val handler: FavoritesHandler) : ListAdapter<MediaLibraryItem, SimpleAdapter.ViewHolder>(cb) {

    interface FavoritesHandler {
        fun onClick(item: MediaLibraryItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context);
        return ViewHolder(handler, SimpleItemBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.item = getItem(position)
    }

    class ViewHolder(val handler: FavoritesHandler, val binding: SimpleItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.handler = handler
        }
    }

}