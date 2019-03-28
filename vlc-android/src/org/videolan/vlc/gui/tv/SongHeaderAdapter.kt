package org.videolan.vlc.gui.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.Medialibrary
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SongHeaderItemBinding

class SongHeaderAdapter(private val onHeaderSelected: OnHeaderSelected) : RecyclerView.Adapter<SongHeaderAdapter.ViewHolder>() {

    val alphaItems = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#")

    var sortType = Medialibrary.SORT_ALPHA

    var items = ArrayList<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongHeaderAdapter.ViewHolder {
        return ViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.song_header_item, parent, false) as SongHeaderItemBinding)

    }

    override fun getItemCount(): Int {
        return when (sortType) {
            Medialibrary.SORT_ALPHA -> alphaItems.size
            else -> items.size
        }


    }

    override fun onBindViewHolder(holder: SongHeaderAdapter.ViewHolder, position: Int) {

        if (sortType == Medialibrary.SORT_ALPHA) {
            holder.binding.setHeader(alphaItems[position])
            holder.binding.hasContent = items.contains(alphaItems[position])
        } else {
            holder.binding.setHeader(items[position])
            holder.binding.hasContent = true
        }
    }

    fun getItem(position: Int): String {
        return if (sortType == Medialibrary.SORT_ALPHA) {
            alphaItems[position]
        } else {
            items[position]
        }

    }


    inner class ViewHolder(var binding: SongHeaderItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.holder = this
        }

        fun onClick(v: View) {
            val item = getItem(layoutPosition)
            onHeaderSelected.onHeaderSelected(item)


        }


    }

    interface OnHeaderSelected {
        fun onHeaderSelected(header: String)
    }

}