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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.moviepedia.databinding.MoviepediaItemBinding
import org.videolan.moviepedia.models.identify.Media
import org.videolan.moviepedia.models.identify.getImageUri
import org.videolan.moviepedia.models.identify.getYear
import org.videolan.tools.getLocaleLanguages
import org.videolan.vlc.gui.helpers.SelectorViewHolder

class MoviepediaResultAdapter internal constructor(private val layoutInflater: LayoutInflater) : RecyclerView.Adapter<MoviepediaResultAdapter.ViewHolder>() {

    private var dataList: List<Media>? = null
    internal lateinit var clickHandler: MoviepediaActivity.ClickHandler

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(MoviepediaItemBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val mediaResult = dataList!![position]
        holder.binding.item = mediaResult
        holder.binding.imageUrl = mediaResult.getImageUri(layoutInflater.context.getLocaleLanguages())
    }

    fun setItems(newList: List<Media>) {
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

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@BindingAdapter("year")
fun showYear(view: TextView, item: Media) {
    view.text = item.getYear()
}
