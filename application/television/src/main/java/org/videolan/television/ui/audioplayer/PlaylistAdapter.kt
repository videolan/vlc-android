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
package org.videolan.television.ui.audioplayer

import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.television.R
import org.videolan.television.databinding.TvPlaylistItemBinding
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.gui.view.MiniVisualizer
import org.videolan.vlc.viewmodels.PlaylistModel

class PlaylistAdapter
internal constructor(private val audioPlayerActivity: AudioPlayerActivity, val model: PlaylistModel) : DiffUtilAdapter<MediaWrapper, PlaylistAdapter.ViewHolder>() {
    var selectedItem = -1
        private set
    private var currentPlayingVisu: MiniVisualizer? = null
    private var defaultCoverAudio: BitmapDrawable = BitmapDrawable(audioPlayerActivity.resources, getBitmapFromDrawable(audioPlayerActivity, R.drawable.ic_song_background))

    inner class ViewHolder(vdb: TvPlaylistItemBinding) : SelectorViewHolder<TvPlaylistItemBinding>(vdb), View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            setSelection(layoutPosition)
            audioPlayerActivity.playSelection()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(TvPlaylistItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.media = dataset[position]
        if (selectedItem == position) {
            if (model.playing) holder.binding.playing.start() else holder.binding.playing.stop()
            holder.binding.playing.visibility = View.VISIBLE
            holder.binding.coverImage.visibility = View.INVISIBLE
            currentPlayingVisu = holder.binding.playing
        } else {
            holder.binding.playing.stop()
            holder.binding.playing.visibility = View.INVISIBLE
            holder.binding.coverImage.visibility = View.VISIBLE
        }
        holder.binding.scaleType = ImageView.ScaleType.CENTER_CROP
        holder.binding.cover = defaultCoverAudio
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNullOrEmpty())
            super.onBindViewHolder(holder, position, payloads)
        else {
            val isCurrent = payloads[0] as Boolean
            val shouldStart = isCurrent && model.playing
            if (shouldStart) holder.binding.playing.start() else holder.binding.playing.stop()
            if (isCurrent) {
                holder.binding.playing.visibility = View.VISIBLE
                holder.binding.coverImage.visibility = View.INVISIBLE
            } else {
                holder.binding.playing.visibility = View.INVISIBLE
                holder.binding.coverImage.visibility = View.VISIBLE
            }
            holder.binding.scaleType = ImageView.ScaleType.CENTER_CROP
            holder.binding.cover = defaultCoverAudio

        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        currentPlayingVisu = null
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