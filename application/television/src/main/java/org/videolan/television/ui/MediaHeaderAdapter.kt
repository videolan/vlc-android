package org.videolan.television.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.television.R
import org.videolan.television.databinding.SongHeaderItemBinding

class MediaHeaderAdapter(private val onHeaderSelected: OnHeaderSelected) : RecyclerView.Adapter<MediaHeaderAdapter.ViewHolder>() {

    private val alphaItems = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#")

    var sortType = Medialibrary.SORT_ALPHA

    var items = ArrayList<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.song_header_item, parent, false) as SongHeaderItemBinding)
    }

    override fun getItemCount(): Int {
        return when (sortType) {
            Medialibrary.SORT_ALPHA, Medialibrary.SORT_DEFAULT -> alphaItems.size
            else -> items.size
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        if (sortType == Medialibrary.SORT_ALPHA || sortType == Medialibrary.SORT_DEFAULT) {
            holder.binding.headerText = alphaItems[position]
            holder.binding.hasContent = items.contains(alphaItems[position])
        } else {
            holder.binding.headerText = items[position]
            holder.binding.hasContent = true
        }
    }

    fun getItem(position: Int): String {
        return if (sortType == Medialibrary.SORT_ALPHA || sortType == Medialibrary.SORT_DEFAULT) {
            alphaItems[position]
        } else {
            items[position]
        }
    }


    inner class ViewHolder(var binding: SongHeaderItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.holder = this
        }

        fun onClick(@Suppress("UNUSED_PARAMETER") v: View) {
            val item = getItem(layoutPosition)
            onHeaderSelected.onHeaderSelected(item)
        }
    }

    interface OnHeaderSelected {
        fun onHeaderSelected(header: String)
    }

}
