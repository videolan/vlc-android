/*
 * ************************************************************************
 *  WidgetUtils.kt
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

package org.videolan.vlc.widget.utils

import android.content.Context
import android.graphics.*
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.ColorUtils.HSLToColor
import androidx.core.graphics.ColorUtils.colorToHSL
import androidx.palette.graphics.Palette
import org.videolan.tools.dp
import org.videolan.vlc.R
import org.videolan.vlc.mediadb.models.Widget
import kotlin.random.Random

/**
 *
 * Get the foreground color of the widget depending on its theme
 * @param context the context to use
 * @param secondary generate a secondary color
 * @param palette the palette to be used if needed
 * @return a color
 */
fun Widget.getForegroundColor(context: Context, secondary: Boolean = false, palette: Palette?): Int {
    val untreatedColor = when {
        theme == 0 -> ContextCompat.getColor(context, if (lightTheme) if (secondary) android.R.color.system_accent2_600 else android.R.color.system_accent2_500 else if (secondary) android.R.color.system_accent1_200 else android.R.color.system_accent1_400)
        theme == 1 -> getPaletteColor(context, palette, true, secondary, lightTheme)
        else -> if (secondary) foregroundColor.lightenOrDarkenColor(0.3F) else foregroundColor
    }
    return untreatedColor
}

fun Widget.getPaletteColor(context: Context, palette: Palette?, foreground: Boolean, secondary: Boolean, lightTheme: Boolean): Int {
    //todo revert
//    return  getRandomColor()

    val swatch =
            if (foreground)
                if (lightTheme) palette?.lightVibrantSwatch else palette?.darkVibrantSwatch
            else
                if (lightTheme) palette?.darkMutedSwatch else palette?.lightMutedSwatch
    val fallback = if (foreground)
        if (lightTheme) R.color.white_transparent_50 else R.color.black_transparent_50
    else
        if (lightTheme) R.color.black_transparent_50 else R.color.white_transparent_50
    return when {
        foreground -> if (secondary) swatch?.rgb
                ?: ContextCompat.getColor(context, fallback) else swatch?.rgb?.lightenOrDarkenColor(0.3F)
                ?: ContextCompat.getColor(context, fallback)
        else -> if (secondary) swatch?.rgb
                ?: ContextCompat.getColor(context, fallback) else swatch?.rgb
                ?: ContextCompat.getColor(context, fallback)
    }
}

fun getRandomColor() = Color.rgb(Random.nextInt(255), Random.nextInt(255), Random.nextInt(255))

/**
 * Get the background color of the widget depending on its theme
 * @param context the context to use
 * @param secondary generate a secondary color
 * @param palette the palette to be used if needed
 * @return a color
 */
fun Widget.getBackgroundColor(context: Context, secondary: Boolean = false, palette: Palette?): Int {
    val untreatedColor = when {
        theme == 0 -> ContextCompat.getColor(context, if (lightTheme) if (secondary) android.R.color.system_neutral1_200 else android.R.color.system_neutral2_10 else if (secondary) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_800)
        theme == 1 -> getPaletteColor(context, palette, false, secondary, lightTheme)
        else -> if (secondary) backgroundColor.lightenOrDarkenColor(0.3F) else backgroundColor
    }

    return if (opacity.coerceAtLeast(0).coerceAtMost(100) != 100) ColorUtils.setAlphaComponent(untreatedColor, (opacity * 2.55F).toInt()) else untreatedColor
}

/**
 * Get a color variant from a given color. It lightens or darkens it depending on its shade
 *
 * @param value the difference in light to apply
 * @return a new color
 */
fun Int.lightenOrDarkenColor(value: Float): Int {
    val hsl = FloatArray(3)
    colorToHSL(this, hsl)
    if (hsl[2] < 0.5F) hsl[2] += value else hsl[2] -= value
    hsl[2] = 0f.coerceAtLeast(hsl[2].coerceAtMost(1f))
    return HSLToColor(hsl)
}

/**
 * Generates a circular progress bar [Bitmap]
 *
 * @param context the context to use
 * @param size the size of the generated [Bitmap]
 * @param progress the progress to show in the progress bar
 * @return a progress bar [Bitmap]
 */
fun WidgetCacheEntry.generateCircularProgressbar(context: Context, size: Float, progress: Float, stroke: Float = 6.dp.toFloat()): Bitmap {
    val paint = Paint()
    val strokeHalfWidth = stroke / 2
    paint.isAntiAlias = true
    paint.strokeWidth = strokeHalfWidth * 2
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    paint.color = widget.getForegroundColor(context, true, palette)
    val bitmapResult = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmapResult)
    canvas.drawCircle(size / 2, size / 2, (size / 2) - strokeHalfWidth, paint)
    paint.color = widget.getForegroundColor(context, false, palette)
    canvas.drawArc(RectF(strokeHalfWidth, strokeHalfWidth, size - strokeHalfWidth, size - strokeHalfWidth), -90F, 360F * progress, false, paint)
    return bitmapResult

}

/**
 * Generates a progress bar [Bitmap]
 *
 * @param context the context to use
 * @param size the size of the generated [Bitmap]
 * @param progress the progress to show in the progress bar
 * @return a progress bar [Bitmap]
 */
fun WidgetCacheEntry.generateProgressbar(context: Context, size: Float, progress: Float): Bitmap {
    val paint = Paint()
    val strokeHalfWidth = 3.dp.toFloat()
    paint.isAntiAlias = true
    paint.strokeWidth = strokeHalfWidth * 2
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    paint.color = widget.getForegroundColor(context, true, palette)
    val bitmapResult = Bitmap.createBitmap(size.toInt(), 3.dp, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmapResult)
    canvas.drawLine(0F, 0F, size, 0F, paint)
    paint.color = widget.getForegroundColor(context, false, palette)
    canvas.drawLine(0F, 0F, size * progress, 0F, paint)
    return bitmapResult

}

/**
 * Generates a pill progress bar [Bitmap]
 *
 * @param context the context to use
 * @param progress the progress to show in the progress bar
 * @return a progress bar [Bitmap]
 */
fun WidgetCacheEntry.generatePillProgressbar(context: Context, progress: Float): Bitmap? {
    if (widget.width == 0) return null
    val paint = Paint()
    val strokeHalfWidth = 2.dp.toFloat()
    paint.isAntiAlias = true
    paint.strokeWidth = strokeHalfWidth * 2
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    paint.color = widget.getForegroundColor(context, true, palette)
    val realWidth = widget.width.dp.toFloat()

    val progressHeight = 72.dp.toFloat()
    val halfHeight = progressHeight / 2
    val bitmapResult = Bitmap.createBitmap(realWidth.toInt(), progressHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmapResult)


    /**
     * Here are the background segment description
     *      1
     * 4 ╭────╮ 3
     *   ╰────╯
     *      2
     * 1: top bar
     * 2: bottom bar
     * 3: right semi circle
     * 4: left semi circle
     */

    //draw background
    canvas.drawLine(halfHeight + strokeHalfWidth, strokeHalfWidth, realWidth - halfHeight - strokeHalfWidth, strokeHalfWidth, paint)
    canvas.drawLine(halfHeight + strokeHalfWidth, progressHeight - strokeHalfWidth, realWidth - halfHeight - strokeHalfWidth, progressHeight - strokeHalfWidth, paint)

    canvas.drawArc(RectF(strokeHalfWidth, strokeHalfWidth, progressHeight - strokeHalfWidth, progressHeight - strokeHalfWidth), -90F, -180F, false, paint)
    canvas.drawArc(RectF(realWidth - progressHeight, strokeHalfWidth, realWidth - strokeHalfWidth, progressHeight - strokeHalfWidth), -90F, 180F, false, paint)

    paint.color = widget.getForegroundColor(context, false, palette)

    //draw progress
    val circleLength = progressHeight * Math.PI
    val pathLength = ((realWidth - progressHeight) * 2) + circleLength
    var remainingProgressLength = pathLength * progress

    /**
     * Here are the segment description
     *    5  1
     * 4 ╭────╮ 2
     *   ╰────╯
     *      3
     * 1: first half of the top bar
     * 2: right semi circle
     * 3: full bottom bar
     * 4: left semi circle
     * 5: other half of the top bar
     */

    //first segment
    val firstSegmentLength = ((realWidth / 2) - halfHeight).coerceAtMost(remainingProgressLength.toFloat())
    canvas.drawLine(realWidth / 2, strokeHalfWidth, (realWidth / 2) + firstSegmentLength, strokeHalfWidth, paint)
    remainingProgressLength -= firstSegmentLength

    //second segment (right semi circle)
    if (remainingProgressLength > 1) {
        val secondSegmentLength = (circleLength / 2).coerceAtMost(remainingProgressLength)
        val secondSegmentAngle = 180F * (secondSegmentLength / (circleLength / 2))
        canvas.drawArc(RectF(realWidth - progressHeight, strokeHalfWidth, realWidth - strokeHalfWidth, progressHeight - strokeHalfWidth), -90F, secondSegmentAngle.toFloat(), false, paint)
        remainingProgressLength -= secondSegmentLength
    }

    //third segment : (bottom bar)
    if (remainingProgressLength > 1) {
        val thirdSegmentLength = (realWidth - progressHeight).coerceAtMost(remainingProgressLength.toFloat())
        canvas.drawLine(realWidth - halfHeight - strokeHalfWidth, progressHeight - strokeHalfWidth, realWidth - halfHeight - strokeHalfWidth - thirdSegmentLength, progressHeight - strokeHalfWidth, paint)
        remainingProgressLength -= thirdSegmentLength
    }

    //fourth segment (left semi circle)
    if (remainingProgressLength > 1) {
        val fourthSegmentLength = (circleLength / 2).coerceAtMost(remainingProgressLength)
        val fourthSegmentAngle = 180F * (fourthSegmentLength / (circleLength / 2))
        canvas.drawArc(RectF(strokeHalfWidth, strokeHalfWidth, progressHeight - strokeHalfWidth, progressHeight - strokeHalfWidth), 90F, fourthSegmentAngle.toFloat(), false, paint)
        remainingProgressLength -= fourthSegmentLength
    }

    //fifth (and last) segment
    if (remainingProgressLength > 1) {
        val fifthSegmentLength = ((realWidth / 2) - halfHeight).coerceAtMost(remainingProgressLength.toFloat())
        canvas.drawLine(halfHeight + strokeHalfWidth, strokeHalfWidth, (halfHeight + strokeHalfWidth) + fifthSegmentLength, strokeHalfWidth, paint)

        remainingProgressLength -= fifthSegmentLength
    }

    return bitmapResult
}

enum class WidgetType(id: Int, @LayoutRes val layout: Int) {
    PILL(0, R.layout.widget_pill), MINI(1, R.layout.widget_mini), MICRO(2, R.layout.widget_micro), MACRO(3, R.layout.widget_macro)
}