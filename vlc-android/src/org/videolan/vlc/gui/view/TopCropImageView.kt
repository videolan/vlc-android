/*
 * ************************************************************************
 *  TopAlignedImageView.java
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

package org.videolan.vlc.gui.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class TopCropImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : AppCompatImageView(context, attrs, defStyle) {

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {

        val width = r - l
        val height = b - t

        val matrix = imageMatrix
        val scaleFactorWidth = width.toFloat() / drawable.intrinsicWidth.toFloat()
        val scaleFactorHeight = height.toFloat() / drawable.intrinsicHeight.toFloat()

        val scaleFactor = if (scaleFactorHeight > scaleFactorWidth) {
            scaleFactorHeight
        } else {
            scaleFactorWidth
        }

        matrix.setScale(scaleFactor, scaleFactor, 0f, 0f)
        imageMatrix = matrix

        return super.setFrame(l, t, r, b)
    }
}