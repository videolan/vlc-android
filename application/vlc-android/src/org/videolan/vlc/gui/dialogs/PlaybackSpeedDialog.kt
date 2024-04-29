/**
 * **************************************************************************
 * PickTimeFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.onEach
import org.videolan.tools.formatRateString
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogExtDeviceBinding
import org.videolan.vlc.databinding.DialogPlaybackSpeedBinding
import org.videolan.vlc.gui.helpers.OnRepeatListenerKey
import org.videolan.vlc.gui.helpers.OnRepeatListenerTouch
import org.videolan.vlc.util.isSchemeStreaming
import org.videolan.vlc.util.launchWhenStarted
import kotlin.math.*

class PlaybackSpeedDialog : VLCBottomSheetDialogFragment() {

    private lateinit var binding: DialogPlaybackSpeedBinding

    private var playbackService: PlaybackService? = null
    private var textColor: Int = 0

    private val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (playbackService == null || playbackService!!.currentMediaWrapper == null)
                return
            if (fromUser) {
                val rate = (4.0).pow(progress.toDouble() / 100.0 - 1).toFloat()
                playbackService!!.setRate(rate, true)
                updateInterface()
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }

    private val resetListener = View.OnClickListener {
        if (playbackService == null || playbackService!!.rate.toDouble() == 1.0 || playbackService!!.currentMediaWrapper == null)
            return@OnClickListener

        playbackService!!.setRate(1f, true)
        setRateProgress()
    }

    private val speedUpListener = View.OnClickListener {
        if (playbackService == null)
            return@OnClickListener
        changeSpeed(0.05f)
        setRateProgress()
    }

    private val speedDownListener = View.OnClickListener {
        if (playbackService == null)
            return@OnClickListener
        changeSpeed(-0.05f)
        setRateProgress()
    }

    override fun initialFocusedView(): View {
        return binding.playbackSpeedSeek
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = DialogPlaybackSpeedBinding.inflate(inflater, container, false)

        binding.playbackSpeedSeek.setOnSeekBarChangeListener(seekBarListener)
        binding.playbackSpeedPlus.setOnClickListener(speedUpListener)
        binding.playbackSpeedMinus.setOnClickListener(speedDownListener)
        binding.playbackSpeedValue.setOnClickListener(resetListener)
        binding.playbackSpeedMinus.setOnTouchListener(OnRepeatListenerTouch(speedDownListener, lifecycle))
        binding.playbackSpeedPlus.setOnTouchListener(OnRepeatListenerTouch(speedUpListener, lifecycle))
        binding.playbackSpeedMinus.setOnKeyListener(OnRepeatListenerKey(speedDownListener, lifecycle))
        binding.playbackSpeedPlus.setOnKeyListener(OnRepeatListenerKey(speedUpListener, lifecycle))

        textColor = binding.playbackSpeedValue.currentTextColor


        dialog?.setCancelable(true)
        dialog?.setCanceledOnTouchOutside(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        PlaybackService.serviceFlow.onEach { onServiceChanged(it) }.launchWhenStarted(lifecycleScope)
    }

    private fun setRateProgress() {
        var speed = playbackService!!.rate.toDouble()
        speed = 100 * (1 + ln(speed) / ln(4.0))
        binding.playbackSpeedSeek.progress = speed.toInt()
        updateInterface()
    }

    private fun changeSpeed(delta: Float) {
        var initialRate = (playbackService!!.rate * 100.0).roundToInt() / 100.0
        initialRate = if (delta > 0)
            floor((initialRate + 0.005) / 0.05) * 0.05
        else
            ceil((initialRate - 0.005) / 0.05) * 0.05
        val rate = ((initialRate + delta) * 100f).roundToInt() / 100f
        if (rate < 0.25f || rate > 4f || playbackService!!.currentMediaWrapper == null)
            return
        binding.playbackSpeedSeek.announceForAccessibility(rate.toString())
        binding.playbackSpeedSeek.contentDescription = rate.toString()
        playbackService!!.setRate(rate, true)
    }

    private fun updateInterface() {
        val rate = playbackService!!.rate
        binding.playbackSpeedValue.text = rate.formatRateString()
        if (rate != 1.0f) {
            binding.playbackSpeedValue.setTextColor(ContextCompat.getColor(requireActivity(), R.color.orange500))
        } else {
            binding.playbackSpeedValue.setTextColor(textColor)
        }
        binding.streamWarning.visibility = if (isSchemeStreaming(playbackService?.currentMediaLocation) && rate > 1) View.VISIBLE else View.INVISIBLE

    }

    private fun onServiceChanged(service: PlaybackService?) {
        if (service != null) {
            playbackService = service
            setRateProgress()
        } else
            playbackService = null
    }

    override fun getDefaultState(): Int {
        return com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    override fun needToManageOrientation(): Boolean {
        return true
    }

    companion object {

        const val TAG = "VLC/PlaybackSpeedDialog"

        fun newInstance(): PlaybackSpeedDialog {
            return PlaybackSpeedDialog()
        }
    }
}
