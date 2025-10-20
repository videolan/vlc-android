package org.videolan.vlc.gui

import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
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

    var defaultCover:BitmapDrawable? = null

    interface ClickHandler {
        fun onClick(position: Int)
    }

    private lateinit var inflater : LayoutInflater

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (!this::inflater.isInitialized) inflater = LayoutInflater.from(parent.context)
        return ViewHolder(SimpleItemBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val isSelected = multiSelectHelper.isSelected(position)
        holder.selectView(isSelected)
        holder.binding.position = position
        holder.binding.item = getItemByPosition(position)
        holder.binding.imageWidth = 48.dp
        (getItemByPosition(position) as? DummyItem)?.let {
            holder.binding.cover =  getDummyItemIcon(holder.itemView.context, it)
        }
        if (defaultCover != null) {
            holder.binding.cover = defaultCover
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNullOrEmpty())
            super.onBindViewHolder(holder, position, payloads)
        else {
            payloads.forEach {
                when (it) {
                    UPDATE_SELECTION -> holder.selectView(multiSelectHelper.isSelected(position))
                }
            }
        }
    }

    fun isEmpty() = itemCount == 0

    override fun getItemByPosition(position: Int): MediaLibraryItem? {
        return if (position in 0 until itemCount) super.getItem(position) else null
    }

    inner class ViewHolder(binding: SimpleItemBinding) :  SelectorViewHolder<SimpleItemBinding>(binding) {
        init {
            binding.holder = this
        }

        override fun selectView(selected: Boolean) {
            binding.setVariable(BR.selected, selected)
        }

        fun onClick(@Suppress("UNUSED_PARAMETER") v: View) {
            handler.onClick(layoutPosition)
        }

    }

}