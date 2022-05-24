/*
 * ************************************************************************
 *  TrackAdapter.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.dialogs.adapters

import android.os.Handler
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.videolan.libvlc.MediaPlayer
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VideoTrackItemBinding
import org.videolan.vlc.gui.helpers.enableMarqueeEffect

class TrackAdapter(private val tracks: Array<MediaPlayer.TrackDescription>, var selectedTrack: MediaPlayer.TrackDescription?, val trackTypePrefix:String) : RecyclerView.Adapter<TrackAdapter.ViewHolder>() {

    lateinit var trackSelectedListener: (MediaPlayer.TrackDescription) -> Unit
    private val handler by lazy(LazyThreadSafetyMode.NONE) { Handler() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = VideoTrackItemBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    fun setOnTrackSelectedListener(listener: (MediaPlayer.TrackDescription) -> Unit) {
        trackSelectedListener = listener
    }

    override fun getItemCount() = tracks.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(tracks[position], tracks[position] == selectedTrack)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (Settings.listTitleEllipsize == 4) enableMarqueeEffect(recyclerView, handler)
    }


    inner class ViewHolder(val binding: VideoTrackItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {

            itemView.setOnClickListener {
                selectedTrack = tracks[layoutPosition]
                notifyDataSetChanged()
                trackSelectedListener.invoke(tracks[layoutPosition])
            }
        }

        fun bind(trackDescription: MediaPlayer.TrackDescription, selected: Boolean) {
            binding.track = trackDescription
            val context = binding.root.context
            binding.contentDescription = context.getString(R.string.talkback_track, trackTypePrefix, if (trackDescription.id == -1) context.getString(R.string.disable_track) else trackDescription.name, if (selected) context.getString(R.string.selected) else "")
            binding.selected = selected
            binding.executePendingBindings()
        }
    }
}