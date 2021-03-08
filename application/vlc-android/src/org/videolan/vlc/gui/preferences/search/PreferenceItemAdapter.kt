/*
 * ************************************************************************
 *  PreferenceItemAdapter.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.preferences.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.videolan.vlc.databinding.PreferenceItemBinding
import org.videolan.vlc.gui.preferences.PreferenceItem

private val cb = object : DiffUtil.ItemCallback<PreferenceItem>() {
    override fun areItemsTheSame(oldItem: PreferenceItem, newItem: PreferenceItem) = oldItem == newItem
    override fun areContentsTheSame(oldItem: PreferenceItem, newItem: PreferenceItem) = true
}

class PreferenceItemAdapter(val handler: ClickHandler) : ListAdapter<PreferenceItem, PreferenceItemAdapter.ViewHolder>(cb) {


    interface ClickHandler {
        fun onClick(item: PreferenceItem)
    }

    private lateinit var inflater : LayoutInflater

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (!this::inflater.isInitialized) inflater = LayoutInflater.from(parent.context)
        return ViewHolder(handler, PreferenceItemBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.item = getItem(position)
    }

    fun isEmpty() = itemCount == 0

    class ViewHolder(handler: ClickHandler, val binding: PreferenceItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.handler = handler
        }
    }

}