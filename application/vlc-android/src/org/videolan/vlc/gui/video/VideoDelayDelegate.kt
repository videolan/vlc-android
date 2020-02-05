/*
 * ************************************************************************
 *  VideoDelayDelegate.kt
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

package org.videolan.vlc.gui.video

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.ViewStubCompat
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import com.google.android.material.animation.ArgbEvaluatorCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.tools.setGone
import org.videolan.tools.setInvisible
import org.videolan.tools.setVisible
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.OnRepeatListener
import org.videolan.vlc.interfaces.IPlaybackSettingsController
import org.videolan.vlc.media.DelayValues

private const val DELAY_DEFAULT_VALUE = 50000L

/**
 * Delegate for delay management.
 *
 * @property player the player activity instance the delegate is attached to
 */
@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class VideoDelayDelegate(private val player: VideoPlayerActivity) : View.OnClickListener, IPlaybackSettingsController {
    var playbackSetting: IPlaybackSettingsController.DelayState = IPlaybackSettingsController.DelayState.OFF

    private var spuDelay = 0L
    private var audioDelay = 0L

    private var playbackSettingPlus: ImageView? = null
    private var playbackSettingMinus: ImageView? = null
    private var delayFirstButton: MaterialButton? = null
    private var delaySecondButton: MaterialButton? = null
    private var delayResetButton: MaterialButton? = null
    private var delayInfoContainer: View? = null
    private var delayInfo: TextView? = null
    private var delayTitle: TextView? = null
    private var delayContainer: View? = null

    /**
     * Instantiate all the views, set their click listeners and shows the view.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showDelayControls() {
        player.touchDelegate?.clearTouchAction()
        if (!player.displayManager.isPrimary) player.showOverlayTimeout(VideoPlayerActivity.OVERLAY_INFINITE)
        player.info.setInvisible()
        val vsc = player.findViewById<ViewStubCompat>(R.id.player_overlay_settings_stub)
        if (vsc != null) {
            vsc.inflate()
            playbackSettingPlus = player.findViewById(R.id.player_delay_plus)
            playbackSettingMinus = player.findViewById(R.id.player_delay_minus)
            delayFirstButton = player.findViewById(R.id.delay_first_button)
            delaySecondButton = player.findViewById(R.id.delay_second_button)
            delayResetButton = player.findViewById(R.id.delay_reset_button)
            delayInfoContainer = player.findViewById(R.id.delay_info_container)
            delayInfo = player.findViewById(R.id.delay_textinfo)
            delayTitle = player.findViewById(R.id.delay_title)
            delayContainer = player.findViewById(R.id.delay_container)
        }
        delayFirstButton!!.text = if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO) player.getString(R.string.audio_delay_start) else player.getString(R.string.subtitle_delay_first)
        delaySecondButton!!.text = if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO) player.getString(R.string.audio_delay_end) else player.getString(R.string.subtitle_delay_end)
        playbackSettingMinus!!.setOnClickListener(this)
        playbackSettingPlus!!.setOnClickListener(this)
        delayFirstButton!!.setOnClickListener(this)
        delaySecondButton!!.setOnClickListener(this)
        delayResetButton!!.setOnClickListener(this)
        playbackSettingMinus!!.setOnTouchListener(OnRepeatListener(this))
        playbackSettingPlus!!.setOnTouchListener(OnRepeatListener(this))
        playbackSettingMinus.setVisible()
        playbackSettingPlus.setVisible()
        delayFirstButton.setVisible()
        delaySecondButton.setVisible()
        playbackSettingPlus!!.requestFocus()
        initPlaybackSettingInfo()
    }

    /**
     * Initialize the whole delay view state
     *
     */
    private fun initPlaybackSettingInfo() {
        player.initInfoOverlay()
        player.verticalBar.setGone()
        delayContainer.setVisible()
        var text = ""
        val title = when (playbackSetting) {
            IPlaybackSettingsController.DelayState.AUDIO -> {
                text += player.service!!.audioDelay / 1000L
                text += " ms"
                player.getString(R.string.audio_delay)
            }
            IPlaybackSettingsController.DelayState.SUBS -> {
                text += player.service!!.spuDelay / 1000L
                text += " ms"
                player.getString(R.string.spu_delay)
            }
            else -> {
                text += "0"
                ""
            }
        }
        delayTitle?.text = title
        delayInfo?.text = text
    }

    /**
     * Click listener for all the views
     *
     * @param v the view that has been clicked
     */
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.player_delay_minus -> if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO)
                delayAudio(-DELAY_DEFAULT_VALUE)
            else if (playbackSetting == IPlaybackSettingsController.DelayState.SUBS)
                delaySubs(-DELAY_DEFAULT_VALUE)
            R.id.player_delay_plus -> if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO)
                delayAudio(DELAY_DEFAULT_VALUE)
            else if (playbackSetting == IPlaybackSettingsController.DelayState.SUBS)
                delaySubs(DELAY_DEFAULT_VALUE)
            R.id.delay_first_button -> if (player.service?.playlistManager?.delayValue?.value?.start ?: -1L == -1L) {
                player.service?.playlistManager?.setDelayValue(System.currentTimeMillis(), true)
                if (player.service?.playlistManager?.delayValue?.value?.stop == -1L) delaySecondButton?.requestFocus()
            } else {
                player.service?.playlistManager?.setDelayValue(-1L, true)
            }
            R.id.delay_second_button -> if (player.service?.playlistManager?.delayValue?.value?.stop ?: -1L == -1L) {
                player.service?.playlistManager?.setDelayValue(System.currentTimeMillis(), false)
                if (player.service?.playlistManager?.delayValue?.value?.start == -1L) delayFirstButton?.requestFocus()
            } else {
                player.service?.playlistManager?.setDelayValue(-1L, false)
            }
            R.id.delay_reset_button -> {
                if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO) player.service?.setAudioDelay(0) else player.service?.setSpuDelay(0)
                delayInfo?.text = "0 ms"
                player.service?.playlistManager?.resetDelayValues()
            }

        }
    }

    /**
     * Delay audio for the video
     *
     * @param delta the delay to set
     * @param round if true, the delay will be rounded to the delta (useful for plus and minus buttons)
     * @param reset if true, the delta will erase the old value, if not, the new delta will be added to the old one
     */
    fun delayAudio(delta: Long, round: Boolean = false, reset: Boolean = true) {
        player.service?.let { service ->
            val realDelta = if (!round && service.audioDelay % delta != 0L) delta - (service.audioDelay % delta) else if (reset) delta else delta + service.audioDelay
            player.initInfoOverlay()
            val delay = if (round) realDelta else service.audioDelay + realDelta
            service.setAudioDelay(delay)
            delayTitle?.text = player.getString(R.string.audio_delay)
            delayInfo?.text = "${delay / 1000L} ms"
            audioDelay = delay
            if (!player.isPlaybackSettingActive) {
                playbackSetting = IPlaybackSettingsController.DelayState.AUDIO
                initPlaybackSettingInfo()
            }
        }
    }

    /**
     * Delay subtitles for the video
     *
     * @param delta the delay to set
     * @param round if true, the delay will be rounded to the delta (useful for plus and minus buttons)
     * @param reset if true, the delta will erase the old value, if not, the new delta will be added to the old one
     */
    fun delaySubs(delta: Long, round: Boolean = false, reset: Boolean = true) {
        player.service?.let { service ->
            val realDelta = if (!round && service.spuDelay % delta != 0L) delta - (service.spuDelay % delta) else if (reset) delta else delta + service.spuDelay
            player.initInfoOverlay()
            val delay = if (round) realDelta else service.spuDelay + realDelta
            service.setSpuDelay(delay)
            delayInfo?.text = "${delay / 1000L} ms"
            spuDelay = delay
            if (!player.isPlaybackSettingActive) {
                playbackSetting = IPlaybackSettingsController.DelayState.SUBS
                initPlaybackSettingInfo()
            }
        }
    }

    /**
     * Set [playbackSetting] to the right value and shows the view
     *
     */
    override fun showAudioDelaySetting() {
        playbackSetting = IPlaybackSettingsController.DelayState.AUDIO
        showDelayControls()
    }

    /**
     * Set [playbackSetting] to the right value and shows the view
     *
     */
    override fun showSubsDelaySetting() {
        playbackSetting = IPlaybackSettingsController.DelayState.SUBS
        showDelayControls()
    }

    /**
     * Close the view and remove the listeners
     *
     */
    override fun endPlaybackSetting() {
        if (playbackSetting == IPlaybackSettingsController.DelayState.OFF) return
        player.service?.let { service ->
            service.saveMediaMeta()
            if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO && (player.audiomanager.isBluetoothA2dpOn || player.audiomanager.isBluetoothScoOn)) {
                val msg = (player.getString(R.string.audio_delay) + "\n"
                        + service.audioDelay / 1000L
                        + " ms")
                val sb = Snackbar.make(delayInfo!!, msg, Snackbar.LENGTH_LONG)
                sb.setAction(R.string.save_bluetooth_delay, btSaveListener)
                sb.show()
            }
            playbackSetting = IPlaybackSettingsController.DelayState.OFF
            playbackSettingMinus?.setOnClickListener(null)
            playbackSettingPlus?.setOnClickListener(null)
            delayFirstButton?.setOnClickListener(null)
            delaySecondButton?.setOnClickListener(null)
            delayContainer.setInvisible()
            player.overlayInfo.setInvisible()
            service.playlistManager.delayValue.value = DelayValues()
            player.focusPlayPause()
        }
    }

    /**
     * Setup the delay values when the livedata has changed
     *
     * @param delayValues the new values for the delay
     * @param service the playback service instance
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun delayChanged(delayValues: DelayValues, service: PlaybackService) {
        var hasChanged = false
        if (delayValues.start != -1L && delayValues.stop != -1L) {
            hasChanged = if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO) {
                val oldDelay = service.audioDelay
                delayAudio(delayValues.stop * 1000 - delayValues.start * 1000, round = true, reset = false)
                oldDelay != service.audioDelay
            } else {
                val oldDelay = service.spuDelay
                delaySubs(delayValues.stop * 1000 - delayValues.start * 1000, round = true, reset = false)
                oldDelay != service.spuDelay
            }
            service.playlistManager.delayValue.postValue(DelayValues())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            delayFirstButton?.iconTint = ColorStateList.valueOf(if (delayValues.start == -1L) ContextCompat.getColor(player, R.color.grey400transparent) else ContextCompat.getColor(player, R.color.orange500))
            delaySecondButton?.iconTint = ColorStateList.valueOf(if (delayValues.stop == -1L) ContextCompat.getColor(player, R.color.grey400transparent) else ContextCompat.getColor(player, R.color.orange500))
            val viewToAnime = if (delayValues.start == -1L && delayValues.stop != -1L) delayFirstButton else if (delayValues.start != -1L && delayValues.stop == -1L) delaySecondButton else if (hasChanged) delayInfoContainer else null
            viewToAnime?.let { button ->
                val anim = ValueAnimator.ofObject(ArgbEvaluatorCompat(), ContextCompat.getColor(player, R.color.playerbackground), ContextCompat.getColor(player, R.color.orange500focus), ContextCompat.getColor(player, R.color.playerbackground))
                anim.addUpdateListener {
                    button.backgroundTintList = ColorStateList.valueOf(it.animatedValue as Int)
                    if (viewToAnime == delayInfoContainer) viewToAnime.background = ContextCompat.getDrawable(player, R.drawable.video_list_length_bg_opaque)
                }
                anim.doOnEnd { button.backgroundTintList = ContextCompat.getColorStateList(player, R.color.player_delay_button_background_tint) }
                anim.repeatCount = 1
                anim.interpolator = AccelerateDecelerateInterpolator()
                anim.duration = 500
                anim.startDelay = 500
                anim.start()

            }
        }
    }

    private val btSaveListener = View.OnClickListener {
        player.service?.run {
            settings.edit().putLong(VideoPlayerActivity.KEY_BLUETOOTH_DELAY, player.service?.audioDelay
                    ?: 0L).apply()
        }
    }
}
