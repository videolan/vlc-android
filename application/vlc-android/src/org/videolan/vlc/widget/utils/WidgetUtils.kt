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
import com.google.android.material.color.DynamicColors
import org.videolan.tools.dp
import org.videolan.vlc.R
import org.videolan.vlc.mediadb.models.Widget


/**
 *
 * Get the foreground color of the widget depending on its theme
 * @param context the context to use
 * @param palette the palette to be used if needed
 * @return a color
 */
fun Widget.getForegroundColor(context: Context, palette: Palette?): Int {
    val untreatedColor = when {
        theme == 0 && DynamicColors.isDynamicColorAvailable() -> ContextCompat.getColor(context, if (lightTheme) android.R.color.system_accent1_400 else android.R.color.system_accent1_200)
        theme == 2 -> foregroundColor
        else -> if (palette == null) foregroundColor else if (lightTheme) palette?.darkVibrantSwatch?.rgb
                ?: Color.BLACK else palette?.lightVibrantSwatch?.rgb ?: Color.WHITE
    }
    return untreatedColor
}

/**
 * Get the background color of the widget depending on its theme
 * @param context the context to use
 * @param palette the palette to be used if needed
 * @return a color
 */
fun Widget.getBackgroundColor(context: Context, palette: Palette?): Int {
    val untreatedColor = when {
        theme == 0 && DynamicColors.isDynamicColorAvailable() -> ContextCompat.getColor(context, if (lightTheme) android.R.color.system_neutral2_50 else android.R.color.system_neutral2_800)
        theme == 2 -> backgroundColor
        else -> if (palette == null) backgroundColor else if (lightTheme) palette?.lightMutedSwatch?.rgb
                ?: backgroundColor else palette?.darkMutedSwatch?.rgb ?: backgroundColor
    }
    return if (opacity.coerceAtLeast(0).coerceAtMost(100) != 100) ColorUtils.setAlphaComponent(untreatedColor, (opacity * 2.55F).toInt()) else untreatedColor
}

/**
 * Get the background secondary color of the widget depending on its theme. Mostly used for the micro widget 'FAB' color
 * @param context the context to use
 * @param palette the palette to be used if needed
 * @return a color
 */
fun Widget.getBackgroundSecondaryColor(context: Context, palette: Palette?): Int {
    val untreatedColor = when {
        theme == 0 && DynamicColors.isDynamicColorAvailable() -> ContextCompat.getColor(context, if (lightTheme) android.R.color.system_accent1_100 else android.R.color.system_accent1_700)
        theme == 2 -> backgroundColor.lightenOrDarkenColor(0.1F)
        else -> if (lightTheme) palette?.lightMutedSwatch?.rgb ?: ContextCompat.getColor(context, R.color.grey300) else palette?.darkMutedSwatch?.rgb ?: ContextCompat.getColor(context, R.color.grey800)
    }
    return if (opacity.coerceAtLeast(0).coerceAtMost(100) != 100) ColorUtils.setAlphaComponent(untreatedColor, (opacity * 2.55F).toInt()) else untreatedColor
}

/**
 * Get the Artist text color of the widget depending on its theme
 * @param context the context to use
 * @param palette the palette to be used if needed
 * @return a color
 */
fun Widget.getArtistColor(context: Context, palette: Palette?): Int {
    val untreatedColor = when {
        theme == 0 && DynamicColors.isDynamicColorAvailable() -> ContextCompat.getColor(context, if (lightTheme) android.R.color.system_neutral2_400 else android.R.color.system_neutral2_500)
        theme == 2 -> foregroundColor.lightenOrDarkenColor(0.1F)
        else -> getForegroundColor(context, palette).lightenOrDarkenColor(0.1F)
    }
    return untreatedColor
}

fun Widget.getProgressBackgroundColor(context: Context, palette: Palette?): Int {
    val untreatedColor = when {
        theme == 0 && DynamicColors.isDynamicColorAvailable() -> ContextCompat.getColor(context, if (lightTheme) android.R.color.system_neutral2_10 else android.R.color.system_neutral2_700)
        theme == 2 -> backgroundColor.lightenOrDarkenColor(0.15F)
        else -> getBackgroundColor(context, palette).lightenOrDarkenColor(0.15F)
    }

    return if (opacity.coerceAtLeast(0).coerceAtMost(100) != 100) ColorUtils.setAlphaComponent(untreatedColor, (opacity * 2.55F).toInt()) else untreatedColor
}

/**
 * Get the separator view color
 *
 * @param context the context to use
 * @return a color without the transparency added
 */
fun Widget.getSeparatorColor(context: Context) = ContextCompat.getColor(context, if (lightTheme) R.color.black_transparent_10 else R.color.white_transparent_10)


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
 * Detects if the color represented by this Int is light
 *
 * @return true if the color is light, false if it's dark
 */
fun Int.isLight():Boolean {
    val hsl = FloatArray(3)
    colorToHSL(this, hsl)
    return hsl[2] > 0.5F
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
    paint.color = widget.getProgressBackgroundColor(context,  palette)
    val bitmapResult = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmapResult)
    canvas.drawCircle(size / 2, size / 2, (size / 2) - strokeHalfWidth, paint)
    paint.color = widget.getForegroundColor(context,  palette)
    canvas.drawArc(RectF(strokeHalfWidth, strokeHalfWidth, size - strokeHalfWidth, size - strokeHalfWidth), -90F, 360F * progress, false, paint)
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
    paint.color = widget.getProgressBackgroundColor(context,  palette)
    val realWidth = 120.dp.toFloat()

    val progressHeight = 62.dp.toFloat()
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

    paint.color = widget.getForegroundColor(context,  palette)

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

enum class WidgetType(@LayoutRes val layout: Int) {
    PILL(R.layout.widget_pill), MINI(R.layout.widget_mini), MICRO(R.layout.widget_micro), MACRO(R.layout.widget_macro)
}

object WidgetUtils {
    /**
     * Calculate the [WidgetType] depending on the widget's settings and size
     *
     * @param widget the widget to check
     * @returnthe [WidgetType] for this widget
     */
    fun getWidgetType(widget: Widget) = when {

        widget.type == 1 -> WidgetType.PILL
        widget.type == 2 -> WidgetType.MINI
        widget.type == 3 -> WidgetType.MICRO
        widget.type == 4 -> WidgetType.MACRO
        widget.width > 220 && widget.height > 220 -> WidgetType.MACRO
        widget.width > 220 && widget.height > 72 -> WidgetType.MINI
        widget.width > 128 && widget.height > 148 -> WidgetType.MICRO
        else -> WidgetType.PILL
    }

    /**
     * Minimal size for this widget type
     *
     * @param type the widget type to check
     * @return the minimal size
     */
    fun getMinimalWidgetSize(type: WidgetType):Pair<Int, Int> = when(type){
        WidgetType.MACRO -> Pair(220,200)
        WidgetType.MINI -> Pair(220,72)
        WidgetType.MICRO -> Pair(128,148)
        else -> Pair(0,0)
    }

    /**
     * Check if the widget has enough space to display the seek icons
     *
     * @param widget the widget to check the size on
     * @param widgetType the current [WidgetType]
     * @return true if the widget has enough space
     */
    fun hasEnoughSpaceForSeek(widget: Widget, widgetType: WidgetType) = when (widgetType) {
        WidgetType.MINI -> widget.width.dp > widget.height.dp + 48.dp * 5
        WidgetType.MACRO -> widget.width.dp > 48.dp * 5
        else -> false
    }

    /**
     * Check if the widget should show the seek buttons
     *
     * @param widget the widget to check the size on
     * @param widgetType the current [WidgetType]
     * @return true if the widget has enough space and the setting is set to on
     */
    fun shouldShowSeek(widget: Widget, widgetType: WidgetType) = widget.showSeek && hasEnoughSpaceForSeek(widget, widgetType)
}