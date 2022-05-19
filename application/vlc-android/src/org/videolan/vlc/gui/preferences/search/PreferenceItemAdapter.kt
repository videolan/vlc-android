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

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.videolan.vlc.databinding.PreferenceItemBinding
import java.util.*

private val cb = object : DiffUtil.ItemCallback<PreferenceItem>() {
    override fun areItemsTheSame(oldItem: PreferenceItem, newItem: PreferenceItem) = oldItem == newItem
    override fun areContentsTheSame(oldItem: PreferenceItem, newItem: PreferenceItem) = true
}

class PreferenceItemAdapter(val handler: ClickHandler) : ListAdapter<PreferenceItem, PreferenceItemAdapter.ViewHolder>(cb) {

    interface ClickHandler {
        fun onClick(item: PreferenceItem)
    }

    var showTranslation: Boolean = false
    set(value) {
        field = value
        notifyItemRangeChanged(0, itemCount)
    }
    var query: String = ""
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(handler, PreferenceItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.item = item
        holder.binding.title = if (showTranslation) item.titleEng else item.title
        holder.binding.description = if (showTranslation) item.summaryEng else item.summary
        holder.binding.category = if (showTranslation) item.categoryEng else item.category
        holder.binding.query = query
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
        if (payloads.isNullOrEmpty())
            super.onBindViewHolder(holder, position, payloads)
        else {
            val item = getItem(position)
            holder.binding.title = if (showTranslation) item.titleEng else item.title
            holder.binding.description = if (showTranslation) item.summaryEng else item.summary
            holder.binding.category = if (showTranslation) item.categoryEng else item.category
        }
    }

    fun isEmpty() = itemCount == 0

    class ViewHolder(handler: ClickHandler, val binding: PreferenceItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.handler = handler
        }
    }
}

@BindingAdapter("searchText", "searchQueryString")
fun searchText(view: TextView, text: String, query: String) {
    val spannableStringBuilder = SpannableStringBuilder(text)
    val indexOf = text.lowercase(Locale.getDefault()).indexOf(query.lowercase(Locale.getDefault()))
    if (indexOf != -1) spannableStringBuilder.setSpan(StyleSpan(Typeface.BOLD), indexOf, indexOf + query.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    if (indexOf != -1) spannableStringBuilder.setSpan(UnderlineSpan(), indexOf, indexOf + query.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    view.text = spannableStringBuilder
}