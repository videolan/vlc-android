/*
 * ************************************************************************
 *  PlayerProgress.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.withClip
import org.videolan.tools.dp
import org.videolan.vlc.R

class PlayerProgress : View {
    var isDouble: Boolean = false
    private var value: Int = 50
    private val progressPercent: Float
        get() = if (isDouble) value.toFloat() / 200 else value.toFloat() / 100

    private val progressColor = ContextCompat.getColor(context, R.color.white)
    private val boostColor = ContextCompat.getColor(context, R.color.orange700)
    private val shadowColor = ContextCompat.getColor(context, R.color.blacktransparent)
    private val backgroundColor = ContextCompat.getColor(context, R.color.white_transparent_50)

    private val paintProgress = Paint()
    private val paintBackground = Paint()
    private val rectProgress = RectF(0F, 0F, 0F, 0F)
    var path = Path()
    private val progressWidth = 8.dp
    private val yOffset = 4.dp
    private val clip = Region()
    private val firstClippingRegion = Region()
    private val secondClippingRegion = Region()
    private val clippingPath = Path()

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
        paintBackground.color = backgroundColor
        paintBackground.isAntiAlias = true
        paintProgress.isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        val left = (width.toFloat() - progressWidth.toFloat()) / 2
        val right = width - left
        val top = yOffset.toFloat()
        val bottom = height - yOffset.toFloat()
        val radius = (right - left) / 2

        //draw background
        roundedRectanglePath(left, top, right, bottom, radius, radius)
        paintBackground.setShadowLayer(6F,0F,0F, shadowColor)
        canvas.drawPath(path, paintBackground)
        paintBackground.clearShadowLayer()
        paintBackground.color = 0x00000000
        canvas.drawPath(path, paintBackground)
        paintBackground.color = backgroundColor
        canvas.drawPath(path, paintBackground)

        //draw progress
        val clipTop = yOffset + (bottom - top) * (1 - progressPercent)
        rectProgress.set(left, clipTop, right, bottom)

        canvas.withClip(rectProgress) {
            clipRect(rectProgress)
        }

        clip.set(left.toInt(), clipTop.toInt(), right.toInt(), bottom.toInt())

        firstClippingRegion.setPath(path, clip)
        clippingPath.moveTo(rectProgress.left, rectProgress.top)
        clippingPath.lineTo(rectProgress.right, rectProgress.top)
        clippingPath.lineTo(rectProgress.right, rectProgress.bottom)
        clippingPath.lineTo(rectProgress.left, rectProgress.bottom)
        clippingPath.close()


        secondClippingRegion.setPath(clippingPath, clip)
        firstClippingRegion.op(secondClippingRegion, Region.Op.INTERSECT)

        paintProgress.color = progressColor
        canvas.drawPath(firstClippingRegion.boundaryPath, paintProgress)

        //draw boost
        if (isDouble && progressPercent > 0.5) {
            val clipBottom = yOffset + (bottom - top) * (0.5F)
            rectProgress.set(left, clipTop, right, clipBottom)

            clip.set(left.toInt(), clipTop.toInt(), right.toInt(), clipBottom.toInt())

            firstClippingRegion.setPath(path, clip)
            clippingPath.moveTo(rectProgress.left, rectProgress.top)
            clippingPath.lineTo(rectProgress.right, rectProgress.top)
            clippingPath.lineTo(rectProgress.right, rectProgress.bottom)
            clippingPath.lineTo(rectProgress.left, rectProgress.bottom)
            clippingPath.close()


            secondClippingRegion.setPath(clippingPath, clip)
            firstClippingRegion.op(secondClippingRegion, Region.Op.INTERSECT)

            paintProgress.color = boostColor
            canvas.drawPath(firstClippingRegion.boundaryPath, paintProgress)
        }

        super.onDraw(canvas)
    }

    fun setValue(value: Int) {
        this.value = value
        invalidate()
    }

    private fun roundedRectanglePath(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float) {
        val width = right - left
        val height = bottom - top
        val widthWithoutCorners = width - 2 * rx
        val heightWithoutCorners = height - 2 * ry
        path.moveTo(right, top + ry)
        path.rQuadTo(0f, -ry, -rx, -ry)
        path.rLineTo(-widthWithoutCorners, 0f)
        path.rQuadTo(-rx, 0f, -rx, ry)
        path.rLineTo(0f, heightWithoutCorners)
        path.rQuadTo(0f, ry, rx, ry)
        path.rLineTo(widthWithoutCorners, 0f)
        path.rQuadTo(rx, 0f, rx, -ry)
        path.rLineTo(0f, -heightWithoutCorners)
        path.close()
    }
}