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
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.onEach
import org.videolan.tools.formatRateString
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogPlaybackSpeedBinding
import org.videolan.vlc.gui.helpers.OnRepeatListenerKey
import org.videolan.vlc.gui.helpers.OnRepeatListenerTouch
import org.videolan.vlc.util.isSchemeStreaming
import org.videolan.vlc.util.launchWhenStarted
import kotlin.math.ln
import kotlin.math.pow


class PlaybackSpeedDialog : VLCBottomSheetDialogFragment() {

    private lateinit var binding: DialogPlaybackSpeedBinding

    private var playbackService: PlaybackService? = null
    private var textColor: Int = 0

    private val orangeColor:Int
        get() {
                val typedValue = TypedValue()
                val theme = requireActivity().theme
                theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
                return typedValue.data
        }

    private val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (playbackService == null || playbackService!!.currentMediaWrapper == null)
                return
            if (fromUser) {
                val rate = (8.0).pow(progress.toDouble() / 100.0 - 1).toFloat()
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
        changeSpeedTo(playbackService!!.rate + 0.01f)
    }

    private val speedDownListener = View.OnClickListener {
        if (playbackService == null)
            return@OnClickListener
        changeSpeedTo(playbackService!!.rate - 0.01f)
    }

    override fun initialFocusedView(): View {
        return binding.playbackSpeedValue
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        binding = DialogPlaybackSpeedBinding.inflate(inflater, container, false)

        binding.playbackSpeedSeek.setOnSeekBarChangeListener(seekBarListener)
        binding.playbackSpeedValue.setOnClickListener(resetListener)
        binding.buttonSpeedMinus.setOnTouchListener(OnRepeatListenerTouch(clickListener = speedDownListener, listenerLifecycle = lifecycle))
        binding.buttonSpeedPlus.setOnTouchListener(OnRepeatListenerTouch(clickListener = speedUpListener, listenerLifecycle = lifecycle))
        binding.buttonSpeedMinus.setOnKeyListener(OnRepeatListenerKey(clickListener = speedDownListener, listenerLifecycle = lifecycle))
        binding.buttonSpeedPlus.setOnKeyListener(OnRepeatListenerKey(clickListener = speedUpListener, listenerLifecycle = lifecycle))
        binding.buttonSpeed1.setOnClickListener {
            changeSpeedTo(1F)
        }
        binding.buttonSpeed08.setOnClickListener {
            changeSpeedTo(0.8F)
        }
        binding.buttonSpeed125.setOnClickListener {
            changeSpeedTo(1.25F)
        }
        binding.buttonSpeed15.setOnClickListener {
            changeSpeedTo(1.5F)
        }
        binding.buttonSpeed2.setOnClickListener {
            changeSpeedTo(2F)
        }
        binding.buttonSpeedMinus.setOnClickListener {
            changeSpeedTo(playbackService!!.rate - 0.01f)
        }
        binding.buttonSpeedPlus.setOnClickListener {
            changeSpeedTo(playbackService!!.rate + 0.01f)
        }

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
        speed = 100 * (1 + ln(speed) / ln(8.0))
        binding.playbackSpeedSeek.progress = speed.toInt()
        updateInterface()
    }

    private fun changeSpeedTo(newValue: Float) {
        if (playbackService == null)
            return
        if (newValue > 8.0F || newValue < 0.25F) return
        playbackService!!.setRate(newValue, true)
        setRateProgress()
    }

    private fun updateInterface() {
        val rate = playbackService!!.rate
        binding.playbackSpeedValue.text = rate.formatRateString()
        if (rate != 1.0f) {
            binding.playbackSpeedValue.setTextColor(orangeColor)
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

    override fun allowRemote() = true

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
