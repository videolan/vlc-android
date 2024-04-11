/*
 * ************************************************************************
 *  ColorPickerActivity.kt
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

package org.videolan.television.ui

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.videolan.resources.UPDATE_PAYLOAD
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.television.R
import org.videolan.television.databinding.ActivityColorPickerBinding
import org.videolan.television.databinding.ColorPickerItemBinding
import org.videolan.tools.dp
import org.videolan.vlc.gui.DiffUtilAdapter
import kotlin.math.abs

const val COLOR_PICKER_SELECTED_COLOR = "color_picker_selected_color"
const val COLOR_PICKER_TITLE = "color_picker_title"

class ColorPickerActivity : AppCompatActivity() {
    internal lateinit var binding: ActivityColorPickerBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_color_picker)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_color_picker)
        applyOverscanMargin(this)

        binding.colorPickerTitle.text = intent.extras?.getString(COLOR_PICKER_TITLE)
                ?: getString(R.string.subtitles_color)
        val previousColor = intent.extras?.getInt(COLOR_PICKER_SELECTED_COLOR) ?: Color.BLACK
        binding.oldColor.color = previousColor
        binding.newColor.color = previousColor


        val colorsAndSelection = generateColorsAndSelection(previousColor)
        val closestColorIndex = colorsAndSelection.first
        val colors = colorsAndSelection.second


        //find closest color variant
        val closestVariantIndex = findClosestVariant(colors, closestColorIndex, previousColor)

        val grid = findViewById<RecyclerView>(R.id.color_grid)
        grid.layoutManager = GridLayoutManager(this, 20)
        grid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            val paint by lazy {
                Paint().apply {
                    isAntiAlias = true
                    color = ContextCompat.getColor(this@ColorPickerActivity, R.color.grey800)
                }
            }

            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                super.getItemOffsets(outRect, view, parent, state)
                val position: Int = parent.getChildAdapterPosition(view)
                outRect.left = 4.dp
                outRect.right = 4.dp
                // add a bigger margin before the color variants
                outRect.top = if (position > colors.size - 1) 32.dp else 4.dp
                outRect.bottom = 4.dp
            }

            override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                super.onDrawOver(c, parent, state)

                //add a separator before the color variants
                val firstChild = parent.getChildAt(colors.size)
                val lastChild = parent.getChildAt(parent.adapter!!.itemCount - 1)
                c.drawLine(firstChild.left.toFloat(), firstChild.top.toFloat() - 16.dp, lastChild.right.toFloat(), firstChild.top.toFloat() - 16.dp, paint)

            }
        })


        val colorAdapter = ColorAdapter(colors, closestColorIndex, closestVariantIndex) {
            binding.newColor.color = it
        }
        grid.adapter = colorAdapter

        binding.colorPickerButtonOk.setOnClickListener {
            setResult(RESULT_OK, Intent(Intent.ACTION_PICK).apply { putExtra(COLOR_PICKER_SELECTED_COLOR, colorAdapter.getSelectedColor()) })
            finish()
        }

        binding.colorPickerButtonCancel.setOnClickListener { finish() }


    }

    /**
     * Finds the closest color variant index in the [colors]
     *
     * @param colors the colors list
     * @param closestColorIndex the closest colors index in the list depending only on the hue
     * @param previousColor the previous color used
     * @return the index of the closest color variant depending on the saturation and value
     */
    private fun findClosestVariant(colors: ArrayList<Int>, closestColorIndex: Int, @ColorInt previousColor: Int): Int {
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
    private fun generateColorsAndSelection(@ColorInt previousColor: Int): Pair<Int, ArrayList<Int>> {
        var minHueDistance = colorHsvDistance(previousColor, Color.GRAY)
        var closestColorIndex = 0
        val colors = ArrayList<Int>(100)
        colors.add(Color.GRAY)
        closestColorIndex = colors.size - 1
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
        return abs(hsv1[hsvIndex] - hsv2[hsvIndex])
    }

    private fun colorHsvDistance(@ColorInt color1: Int, @ColorInt color2: Int): Float {

        val hsv1 = FloatArray(3)
        val hsv2 = FloatArray(3)
        Color.colorToHSV(color1, hsv1)
        Color.colorToHSV(color2, hsv2)
        return abs(hsv1[0] - hsv2[0]) + abs(hsv1[1] - hsv2[1]) + abs(hsv1[2] - hsv2[2])
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
    @ColorInt
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


    /**
     * Color adapter used to display the color list
     *
     * @property colors the list of colors
     * @property selectedIndex the main color selected index (between 0 and 99)
     * @property selectedVariantIndex the selected color variant index (between 0 and 19)
     */
    inner class ColorAdapter(private val colors: List<Int>, private var selectedIndex: Int, private var selectedVariantIndex: Int, private val colorSelectionListener: (Int) -> Unit) : DiffUtilAdapter<Int, ColorPickerViewHolder>() {


        // tracks the focus restoration upon item changes
        private var waitingForFocusRestore = false
        private var currentFocusPosition: Int = -1

        override fun onBindViewHolder(holder: ColorPickerViewHolder, position: Int) {
            val adapterPosition = holder.absoluteAdapterPosition
            val color = if (adapterPosition in colors.indices) colors[adapterPosition] else getVariantColor(colors[selectedIndex], adapterPosition - colors.size)
            holder.binding.colorPicker.color = color

            val selected = if (adapterPosition in colors.indices) selectedIndex == adapterPosition else adapterPosition - colors.size == selectedVariantIndex
            holder.binding.colorPicker.currentlySelected = selected
            holder.binding.colorPicker.setOnFocusChangeListener { _, hasFocus ->
                if (!waitingForFocusRestore && hasFocus) currentFocusPosition = adapterPosition
            }
            if (adapterPosition == currentFocusPosition) {
                holder.binding.colorPicker.requestFocus()
                waitingForFocusRestore = false
            }
        }

        /**
         * Get the currently selected color
         *
         * @return the currently selected color
         */
        @ColorInt
        fun getSelectedColor() = getVariantColor(colors[selectedIndex], selectedVariantIndex)

        override fun onBindViewHolder(holder: ColorPickerViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) onBindViewHolder(holder, position)
            else for (payload in payloads) {
                if (payload == UPDATE_PAYLOAD) {
                    holder.binding.colorPicker.currentlySelected = selectedIndex == position || position - colors.size == selectedVariantIndex
                    if (position == currentFocusPosition) holder.binding.colorPicker.requestFocus()
                }

            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorPickerViewHolder {
            return ColorPickerViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.color_picker_item, parent, false) as ColorPickerItemBinding) { position, view ->
                waitingForFocusRestore = true
                view.clearFocus()
                if (position in colors.indices) {
                    val oldSelection = selectedIndex
                    selectedIndex = position
                    selectedVariantIndex = 9
                    notifyItemChanged(position)
                    notifyItemChanged(oldSelection)
                    notifyItemRangeChanged(colors.size, 20)
                } else {
                    val oldSelection = selectedVariantIndex
                    selectedVariantIndex = position - colors.size
                    notifyItemChanged(position)
                    notifyItemChanged(colors.size + oldSelection)
                }
                colorSelectionListener.invoke(getSelectedColor())
            }

        }

        override fun getItemCount(): Int {
            return colors.size + 20
        }

        override fun createCB(): DiffCallback<Int> = object : DiffCallback<Int>() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) = false

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return false
            }

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int) = arrayListOf(UPDATE_PAYLOAD)
        }
    }

    inner class ColorPickerViewHolder(val binding: ColorPickerItemBinding, private val listener: (Int, View) -> Unit) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.colorPicker.setOnClickListener {
                listener.invoke(layoutPosition, binding.colorPicker)
            }
        }

    }
}

