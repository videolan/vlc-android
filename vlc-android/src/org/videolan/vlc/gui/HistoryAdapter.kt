/*****************************************************************************
 * HistoryAdapter.java
 *
 * Copyright © 2012-2015 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 */
package org.videolan.vlc.gui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.databinding.HistoryItemBinding
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.getMediaIconDrawable
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.Util
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class HistoryAdapter(private val mEventsHandler: IEventsHandler) : DiffUtilAdapter<MediaWrapper, HistoryAdapter.ViewHolder>() {
    private var mLayoutInflater: LayoutInflater? = null

    val selection: List<MediaWrapper>
        get() {
            val selection = LinkedList<MediaWrapper>()
            for (media in dataset) {
                if (media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED))
                    selection.add(media)
            }
            return selection
        }

    inner class ViewHolder(binding: HistoryItemBinding) : SelectorViewHolder<HistoryItemBinding>(binding) {

        init {
            this.binding = binding
            binding.holder = this
        }

        fun onClick(v: View) {
            val position = layoutPosition
            mEventsHandler.onClick(v, position, getItem(position))
        }

        fun onLongClick(v: View): Boolean {
            val position = layoutPosition
            return mEventsHandler.onLongClick(v, position, getItem(position))
        }

        fun onImageClick(v: View) {
            val position = layoutPosition
            mEventsHandler.onImageClick(v, position, getItem(position))
        }

        override fun isSelected(): Boolean {
            return getItem(layoutPosition).hasStateFlags(MediaLibraryItem.FLAG_SELECTED)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (mLayoutInflater == null)
            mLayoutInflater = LayoutInflater.from(parent.context)
        return ViewHolder(HistoryItemBinding.inflate(mLayoutInflater!!, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = getItem(position)
        val isSelected = media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)
        holder.binding.media = media
        holder.binding.cover = getMediaIconDrawable(holder.itemView.context, media.type)
        holder.selectView(isSelected)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (Util.isListEmpty(payloads))
            super.onBindViewHolder(holder, position, payloads)
        else
            holder.selectView((payloads[0] as MediaLibraryItem).hasStateFlags(MediaLibraryItem.FLAG_SELECTED))
    }

    override fun getItemId(arg0: Int): Long {
        return 0
    }

    override fun getItemCount(): Int {
        return dataset.size
    }

    override fun onUpdateFinished() {
        mEventsHandler.onUpdateFinished(this)
    }

    companion object {

        const val TAG = "VLC/HistoryAdapter"
    }
}