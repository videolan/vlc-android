/*****************************************************************************
 * MediaInfoAdapter.java
 *
 * Copyright © 2011-2017 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.video

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.tools.readableSize
import org.videolan.vlc.R
import org.videolan.vlc.util.LocaleUtil

class MediaInfoAdapter : RecyclerView.Adapter<MediaInfoAdapter.ViewHolder>() {
    private lateinit var inflater: LayoutInflater
    private var dataset: List<IMedia.Track>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (!::inflater.isInitialized)
            inflater = LayoutInflater.from(parent.context)
        return ViewHolder(inflater.inflate(R.layout.info_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = dataset!![position]
        val title: String
        val textBuilder = StringBuilder()
        val res = holder.itemView.context.resources
        when (track.type) {
            IMedia.Track.Type.Audio -> {
                title = res.getString(R.string.track_audio)
                appendCommon(textBuilder, res, track)
                appendAudio(textBuilder, res, track as IMedia.AudioTrack)
            }
            IMedia.Track.Type.Video -> {
                title = res.getString(R.string.track_video)
                appendCommon(textBuilder, res, track)
                appendVideo(textBuilder, res, track as IMedia.VideoTrack)
            }
            IMedia.Track.Type.Text -> {
                title = res.getString(R.string.track_text)
                appendCommon(textBuilder, res, track)
            }
            else -> title = res.getString(R.string.track_unknown)
        }
        holder.title.text = title
        holder.text.text = textBuilder.toString()
    }

    override fun getItemCount() = dataset?.size ?: 0

    fun setTracks(tracks: List<IMedia.Track>) {
        val size = itemCount
        dataset = tracks
        if (size > 0) notifyItemRangeRemoved(0, size - 1)
        notifyItemRangeInserted(0, tracks.size)
    }

    private fun appendCommon(textBuilder: StringBuilder, res: Resources, track: IMedia.Track) {
        if (track.bitrate != 0)
            textBuilder.append(res.getString(R.string.track_bitrate_info, track.bitrate.toLong().readableSize()))
        textBuilder.append(res.getString(R.string.track_codec_info, track.codec))
        if (track.language != null && !track.language.equals("und", ignoreCase = true))
            textBuilder.append(res.getString(R.string.track_language_info, LocaleUtil.getLocaleName(track.language)))
    }

    private fun appendAudio(textBuilder: StringBuilder, res: Resources, track: IMedia.AudioTrack) {
        textBuilder.append(res.getQuantityString(R.plurals.track_channels_info_quantity, track.channels, track.channels))
        textBuilder.append(res.getString(R.string.track_samplerate_info, track.rate))
    }

    private fun appendVideo(textBuilder: StringBuilder, res: Resources, track: IMedia.VideoTrack) {
        val frameRate = track.frameRateNum / track.frameRateDen.toDouble()
        if (track.width != 0 && track.height != 0)
            textBuilder.append(res.getString(R.string.track_resolution_info, track.width, track.height))
        if (!frameRate.isNaN())
            textBuilder.append(res.getString(R.string.track_framerate_info, frameRate))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val text: TextView = itemView.findViewById(R.id.subtitle)
    }
}
