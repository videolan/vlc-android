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

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.channels.SendChannel
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.databinding.MrlItemBinding
import org.videolan.vlc.gui.DiffUtilAdapter

internal class MRLAdapter(private val eventActor: SendChannel<MrlAction>) : DiffUtilAdapter<MediaWrapper, MRLAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MRLAdapter.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding: MrlItemBinding = DataBindingUtil.inflate(inflater, R.layout.mrl_item, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataset.get(position)
        holder.binding.mrlItemUri.text = Uri.decode(item.location)
        holder.binding.mrlItemTitle.text = Uri.decode(item.title)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNullOrEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        for (payload in payloads) {
            if (payload is String) holder.binding.mrlItemTitle.text = payload
        }
    }

    inner class ViewHolder(val binding: MrlItemBinding) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener { eventActor.offer(ShowContext(layoutPosition)) }
            binding.mrlCtx.setOnClickListener { eventActor.offer(ShowContext(layoutPosition)) }
        }

        override fun onClick(v: View) {
            dataset.get(layoutPosition).let { eventActor.offer(Playmedia(it)) }
        }
    }

    override fun createCB() = object : DiffCallback<MediaWrapper>() {
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
                    && oldList[oldItemPosition].title == newList[newItemPosition].title
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int) = newList[newItemPosition].title
    }
}

sealed class MrlAction
class Playmedia(val media: MediaWrapper) : MrlAction()
class ShowContext(val position: Int) : MrlAction()
