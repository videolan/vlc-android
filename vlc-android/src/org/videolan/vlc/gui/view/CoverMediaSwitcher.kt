/*****************************************************************************
 * CoverMediaSwitcher.java
 *
 * Copyright © 2011-2014 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi

import org.videolan.vlc.R

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class CoverMediaSwitcher(context: Context, attrs: AttributeSet) : AudioMediaSwitcher(context, attrs) {

    override fun addMediaView(inflater: LayoutInflater, title: String?, artist: String?, cover: Bitmap?) {
        var cover = cover

        if (cover == null) cover = BitmapFactory.decodeResource(resources, R.drawable.icon)

        val imageView = ImageView(context)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setImageBitmap(cover)
        addView(imageView)
    }
}
