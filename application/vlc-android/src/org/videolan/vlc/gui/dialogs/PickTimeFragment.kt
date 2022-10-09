/**
 * **************************************************************************
 * PickTimeFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.TalkbackUtil
import org.videolan.vlc.util.launchWhenStarted

abstract class PickTimeFragment : VLCBottomSheetDialogFragment(), View.OnClickListener, View.OnFocusChangeListener {

    private var mTextColor: Int = 0

    var hours = ""
    var minutes = ""
    var seconds = ""
    private var formatTime = ""
    private var pickedRawTime = ""
    var maxTimeSize = 6
    private lateinit var tvTimeToJump: TextView

    lateinit var playbackService: PlaybackService

    abstract fun getTitle(): Int

    open fun showDeleteCurrent() = false

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun needToManageOrientation(): Boolean {
        return true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_time_picker, container)
        tvTimeToJump = view.findViewById<View>(R.id.tim_pic_timetojump) as TextView
        (view.findViewById<View>(R.id.tim_pic_title) as TextView).setText(getTitle())

        view.findViewById<View>(R.id.tim_pic_1).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_1).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_2).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_2).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_3).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_3).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_4).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_4).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_5).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_5).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_6).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_6).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_7).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_7).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_8).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_8).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_9).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_9).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_0).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_0).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_00).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_00).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_30).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_30).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_delete).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_delete).onFocusChangeListener = this
        view.findViewById<View>(R.id.tim_pic_ok).setOnClickListener(this)
        view.findViewById<View>(R.id.tim_pic_ok).onFocusChangeListener = this
        val deleteCurrent = view.findViewById<View>(R.id.tim_pic_delete_current)
        deleteCurrent.setOnClickListener(this)
        deleteCurrent.visibility = if (showDeleteCurrent()) View.VISIBLE else View.GONE
        deleteCurrent.onFocusChangeListener = this

        mTextColor = tvTimeToJump.currentTextColor

        return view
    }

    override fun initialFocusedView(): View {
        return requireView().findViewById(R.id.tim_pic_1)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        PlaybackService.serviceFlow.filterNotNull().onEach { playbackService = it }.launchWhenStarted(lifecycleScope)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onFocusChange(v: View, hasFocus: Boolean) {
        if (v is TextView) {
            v.setTextColor(if (hasFocus) ContextCompat.getColor(requireActivity(), R.color.orange500) else mTextColor)
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.tim_pic_1 -> updateValue("1")
            R.id.tim_pic_2 -> updateValue("2")
            R.id.tim_pic_3 -> updateValue("3")
            R.id.tim_pic_4 -> updateValue("4")
            R.id.tim_pic_5 -> updateValue("5")
            R.id.tim_pic_6 -> updateValue("6")
            R.id.tim_pic_7 -> updateValue("7")
            R.id.tim_pic_8 -> updateValue("8")
            R.id.tim_pic_9 -> updateValue("9")
            R.id.tim_pic_0 -> updateValue("0")
            R.id.tim_pic_00 -> updateValue("00")
            R.id.tim_pic_30 -> updateValue("30")
            R.id.tim_pic_delete -> deleteLastNumber()
            R.id.tim_pic_ok -> executeAction()
        }
    }

    private fun getLastNumbers(rawTime: String): String {
        if (rawTime.isEmpty())
            return ""
        return if (rawTime.length == 1)
            rawTime
        else
            rawTime.substring(rawTime.length - 2)
    }

    private fun removeLastNumbers(rawTime: String): String {
        return if (rawTime.length <= 1) "" else rawTime.substring(0, rawTime.length - 2)
    }

    private fun deleteLastNumber() {
        if (pickedRawTime !== "") {
            pickedRawTime = pickedRawTime.substring(0, pickedRawTime.length - 1)
            updateValue("")
        }
    }

    private fun updateValue(value: String) {
        if (pickedRawTime.length >= maxTimeSize)
            return
        pickedRawTime += value
        var tempRawTime = pickedRawTime
        formatTime = ""

        if (maxTimeSize > 4) {
            seconds = getLastNumbers(tempRawTime)
            if (seconds !== "")
                formatTime = seconds + "s"
            tempRawTime = removeLastNumbers(tempRawTime)
        } else
            seconds = ""

        minutes = getLastNumbers(tempRawTime)
        if (minutes !== "")
            formatTime = minutes + "m " + formatTime
        tempRawTime = removeLastNumbers(tempRawTime)

        hours = getLastNumbers(tempRawTime)
        if (hours !== "")
            formatTime = hours + "h " + formatTime

        tvTimeToJump.text = formatTime
        tvTimeToJump.announceForAccessibility(TalkbackUtil.millisToString(requireActivity(), getTimeInMillis() ))
    }

    fun getTimeInMillis(): Long {
        val hours = if (hours != "") java.lang.Long.parseLong(hours) * HOURS_IN_MICROS else 0L
        val minutes = if (minutes != "") java.lang.Long.parseLong(minutes) * MINUTES_IN_MICROS else 0L
        val seconds = if (seconds != "") java.lang.Long.parseLong(seconds) * SECONDS_IN_MICROS else 0L
        return (hours + minutes + seconds) / 1000L
    }

    protected abstract fun executeAction()

    companion object {

        const val TAG = "VLC/PickTimeFragment"

        const val MILLIS_IN_MICROS: Long = 1000
        const val SECONDS_IN_MICROS = 1000 * MILLIS_IN_MICROS
        const val MINUTES_IN_MICROS = 60 * SECONDS_IN_MICROS
        const val HOURS_IN_MICROS = 60 * MINUTES_IN_MICROS
    }
}
