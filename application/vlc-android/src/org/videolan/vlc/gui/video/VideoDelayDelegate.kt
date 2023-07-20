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
import org.videolan.tools.*
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.OnRepeatListenerKey
import org.videolan.vlc.gui.helpers.OnRepeatListenerTouch
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.video.VideoPlayerActivity.Companion.KEY_BLUETOOTH_DELAY
import org.videolan.vlc.interfaces.IPlaybackSettingsController
import org.videolan.vlc.media.DelayValues
import org.videolan.vlc.util.isTalkbackIsEnabled

private const val DELAY_DEFAULT_VALUE = 50000L

/**
 * Delegate for delay management.
 *
 * @property player the player activity instance the delegate is attached to
 */
class VideoDelayDelegate(private val player: VideoPlayerActivity) : View.OnClickListener, IPlaybackSettingsController {
    var playbackSetting: IPlaybackSettingsController.DelayState = IPlaybackSettingsController.DelayState.OFF

    private var spuDelay = 0L
    private var audioDelay = 0L

    private lateinit var playbackSettingPlus: ImageView
    private lateinit var playbackSettingMinus: ImageView
    private lateinit var delayFirstButton: MaterialButton
    private lateinit var delaySecondButton: MaterialButton
    private lateinit var delayResetButton: MaterialButton
    private lateinit var delayInfoContainer: View
    private lateinit var delayInfo: TextView
    private lateinit var delayTitle: TextView
    private lateinit var delayContainer: View
    private lateinit var delayApplyAll: MaterialButton
    private lateinit var delayApplyBt: MaterialButton
    private lateinit var close: ImageView

    /**
     * Instantiate all the views, set their click listeners and shows the view.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showDelayControls() {
        player.touchDelegate.clearTouchAction()
        if (!player.displayManager.isPrimary) player.overlayDelegate.showOverlayTimeout(VideoPlayerActivity.OVERLAY_INFINITE)
        player.overlayDelegate.info.setInvisible()
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
            delayApplyAll = player.findViewById(R.id.delay_apply_all)
            delayApplyBt = player.findViewById(R.id.delay_apply_bt)
            close = player.findViewById(R.id.close)
        }
        delayFirstButton.text = if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO) player.getString(R.string.audio_delay_start) else player.getString(R.string.subtitle_delay_first)
        delaySecondButton.text = if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO) player.getString(R.string.audio_delay_end) else player.getString(R.string.subtitle_delay_end)
        playbackSettingMinus.setOnClickListener(this)
        playbackSettingPlus.setOnClickListener(this)
        delayFirstButton.setOnClickListener(this)
        delaySecondButton.setOnClickListener(this)
        delayResetButton.setOnClickListener(this)
        delayApplyAll.setOnClickListener(this)
        delayApplyBt.setOnClickListener(this)
        close.setOnClickListener(this)
        playbackSettingMinus.setOnTouchListener(OnRepeatListenerTouch(this, player.lifecycle))
        playbackSettingPlus.setOnTouchListener(OnRepeatListenerTouch(this, player.lifecycle))
        playbackSettingMinus.setOnKeyListener(OnRepeatListenerKey(this, player.lifecycle))
        playbackSettingPlus.setOnKeyListener(OnRepeatListenerKey(this, player.lifecycle))
        playbackSettingMinus.setVisible()
        close.setVisible()
        playbackSettingPlus.setVisible()
        delayFirstButton.setVisible()
        delaySecondButton.setVisible()
        if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO && (player.audiomanager.isBluetoothA2dpOn || player.audiomanager.isBluetoothScoOn)) delayApplyBt.setVisible() else delayApplyBt.setGone()
        playbackSettingPlus.requestFocus()
        initPlaybackSettingInfo()
        if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO) delayApplyAll.setVisible() else delayApplyAll.setGone()
        if (player.displayManager.isPrimary) player.overlayDelegate.hideOverlay(fromUser = true, forceTalkback = true)
    }

    /**
     * Initialize the whole delay view state
     *
     */
    private fun initPlaybackSettingInfo() {
        player.overlayDelegate.initInfoOverlay()
        delayContainer.setVisible()
        val text: String
        val title = when (playbackSetting) {
            IPlaybackSettingsController.DelayState.AUDIO -> {
                text = "${player.service!!.audioDelay / 1000L} ms"
                player.getString(R.string.audio_delay)
            }
            IPlaybackSettingsController.DelayState.SUBS -> {
                text = "${player.service!!.spuDelay / 1000L} ms"
                player.getString(R.string.spu_delay)
            }
            else -> {
                text = "0"
                ""
            }
        }
        delayTitle.text = title
        delayInfo.text = text
    }

    /**
     * Click listener for all the views
     *
     * @param v the view that has been clicked
     */
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.player_delay_minus -> delayAudioOrSpu(-DELAY_DEFAULT_VALUE, delayState = playbackSetting)
            R.id.player_delay_plus -> delayAudioOrSpu(DELAY_DEFAULT_VALUE, delayState = playbackSetting)
            R.id.delay_first_button -> if (player.service?.playlistManager?.delayValue?.value?.start ?: -1L == -1L) {
                player.service?.playlistManager?.setDelayValue(System.currentTimeMillis(), true)
                if (player.service?.playlistManager?.delayValue?.value?.stop == -1L) delaySecondButton.requestFocus()
            } else {
                player.service?.playlistManager?.setDelayValue(-1L, true)
            }
            R.id.delay_second_button -> if (player.service?.playlistManager?.delayValue?.value?.stop ?: -1L == -1L) {
                player.service?.playlistManager?.setDelayValue(System.currentTimeMillis(), false)
                if (player.service?.playlistManager?.delayValue?.value?.start == -1L) delayFirstButton.requestFocus()
            } else {
                player.service?.playlistManager?.setDelayValue(-1L, false)
            }
            R.id.delay_reset_button -> {
                if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO) player.service?.setAudioDelay(0) else player.service?.setSpuDelay(0)
                delayInfo.text = "0 ms"
                player.service?.playlistManager?.resetDelayValues()
            }
            R.id.delay_apply_all -> {
                player.service?.let {
                    Settings.getInstance(player).putSingle(AUDIO_DELAY_GLOBAL, it.audioDelay)
                    UiTools.snacker(player, player.getString(R.string.audio_delay_global, "${it.audioDelay / 1000L}"))
                }
            }
            R.id.delay_apply_bt -> {
                player.service?.let {
                    Settings.getInstance(player).putSingle(KEY_BLUETOOTH_DELAY, it.audioDelay)
                    UiTools.snacker(player, player.getString(R.string.audio_delay_bt, "${it.audioDelay / 1000L}"))
                }
            }
            R.id.close -> endPlaybackSetting()

        }
    }

    /**
     * Delay audio or spu for the video
     *
     * @param delta the delay to set
     * @param fromCustom does the delay change come from the custom buttons? If so, the delay should always be added to the previous one
     */
    fun delayAudioOrSpu(delta: Long, fromCustom: Boolean = false, delayState: IPlaybackSettingsController.DelayState) {
        if (delayState == IPlaybackSettingsController.DelayState.OFF) return
        player.service?.let { service ->
            val currentDelay = if (delayState == IPlaybackSettingsController.DelayState.SUBS) service.spuDelay else service.audioDelay
            val delay = currentDelay + when {
                // Comes from plus or minus buttons. We try to round it if needed
                !fromCustom && currentDelay % delta != 0L -> delta - (currentDelay % delta)
                else -> delta
            }
            player.overlayDelegate.initInfoOverlay()
            if (delayState == IPlaybackSettingsController.DelayState.SUBS) service.setSpuDelay(delay) else service.setAudioDelay(delay)
            if (::delayTitle.isInitialized) delayTitle.text =
                player.getString(if (delayState == IPlaybackSettingsController.DelayState.SUBS) R.string.spu_delay else R.string.audio_delay)
            if (::delayInfo.isInitialized) delayInfo.text = "${delay / 1000L} ms"
            if (delayState == IPlaybackSettingsController.DelayState.SUBS) spuDelay = delay else audioDelay = delay
            if (!player.isPlaybackSettingActive) {
                playbackSetting = delayState
                showDelayControls()
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
            playbackSetting = IPlaybackSettingsController.DelayState.OFF
            playbackSettingMinus.setOnClickListener(null)
            playbackSettingPlus.setOnClickListener(null)
            delayFirstButton.setOnClickListener(null)
            delaySecondButton.setOnClickListener(null)
            close.setOnClickListener(null)
            delayContainer.setInvisible()
            player.overlayDelegate.overlayInfo.setInvisible()
            service.playlistManager.delayValue.value = DelayValues()
            player.overlayDelegate.focusPlayPause()
        }
        if (player.isTalkbackIsEnabled()) player.overlayDelegate.showOverlay()
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
            val oldDelay = if (playbackSetting == IPlaybackSettingsController.DelayState.SUBS) service.spuDelay else service.audioDelay
            delayAudioOrSpu(delayValues.start * 1000 - delayValues.stop * 1000, fromCustom = true, delayState = playbackSetting)
            hasChanged = oldDelay != if (playbackSetting == IPlaybackSettingsController.DelayState.SUBS) service.spuDelay else service.audioDelay
            service.playlistManager.delayValue.postValue(DelayValues())
        }
        if (!::delayFirstButton.isInitialized) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            delayFirstButton.iconTint = ColorStateList.valueOf(if (delayValues.start == -1L) ContextCompat.getColor(player, R.color.grey400transparent) else ContextCompat.getColor(player, R.color.orange500))
            delaySecondButton.iconTint = ColorStateList.valueOf(if (delayValues.stop == -1L) ContextCompat.getColor(player, R.color.grey400transparent) else ContextCompat.getColor(player, R.color.orange500))
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
            settings.putSingle(KEY_BLUETOOTH_DELAY, player.service?.audioDelay ?: 0L)
        }
    }
}
