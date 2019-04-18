/*****************************************************************************
 * SearchFragment.java
 *
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
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
package org.videolan.vlc.gui.tv.audioplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.databinding.TvSimpleListItemBinding
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.util.Util

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class PlaylistAdapter
internal constructor(private val audioPlayerActivity: AudioPlayerActivity) : DiffUtilAdapter<MediaWrapper, PlaylistAdapter.ViewHolder>() {
    internal var selectedItem = -1
        private set

    inner class ViewHolder(vdb: TvSimpleListItemBinding) : SelectorViewHolder<TvSimpleListItemBinding>(vdb), View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            setSelection(layoutPosition)
            audioPlayerActivity.playSelection()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(TvSimpleListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.media = dataset[position]
        val textAppearance = if (position == selectedItem) R.style.TextAppearance_AppCompat_Title else R.style.TextAppearance_AppCompat_Medium
        val ctx = holder.itemView.context
        holder.binding.artist.setTextAppearance(ctx, textAppearance)
        holder.binding.title.setTextAppearance(ctx, textAppearance)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (Util.isListEmpty(payloads))
            super.onBindViewHolder(holder, position, payloads)
        else {
            val textAppearance = if (payloads[0] as Boolean) R.style.TextAppearance_AppCompat_Title else R.style.TextAppearance_AppCompat_Medium
            val ctx = holder.itemView.context
            holder.binding.artist.setTextAppearance(ctx, textAppearance)
            holder.binding.title.setTextAppearance(ctx, textAppearance)
        }
    }

    fun setSelection(pos: Int) {
        if (pos == selectedItem) return
        val previous = selectedItem
        selectedItem = pos
        if (previous != -1) notifyItemChanged(previous, false)
        if (pos != -1) notifyItemChanged(selectedItem, true)
    }

    override fun onUpdateFinished() {
        audioPlayerActivity.onUpdateFinished()

    }

    companion object {
        const val TAG = "VLC/PlaylistAdapter"
    }
}