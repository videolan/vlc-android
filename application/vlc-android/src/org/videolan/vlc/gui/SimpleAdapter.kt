package org.videolan.vlc.gui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.UPDATE_SELECTION
import org.videolan.tools.MultiSelectAdapter
import org.videolan.tools.MultiSelectHelper
import org.videolan.tools.dp
import org.videolan.vlc.BR
import org.videolan.vlc.databinding.SimpleItemBinding
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.getDummyItemIcon

private val cb = object : DiffUtil.ItemCallback<MediaLibraryItem>() {
    override fun areItemsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem) = oldItem == newItem
    override fun areContentsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem) = true
}

class SimpleAdapter(val handler: ClickHandler) : ListAdapter<MediaLibraryItem, SimpleAdapter.ViewHolder>(cb),
    MultiSelectAdapter<MediaLibraryItem> {
    val multiSelectHelper: MultiSelectHelper<MediaLibraryItem> = MultiSelectHelper(this, UPDATE_SELECTION)


    interface ClickHandler {
        fun onClick(item: MediaLibraryItem, position: Int)
    }

    private lateinit var inflater : LayoutInflater

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (!this::inflater.isInitialized) inflater = LayoutInflater.from(parent.context)
        return ViewHolder(handler, SimpleItemBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val isSelected = multiSelectHelper.isSelected(position)
        holder.selectView(isSelected)
        holder.binding.position = position
        holder.binding.item = getItem(position)
        holder.binding.imageWidth = 48.dp
        (getItem(position) as? DummyItem)?.let {
            holder.binding.cover =  getDummyItemIcon(holder.itemView.context, it)
        }
    }

    fun isEmpty() = itemCount == 0

    override fun getItem(position: Int): MediaLibraryItem? {
        return if (position in 0 until itemCount) super.getItem(position) else null
    }

    inner class ViewHolder(handler: ClickHandler,  binding: SimpleItemBinding) :  SelectorViewHolder<SimpleItemBinding>(binding) {
        init {
            binding.handler = handler
        }

        override fun selectView(selected: Boolean) {
            binding.setVariable(BR.selected, selected)
        }
    }

}