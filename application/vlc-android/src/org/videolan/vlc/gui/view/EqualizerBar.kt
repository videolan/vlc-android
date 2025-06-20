/*****************************************************************************
 * EqualizerBar.java
 *
 * Copyright © 2013 VLC authors and VideoLAN
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

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import com.google.android.material.slider.Slider
import org.videolan.vlc.R
import org.videolan.vlc.interfaces.OnEqualizerBarChangeListener


class EqualizerBar : LinearLayout {

    private lateinit var bandValueTextView: TextView
    private lateinit var verticalSlider: Slider
    private lateinit var bandTextView: TextView
    private var listener: OnEqualizerBarChangeListener? = null

    override fun setNextFocusLeftId(nextFocusLeftId: Int) {
        super.setNextFocusLeftId(nextFocusLeftId)
        verticalSlider.nextFocusLeftId = nextFocusLeftId
    }

    override fun setNextFocusRightId(nextFocusRightId: Int) {
        super.setNextFocusRightId(nextFocusRightId)
        verticalSlider.nextFocusRightId = nextFocusRightId
    }

    private val seekListener = Slider.OnChangeListener { slider, value, fromUser ->
        val newValue = (value - RANGE) / PRECISION.toFloat()
        listener?.onProgressChanged(newValue, fromUser || (slider as VerticalSeekBar).fromUser)
        updateValueText()
    }

    constructor(context: Context, band: Float) : super(context) {
        init(context, band)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, 0f)
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun init(context: Context, band: Float) {
        LayoutInflater.from(context).inflate(R.layout.equalizer_bar, this, true)

        verticalSlider = findViewById(R.id.equalizer_seek)
        //Force LTR to fix VerticalSeekBar background problem with RTL layout
        verticalSlider.layoutDirection = View.LAYOUT_DIRECTION_LTR
        verticalSlider.valueTo = (2 * RANGE).toFloat()
        verticalSlider.value = RANGE.toFloat()
        verticalSlider.addOnChangeListener(seekListener)
        verticalSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                listener?.onStartTrackingTouch()
            }

            override fun onStopTrackingTouch(slider: Slider) {
                listener?.onStopTrackingTouch()
            }
        })
        bandTextView = findViewById(R.id.equalizer_band)
        bandValueTextView = findViewById(R.id.band_value)
        bandTextView.text = if (band < 999.5f)
            (band + 0.5f).toInt().toString() + "Hz"
        else
            (band / 1000.0f + 0.5f).toInt().toString() + "kHz"
        updateValueText()
    }

    private fun updateValueText() {
        val newValue = (verticalSlider.value / 10) - 20
        bandValueTextView.text = if (newValue > 0) "+${newValue.toInt()}dB" else "${newValue.toInt()}dB"
    }

    fun setValue(value: Float) {
        verticalSlider.value = (value * PRECISION + RANGE)
        updateValueText()
    }

    fun getValue() = ((verticalSlider.value - RANGE) / PRECISION.toFloat())

    fun setListener(listener: OnEqualizerBarChangeListener?) {
        this.listener = listener
    }

    fun setProgress(fl: Int) {
        verticalSlider.value = fl.toFloat()
        updateValueText()
    }

    fun getProgress(): Int = verticalSlider.value.toInt()

    fun setSliderId(@IdRes id: Int) {
        verticalSlider.id = id
    }

    fun getSliderId() = verticalSlider.id

    companion object {

        const val PRECISION = 10
        const val RANGE = 20 * PRECISION
    }
}
