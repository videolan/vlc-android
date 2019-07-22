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
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.MultiSelectAdapter
import org.videolan.tools.MultiSelectHelper
import org.videolan.vlc.databinding.HistoryItemBinding
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.getMediaIconDrawable
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.UPDATE_SELECTION
import org.videolan.vlc.util.Util

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class HistoryAdapter(private val mEventsHandler: IEventsHandler) : DiffUtilAdapter<AbstractMediaWrapper, HistoryAdapter.ViewHolder>(), MultiSelectAdapter<AbstractMediaWrapper> {
    private var mLayoutInflater: LayoutInflater? = null
    var multiSelectHelper: MultiSelectHelper<AbstractMediaWrapper> = MultiSelectHelper(this, UPDATE_SELECTION)

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
        holder.binding.media = media
        holder.binding.cover = getMediaIconDrawable(holder.itemView.context, media.type)
        (holder.binding.icon.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = if (media.type == AbstractMediaWrapper.TYPE_VIDEO) "16:10" else "1"
        holder.selectView(multiSelectHelper.isSelected(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (Util.isListEmpty(payloads))
            super.onBindViewHolder(holder, position, payloads)
        else
            holder.selectView(multiSelectHelper.isSelected(position))
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