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
import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.edit
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.KEY_INCOGNITO
import org.videolan.tools.KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE
import org.videolan.tools.KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE
import org.videolan.tools.KEY_PLAYBACK_SPEED_AUDIO_GLOBAL
import org.videolan.tools.KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE
import org.videolan.tools.KEY_PLAYBACK_SPEED_VIDEO_GLOBAL
import org.videolan.tools.KEY_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE
import org.videolan.tools.Settings
import org.videolan.tools.formatRateString
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogPlaybackSpeedBinding
import org.videolan.vlc.gui.helpers.OnRepeatListenerKey
import org.videolan.vlc.gui.helpers.OnRepeatListenerTouch
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.isSchemeStreaming
import kotlin.math.ln
import kotlin.math.pow


class PlaybackSpeedDialog : PlaybackBottomSheetDialogFragment(), PlaybackService.Callback {

    private lateinit var settings: SharedPreferences
    private val forVideo: Boolean
        get() {
            return !(PlaylistManager.showAudioPlayer.value ?: true)
        }
    private lateinit var binding: DialogPlaybackSpeedBinding

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
                val coef = if (progress < 100) 4.0 else 8.0
                val rate = (coef).pow(progress.toDouble() / 100.0 - 1).toFloat()
                changeSpeedTo(rate, true)
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
        settings = Settings.getInstance(requireActivity())

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
        binding.toggleButton.addOnButtonCheckedListener { toggleButton, checkedId, isChecked ->
            if (isChecked) when (checkedId) {
                R.id.this_media -> {
                    settings.edit(commit = true) {
                        putBoolean(if(forVideo) KEY_PLAYBACK_SPEED_VIDEO_GLOBAL else KEY_PLAYBACK_SPEED_AUDIO_GLOBAL, false)
                    }
                    val newValue = getCurrentMedia()?.getMetaString(MediaWrapper.META_SPEED)?.toFloat() ?: 1F
                    changeSpeedTo(newValue)
                }
                R.id.all_media -> {
                    settings.edit(commit = true) {
                        putBoolean(if (forVideo) KEY_PLAYBACK_SPEED_VIDEO_GLOBAL else KEY_PLAYBACK_SPEED_AUDIO_GLOBAL, true)
                    }
                    val newValue = when {
                        settings.getBoolean(KEY_INCOGNITO, false) -> settings.getFloat(if (forVideo) KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE else KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE, 1F)
                        else -> settings.getFloat(if (forVideo) KEY_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE else KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE, 1F)
                    }
                    changeSpeedTo(newValue)
                }
            }
            updateExplanation()
        }

        val initialCheckedId = if (
            forVideo && settings.getBoolean(KEY_PLAYBACK_SPEED_VIDEO_GLOBAL, false) ||
            !forVideo && settings.getBoolean(KEY_PLAYBACK_SPEED_AUDIO_GLOBAL, false)
        )
            R.id.all_media
        else
            R.id.this_media

        binding.toggleButton.check(initialCheckedId)
        binding.thisMedia.text = if (forVideo) getString(R.string.playback_speed_this_video) else getString(R.string.playback_speed_this_track)
        binding.allMedia.text = if (forVideo) getString(R.string.playback_speed_all_videos) else getString(R.string.playback_speed_all_tracks)
        updateExplanation()

        textColor = binding.playbackSpeedValue.currentTextColor


        dialog?.setCancelable(true)
        dialog?.setCanceledOnTouchOutside(true)
        return binding.root
    }

    /**
     * Update the explanation text based on the current playback speed mode.
     *
     */
    private fun updateExplanation() {
        binding.speedModeExplanation.text = when {
            binding.toggleButton.checkedButtonId == R.id.all_media && settings.getBoolean(KEY_INCOGNITO, false) -> buildString {
                append(if (forVideo) getString(R.string.playback_speed_explanation_all_videos) else getString(R.string.playback_speed_explanation_all_tracks) )
                append("\n\n")
                append(getString(R.string.playback_speed_explanation_all_incognito))
            }
            binding.toggleButton.checkedButtonId == R.id.all_media -> if (forVideo) getString(R.string.playback_speed_explanation_all_videos) else getString(R.string.playback_speed_explanation_all_tracks)
            else -> if (forVideo) getString(R.string.playback_speed_explanation_one_video) else getString(R.string.playback_speed_explanation_one_track)
        }
    }

    override fun onServiceAvailable() {
        setRateProgress()
    }

    override fun onMediaChanged() {
        setRateProgress()
    }

    private fun setRateProgress() {
        var speed = playbackService!!.rate.toDouble()
        val coef = if (speed < 1.0) 4.0 else 8.0
        speed = 100 * (1 + ln(speed) / ln(coef))
        binding.playbackSpeedSeek.progress = speed.toInt()
        updateInterface()
    }

    /**
     * Change the playback speed of the current media to the [newValue] and save it in the settings or the media metadata.
     *
     * @param newValue the new playback speed
     * @param preventChangeProgressbar if true, the progress bar will not be updated
     */
    private fun changeSpeedTo(newValue: Float, preventChangeProgressbar:Boolean = false) {
        if (playbackService == null)
            return
        if (newValue > 8.0F || newValue < 0.25F) return
        if (binding.toggleButton.checkedButtonId == R.id.this_media) {
            getCurrentMedia()?.setStringMeta(MediaWrapper.META_SPEED, newValue.toString())
        } else {
            if (settings.getBoolean(KEY_INCOGNITO, false)) {
                settings.edit {
                    putFloat(if (forVideo) KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE else KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE, newValue)
                }
            } else
                settings.edit {
                    putFloat(if (forVideo) KEY_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE else KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE, newValue)
                }
        }
        playbackService!!.setRate(newValue, true)
       if (!preventChangeProgressbar)
           setRateProgress()
        else
            updateInterface()
    }

    private fun getCurrentMedia():MediaWrapper? {
        PlaylistManager.currentPlayedMedia.value?.let {
            if (it.id > 0) return it
             return  playbackService?.medialibrary?.getMedia(it.uri)
        }
        return null
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
