package org.videolan.vlc.gui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.databinding.SearchItemBinding
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.UiTools


class SearchResultAdapter internal constructor(private val mLayoutInflater: LayoutInflater) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    private var mDataList: Array<MediaLibraryItem>? = null
    internal lateinit var mClickHandler: SearchActivity.ClickHandler

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(SearchItemBinding.inflate(mLayoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (mDataList!![position].artworkMrl.isNullOrEmpty())
            holder.binding.cover = UiTools.getDefaultCover(holder.itemView.context, mDataList!![position])
        holder.binding.item = mDataList!![position]
    }

    fun add(newList: Array<MediaLibraryItem>) {
        mDataList = newList
        notifyDataSetChanged()
    }

    internal fun setClickHandler(clickHandler: SearchActivity.ClickHandler) {
        mClickHandler = clickHandler
    }

    override fun getItemCount(): Int {
        return if (mDataList == null) 0 else mDataList!!.size
    }

    inner class ViewHolder(binding: SearchItemBinding) : SelectorViewHolder<SearchItemBinding>(binding) {

        init {
            binding.holder = this
            binding.handler = mClickHandler
        }
    }
}
