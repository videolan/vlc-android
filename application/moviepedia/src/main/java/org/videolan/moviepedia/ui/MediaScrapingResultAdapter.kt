/*
 * ************************************************************************
 *  NextResultAdapter.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.moviepedia.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import org.videolan.moviepedia.databinding.MoviepediaItemBinding
import org.videolan.moviepedia.models.resolver.ResolverMedia
import org.videolan.tools.getLocaleLanguages
import org.videolan.vlc.gui.helpers.SelectorViewHolder

class MediaScrapingResultAdapter internal constructor(private val layoutInflater: LayoutInflater) : RecyclerView.Adapter<MediaScrapingResultAdapter.ViewHolder>() {

    private var dataList: List<ResolverMedia>? = null
    internal lateinit var clickHandler: MediaScrapingActivity.ClickHandler

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(MoviepediaItemBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val mediaResult = dataList!![position]
        holder.binding.item = mediaResult
        holder.binding.imageUrl = mediaResult.imageUri(layoutInflater.context.getLocaleLanguages())
    }

    fun setItems(newList: List<ResolverMedia>) {
        dataList = newList
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return if (dataList == null) 0 else dataList!!.size
    }

    inner class ViewHolder(binding: MoviepediaItemBinding) : SelectorViewHolder<MoviepediaItemBinding>(binding) {

        init {
            binding.handler = clickHandler
        }
    }
}

@BindingAdapter("year")
fun showYear(view: TextView, item: ResolverMedia) {
    view.text = item.year()
}
