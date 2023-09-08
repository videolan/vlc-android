/*
 * ************************************************************************
 *  ColorPickerItem.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

package org.videolan.television.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import org.videolan.tools.dp
import org.videolan.vlc.R

/**
 * Custom view showing a color item. The color is drawn on the view canvas and the view i selectable
 */
class ColorPickerItem @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        setWillNotDraw(false)
    }


    /**
     * Change the selection state and manages the addition/removal of the icon
     */
    var currentlySelected:Boolean = false
    set(value) {
        field = value
        if (value) addView(ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_check))
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.gravity = Gravity.CENTER
            layoutParams = lp
            setPadding(2.dp, 2.dp,2.dp, 2.dp)
            background = ContextCompat.getDrawable(context, R.drawable.round_black_transparent_50)
        })
        else removeAllViews()
    }

    private val paint by lazy {
        Paint().apply {
            isAntiAlias = true
        }
    }

    private val outerPaint by lazy {
        Paint().apply {
            isAntiAlias = true
            color = ContextCompat.getColor(context, R.color.grey500)
        }
    }

    /**
     * Changes the color and update the view
     */
    var color: Int = 0
        set(value) {
            field = value
            paint.color = value
            requestLayout()
        }


    /**
     * Draws the color and the outer circle in the [canvas]
     *
     * @param canvas the view's [Canvas]
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(width.toFloat() / 2, height.toFloat() / 2, (width.toFloat() / 2) - 4.dp, outerPaint)
        canvas.drawCircle(width.toFloat() / 2, height.toFloat() / 2, (width.toFloat() / 2) - 5.dp, paint)
    }


}
