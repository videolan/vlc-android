/*
 * ************************************************************************
 *  VideoDetailsPresenter.kt
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

package org.videolan.television.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import androidx.leanback.graphics.ColorOverlayDimmer
import org.videolan.television.databinding.TvVideoDetailsBinding
import org.videolan.television.R

class VideoDetailsPresenter(private val context: Context, private val screenWidth: Int) : FullWidthRowPresenter() {

    private lateinit var binding: TvVideoDetailsBinding

    override fun createRowViewHolder(parent: ViewGroup): VideoDetailsViewHolder {
        binding = DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.tv_video_details, parent, false)
        binding.container.minWidth = (screenWidth - (context.resources.getDimension(R.dimen.tv_overscan_horizontal) * 2)).toInt()

        return VideoDetailsViewHolder(binding.root)
    }

    override fun onBindRowViewHolder(viewHolder: ViewHolder, item: Any?) {
        super.onBindRowViewHolder(viewHolder, item)
        val metadata = (item as VideoDetailsOverviewRow).item
        binding.item = metadata
    }

    override fun onSelectLevelChanged(holder: ViewHolder) {
        // super.onSelectLevelChanged(holder)
//        if (selectEffectEnabled) {
//            val vh = holder as VideoDetailsViewHolder
//            val dimmedColor = vh.colorDimmer.paint.color
//            (vh.container.background.mutate() as ColorDrawable).color = dimmedColor
//        }
    }

    inner class VideoDetailsViewHolder(view: View) : ViewHolder(view) {
        val colorDimmer = ColorOverlayDimmer.createDefault(view.context)
        val container = view.findViewById<ConstraintLayout>(R.id.container)
    }
}