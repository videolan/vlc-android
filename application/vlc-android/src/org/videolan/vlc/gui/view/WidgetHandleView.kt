/*
 * ************************************************************************
 *  WidgetHandleView.kt
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

package org.videolan.vlc.gui.view

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import org.videolan.tools.dp
import org.videolan.vlc.R


class WidgetHandleView : View {
    private val paint = Paint()

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }


    private fun initialize() {
        val typedValue = TypedValue()
        val theme: Resources.Theme = context.theme
        theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
        paint.color = typedValue.data
        paint.isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        val padding = 8.dp.toFloat()
        val viewWidth = width
        //3.56 is the known ratio of the [R.drawable.vlc_widget_mini] image. 6.dp is two times the stroke size
        val width = ((height - 16.dp) * 3.56) - 6.dp
        val hPadding = ((viewWidth.toFloat() - width) / 2).toFloat()
        val height = height - padding
        paint.strokeWidth = 3.dp.toFloat()
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(hPadding, padding, viewWidth - hPadding, height, 12.dp.toFloat(), 12.dp.toFloat(), paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(hPadding, padding + (height / 2), 6.dp.toFloat(), paint)
        canvas.drawCircle(hPadding + (width.toFloat() / 2), padding, 6.dp.toFloat(), paint)
        canvas.drawCircle(hPadding + width.toFloat(), padding + (height / 2), 6.dp.toFloat(), paint)
        canvas.drawCircle(hPadding + (width.toFloat() / 2), height, 6.dp.toFloat(), paint)
        super.onDraw(canvas)
    }
}