/*
 * ************************************************************************
 *  ColorPickerViewModel.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.television.viewmodel

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlin.math.absoluteValue

@HiltViewModel
class ColorPickerViewModel @Inject constructor() : ViewModel() {

    private val _selectedHueIndex = MutableStateFlow(0)
    val selectedHueIndex: StateFlow<Int> = _selectedHueIndex.asStateFlow()

    private val _selectedVariantIndex = MutableStateFlow(10)
    val selectedVariantIndex: StateFlow<Int> = _selectedVariantIndex.asStateFlow()

    private val _colors = MutableStateFlow<List<Int>>(emptyList())
    val colors: StateFlow<List<Int>> = _colors.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _initialColor = MutableStateFlow(Color.BLACK)
    val initialColor: StateFlow<Int> = _initialColor.asStateFlow()

    fun initState(title: String, previousColor: Int) {
        _title.value = title
        _initialColor.value = previousColor
        val colorsAndSelection = generateColorsAndSelection(previousColor)
        _colors.value = colorsAndSelection.second
        _selectedHueIndex.value = colorsAndSelection.first
        _selectedVariantIndex.value = findClosestVariant(_colors.value, _selectedHueIndex.value, previousColor)
    }

    fun selectHue(index: Int) {
        _selectedHueIndex.value = index
        _selectedVariantIndex.value = 9
    }

    fun selectVariant(index: Int) {
        _selectedVariantIndex.value = index
    }

    fun getSelectedColor(): Int {
        if (_colors.value.isEmpty()) return _initialColor.value
        return getVariantColor(_colors.value[_selectedHueIndex.value], _selectedVariantIndex.value)
    }

    /**
     * Finds the closest color variant index in the [colors]
     *
     * @param colors the colors list
     * @param closestColorIndex the closest colors index in the list depending only on the hue
     * @param previousColor the previous color used
     * @return the index of the closest color variant depending on the saturation and value
     */
    private fun findClosestVariant(colors: List<Int>, closestColorIndex: Int, @ColorInt previousColor: Int): Int {
        val distances = HashMap<Int, Pair<Float, Float>>()

        for (i in 0..19) {
            val variant = getVariantColor(colors[closestColorIndex], i)
            val satDistance = colorHsvDistance(previousColor, variant, 1)
            val valDistance = colorHsvDistance(previousColor, variant, 2)
            distances[i] = Pair(satDistance, valDistance)
        }

        var closestVariantIndex = 10
        var minSVDistance = 2F
        distances.forEach {
            val combinedDistance = it.value.first + it.value.second
            if (combinedDistance < minSVDistance) {
                closestVariantIndex = it.key
                minSVDistance = combinedDistance
            }
        }
        return closestVariantIndex
    }

    /**
     * Generate the colors list by making the hue vary
     * and also determine the closest color from the previous selected on
     *
     * @param previousColor the previous color used
     * @return a pair with first being the index of the closest color from [previousColor] and the full color list
     */
    private fun generateColorsAndSelection(@ColorInt previousColor: Int): Pair<Int, List<Int>> {
        var minHueDistance = colorHsvDistance(previousColor, Color.GRAY)
        val colors = ArrayList<Int>(100)
        colors.add(Color.GRAY)
        var closestColorIndex = colors.size - 1
        var hue = 1
        while (hue < 100) {
            val color = Color.HSVToColor(floatArrayOf(3.6F * hue, 1F, 1F))
            colors.add(color)
            val colorHueDistance = colorHsvDistance(previousColor, color)
            if (colorHueDistance < minHueDistance) {
                minHueDistance = colorHueDistance
                closestColorIndex = colors.size - 1
            }
            hue++
        }
        return Pair(closestColorIndex, colors)
    }

    /**
     * Calculate a color distance between two colors on one HSV component
     *
     * @param color1 the first color
     * @param color2 th second color
     * @param hsvIndex the HSV index to use (0 is hue, 1 is saturation, 2 is value)
     * @return
     */
    private fun colorHsvDistance(@ColorInt color1: Int, @ColorInt color2: Int, hsvIndex: Int): Float {
        if (hsvIndex !in 0..2) throw IllegalStateException("hsvIndex must be between 0 and 2")
        val hsv1 = FloatArray(3)
        val hsv2 = FloatArray(3)
        Color.colorToHSV(color1, hsv1)
        Color.colorToHSV(color2, hsv2)
        return (hsv1[hsvIndex] - hsv2[hsvIndex]).absoluteValue
    }

    private fun colorHsvDistance(@ColorInt color1: Int, @ColorInt color2: Int): Float {
        val hsv1 = FloatArray(3)
        val hsv2 = FloatArray(3)
        Color.colorToHSV(color1, hsv1)
        Color.colorToHSV(color2, hsv2)
        return (hsv1[0] - hsv2[0]).absoluteValue + (hsv1[1] - hsv2[1]).absoluteValue + (hsv1[2] - hsv2[2]).absoluteValue
    }

    /**
     * Get a variant color from [color]'s hue depending on a position
     * It will make the saturation and value vary depending on the position:
     * [0-9] -> ascending saturation
     * [10-19] -> descending value
     *
     * @param color the color with a pure hue
     * @param position the index of the variant to generate
     * @return a
     */
    fun getVariantColor(color: Int, position: Int): Int {
        if (color == Color.GRAY) {
            val value = 1F - (0.05F * position)
            return Color.HSVToColor(floatArrayOf(0F, 0F, value))
        }
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (position <= 9)
            hsv[1] = 0.1F * position
        else
            hsv[2] = 1F - (0.1F * (position - 9))
        return Color.HSVToColor(hsv)
    }
}
