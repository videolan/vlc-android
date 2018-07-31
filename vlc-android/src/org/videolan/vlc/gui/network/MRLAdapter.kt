/*****************************************************************************
 * MRLAdapter.java
 *
 * Copyright © 2014-2015 VLC authors and VideoLAN
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
package org.videolan.vlc.gui.network

import android.databinding.DataBindingUtil
import android.net.Uri
import android.support.v7.widget.RecyclerView
import android.text.Layout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.coroutines.experimental.channels.SendChannel
import org.videolan.libvlc.Media

import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.databinding.MrlItemBinding

internal class MRLAdapter(private val eventActor: SendChannel<MediaWrapper>) : RecyclerView.Adapter<MRLAdapter.ViewHolder>() {
    private var dataset: Array<MediaWrapper>? = null

    val isEmpty: Boolean
        get() = itemCount == 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MRLAdapter.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding: MrlItemBinding = DataBindingUtil.inflate(inflater, R.layout.mrl_item, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataset?.get(position)
        holder.uriTv.text = Uri.decode(item?.location)
        holder.titleTv.text = Uri.decode(item?.title)
    }

    fun setList(list: Array<MediaWrapper>?) {
        dataset = list
        notifyDataSetChanged()
    }

    fun getItem(position: Int): MediaWrapper? = when {
        position >= itemCount -> null
        position < 0 -> null
        else -> dataset?.get(position)
    }

    override fun getItemCount() = dataset?.size ?: 0

    inner class ViewHolder(val binding: MrlItemBinding) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {
        internal val uriTv: TextView = binding.mrlItemUri
        internal val titleTv: TextView = binding.mrlItemTitle

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            dataset?.get(layoutPosition)?.let { eventActor.offer(it) }
        }
    }

}
