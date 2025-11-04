/*
 * ************************************************************************
 *  VideoPlayerOverlayDelegate.kt
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

import android.animation.Animator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.ViewStubCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.children
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.window.layout.FoldingFeature
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaWrapperImpl
import org.videolan.resources.AndroidDevices
import org.videolan.tools.ALLOW_FOLD_AUTO_LAYOUT
import org.videolan.tools.ENABLE_SEEK_BUTTONS
import org.videolan.tools.HINGE_ON_RIGHT
import org.videolan.tools.KEY_ALWAYS_FAST_SEEK
import org.videolan.tools.KEY_ENABLE_CASTING
import org.videolan.tools.KEY_ENABLE_CLONE_MODE
import org.videolan.tools.KEY_PLAYBACK_SPEED_VIDEO_GLOBAL
import org.videolan.tools.SCREENSHOT_MODE
import org.videolan.tools.SHOW_ORIENTATION_BUTTON
import org.videolan.tools.Settings
import org.videolan.tools.VIDEO_TRANSITION_SHOW
import org.videolan.tools.dp
import org.videolan.tools.formatRateString
import org.videolan.tools.putSingle
import org.videolan.tools.runIO
import org.videolan.tools.setGone
import org.videolan.tools.setInvisible
import org.videolan.tools.setVisible
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.RendererDelegate
import org.videolan.vlc.VlcMigrationHelper
import org.videolan.vlc.databinding.PlayerHudBinding
import org.videolan.vlc.databinding.PlayerHudRightBinding
import org.videolan.vlc.gui.audio.PlaylistAdapter
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.browser.KEY_MEDIA
import org.videolan.vlc.gui.dialogs.VideoTracksDialog
import org.videolan.vlc.gui.helpers.BookmarkListDelegate
import org.videolan.vlc.gui.helpers.OnRepeatListenerKey
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback
import org.videolan.vlc.gui.helpers.TalkbackUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.showVideoTrack
import org.videolan.vlc.gui.helpers.hf.checkPIN
import org.videolan.vlc.gui.view.PlayerProgress
import org.videolan.vlc.isVLC4
import org.videolan.vlc.manageAbRepeatStep
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.util.isSchemeFile
import org.videolan.vlc.util.isSchemeNetwork
import org.videolan.vlc.util.isTalkbackIsEnabled
import org.videolan.vlc.viewmodels.PlaylistModel
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale


class VideoPlayerOverlayDelegate (private val player: VideoPlayerActivity) {

    private lateinit var playerOverlayBrightness: ConstraintLayout
    private lateinit var brightnessValueText: TextView
    private lateinit var playerBrightnessProgress: PlayerProgress
    private lateinit var playerOverlayVolume: ConstraintLayout
    private lateinit var volumeValueText: TextView
    private lateinit var playerVolumeProgress: PlayerProgress
    var info: TextView? = null
    var subinfo: TextView? = null
    var overlayInfo: View? = null
    lateinit var playerUiContainer: ViewGroup

    lateinit var hudBinding: PlayerHudBinding
    lateinit var hudRightBinding: PlayerHudRightBinding
    private var overlayBackground: View? = null


    private var overlayTimeout = 0
    private var wasPlaying = true

    lateinit var playToPause: AnimatedVectorDrawableCompat
    lateinit var pauseToPlay: AnimatedVectorDrawableCompat

    private val hudBackground: View? by lazy { player.findViewById(R.id.hud_background) }
    private val hudRightBackground: View? by lazy { player.findViewById(R.id.hud_right_background) }

    private lateinit var abRepeatAddMarker: Button

    var seekButtons: Boolean = false
    var hasPlaylist: Boolean = false
    private var hingeSnackShown: Boolean = false

    var enableSubs = true
    var bookmarkListDelegate: BookmarkListDelegate? = null

    fun isHudBindingInitialized() = ::hudBinding.isInitialized
    fun isHudRightBindingInitialized() = ::hudRightBinding.isInitialized
    fun isPlaylistAdapterInitialized() = ::playlistAdapter.isInitialized

    private var orientationLockedBeforeLock: Boolean = false
    lateinit var closeButton: View
    lateinit var playlistContainer: View
    var hingeArrowRight: ImageView? = null
    var hingeArrowLeft: ImageView? = null
    lateinit var playlist: RecyclerView
    lateinit var playlistSearchText: TextInputLayout
    lateinit var playlistAdapter: PlaylistAdapter
    var foldingFeature: FoldingFeature? = null
        set(value) {
            field = value
            manageHinge()
        }

    /**
     * Changes the device layout depending on the scree foldable status and features
     */
     fun manageHinge() {
        player.service?.mediaplayer?.setUseOrientationFromBounds(false)
        resetHingeLayout()
        if (foldingFeature == null || !Settings.getInstance(player).getBoolean(ALLOW_FOLD_AUTO_LAYOUT, true)) return
        val foldingFeature = foldingFeature!!

        //device is fully occluded and split vertically. We display the controls on the half left or right side
        if (foldingFeature.occlusionType == FoldingFeature.OcclusionType.FULL && foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL) {
            val onRight = Settings.getInstance(player).getBoolean(HINGE_ON_RIGHT, true)
            hingeArrowLeft?.visibility = if (onRight && ::hudBinding.isInitialized) View.VISIBLE else View.GONE
            hingeArrowRight?.visibility = if (!onRight && ::hudBinding.isInitialized) View.VISIBLE else View.GONE
            val halfScreenSize = player.getScreenWidth() - foldingFeature.bounds.right
            arrayOf(playerUiContainer, hudBackground, hudRightBackground, playlistContainer).forEach {
                it?.let { view ->
                    val lp = (view.layoutParams as FrameLayout.LayoutParams)
                    lp.width = halfScreenSize
                    //get vertical flags to keep them
                    val newGravity = lp.gravity and Gravity.VERTICAL_GRAVITY_MASK
                    lp.gravity = newGravity or (if (onRight) Gravity.END else Gravity.START)
                    view.layoutParams = lp
                }
            }
            showHingeSnackIfNeeded()
        } else {
            //device is separated and half opened. We display the controls on the bottom half and the video on the top half
            if (foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                !(foldingFeature.occlusionType == FoldingFeature.OcclusionType.NONE && foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL)) {
                val videoLayoutLP = (player.videoLayout!!.layoutParams as ViewGroup.LayoutParams)
                val halfScreenSize = foldingFeature.bounds.top
                videoLayoutLP.height = halfScreenSize
                player.videoLayout!!.layoutParams = videoLayoutLP
                player.service?.mediaplayer?.setUseOrientationFromBounds(true)
                player.findViewById<FrameLayout>(R.id.player_surface_frame).children.forEach { it.requestLayout() }

                arrayOf(playerUiContainer, playlistContainer).forEach {
                    val lp = (it.layoutParams as FrameLayout.LayoutParams)
                    lp.height = halfScreenSize
                    lp.gravity = Gravity.BOTTOM
                    it.layoutParams = lp
                }
                arrayOf(hudBackground, hudRightBackground).forEach {
                    it?.setGone()
                }
                showHingeSnackIfNeeded()
            }
        }
    }

    /**
     * Shows the fold layout snackbar if needed
     */
    private fun showHingeSnackIfNeeded() {
        if (!hingeSnackShown) {
            UiTools.snackerConfirm(player, player.getString(R.string.fold_optimized), confirmMessage = R.string.undo) {
                player.resizeDelegate.showResizeOverlay()
            }
            hingeSnackShown = true
        }
    }

    /**
     * Resets the layout to normal after a fold/hinge status change
     */
    private fun resetHingeLayout() {
        arrayOf(playerUiContainer, hudBackground, hudRightBackground, playlistContainer).forEach {
            it?.let { view ->
                val lp = (view.layoutParams as ViewGroup.LayoutParams)
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                view.layoutParams = lp
            }
        }
        arrayOf(playerUiContainer, playlistContainer).forEach {
            val lp = (it.layoutParams as ViewGroup.LayoutParams)
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            it.layoutParams = lp
        }
        if (::hudBinding.isInitialized) arrayOf(hudBackground, hudRightBackground).forEach {
            it?.setVisible()
        }
        hingeArrowLeft?.visibility = View.GONE
        hingeArrowRight?.visibility = View.GONE
        val lp = (player.videoLayout!!.layoutParams as ViewGroup.LayoutParams)
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT
        player.videoLayout!!.layoutParams = lp
        player.findViewById<FrameLayout>(R.id.player_surface_frame).children.forEach { it.requestLayout() }
    }

    fun showTracks() {
        player.showVideoTrack(
                {
                    when (it) {
                        VideoTracksDialog.VideoTrackOption.AUDIO_DELAY -> player.delayDelegate.showAudioDelaySetting()
                        VideoTracksDialog.VideoTrackOption.SUB_DELAY -> player.delayDelegate.showSubsDelaySetting()
                        VideoTracksDialog.VideoTrackOption.SUB_DOWNLOAD -> downloadSubtitles()
                        VideoTracksDialog.VideoTrackOption.SUB_PICK -> pickSubtitles()
                    }
                }, { trackID: String, trackType: VideoTracksDialog.TrackType ->
            when (trackType) {
                VideoTracksDialog.TrackType.AUDIO -> {
                    player.service?.let { service ->
                        if (isVLC4() && trackID == "-1")
                            service.unselectTrackType(trackType)
                        else
                            service.setAudioTrack(trackID)
                        runIO {
                            val mw = player.medialibrary.findMedia(service.currentMediaWrapper)
                            if (mw != null && mw.id != 0L) mw.setStringMeta(MediaWrapper.META_AUDIOTRACK, trackID)
                        }
                    }
                }
                VideoTracksDialog.TrackType.SPU -> {
                    player.service?.let { service ->
                        if (isVLC4() && trackID == "-1")
                            service.unselectTrackType(trackType)
                        else
                            service.setSpuTrack(trackID)
                        runIO {
                            val mw = player.medialibrary.findMedia(service.currentMediaWrapper)
                            if (mw != null && mw.id != 0L) mw.setStringMeta(MediaWrapper.META_SUBTITLE_TRACK, trackID)
                        }
                    }
                }
                VideoTracksDialog.TrackType.VIDEO -> {
                    player.service?.let { service ->
                        player.seek(service.getTime())
                        if (isVLC4() && trackID == "-1")
                            service.unselectTrackType(trackType)
                        else
                            service.setVideoTrack(trackID)
                        runIO {
                            val mw = player.medialibrary.findMedia(service.currentMediaWrapper)
                            if (mw != null && mw.id != 0L) mw.setStringMeta(MediaWrapper.META_VIDEOTRACK, trackID)
                        }
                    }
                }
            }
        })
    }

    fun showInfo(@StringRes textId: Int , duration: Int ,@StringRes subtextId: Int = -1) {
        showInfo(player.getString(textId), duration, if (subtextId == -1) "" else player.getString(subtextId))
    }

    /**
     * Show text in the info view for "duration" milliseconds
     * @param text
     * @param duration
     */
    fun showInfo(text: String, duration: Int, subText:String = "") {
        if (player.isInPictureInPictureMode) return
        initInfoOverlay()
        overlayInfo.setVisible()
        info.setVisible()
        info?.text = text
        if (subText.isNotBlank()) {
            subinfo?.text = subText
            subinfo.setVisible()
        } else subinfo.setGone()
        player.handler.removeMessages(VideoPlayerActivity.FADE_OUT_INFO)
        player.handler.sendEmptyMessageDelayed(VideoPlayerActivity.FADE_OUT_INFO, duration.toLong())
        player.rootView?.announceForAccessibility("$text.$subText")
    }

    fun hideInfo() {
        player.handler.sendEmptyMessage(VideoPlayerActivity.FADE_OUT_INFO)
    }

     fun fadeOutInfo(view:View?) {
        if (view?.visibility == View.VISIBLE) {
            view.startAnimation(AnimationUtils.loadAnimation(
                    player, android.R.anim.fade_out))
            view.setInvisible()
        }
    }

    fun initInfoOverlay() {
        val vsc = player.findViewById<ViewStubCompat>(R.id.player_info_stub)
        if (vsc != null) {
            vsc.setVisible()
            // the info textView is not on the overlay
            info = player.findViewById(R.id.player_overlay_textinfo)
            subinfo = player.findViewById(R.id.player_overlay_subtextinfo)
            overlayInfo = player.findViewById(R.id.player_overlay_info)
        }
    }

    /**
     * Show the brightness value with  bar
     * @param brightness the brightness value
     */
    fun showBrightnessBar(brightness: Int) {
        player.handler.sendEmptyMessage(VideoPlayerActivity.FADE_OUT_VOLUME_INFO)
        player.findViewById<ViewStubCompat>(R.id.player_brightness_stub)?.setVisible()
        playerOverlayBrightness = player.findViewById(R.id.player_overlay_brightness)
        brightnessValueText = player.findViewById(R.id.brightness_value_text)
        playerBrightnessProgress = player.findViewById(R.id.playerBrightnessProgress)
        playerOverlayBrightness.setVisible()
        brightnessValueText.text = "$brightness%"
        playerBrightnessProgress.setValue(brightness)
        playerOverlayBrightness.setVisible()
        player.handler.removeMessages(VideoPlayerActivity.FADE_OUT_BRIGHTNESS_INFO)
        player.handler.sendEmptyMessageDelayed(VideoPlayerActivity.FADE_OUT_BRIGHTNESS_INFO, 1000L)
        dimStatusBar(true)
    }

    /**
     * Show the volume value with  bar
     * @param volume the volume value
     */
    fun showVolumeBar(volume: Int) {
        player.handler.sendEmptyMessage(VideoPlayerActivity.FADE_OUT_BRIGHTNESS_INFO)
        player.findViewById<ViewStubCompat>(R.id.player_volume_stub)?.setVisible()
        playerOverlayVolume = player.findViewById(R.id.player_overlay_volume)
        volumeValueText = player.findViewById(R.id.volume_value_text)
        playerVolumeProgress = player.findViewById(R.id.playerVolumeProgress)
        volumeValueText.text = "$volume%"
        playerVolumeProgress.isDouble = player.isAudioBoostEnabled
        playerVolumeProgress.setValue(volume)
        playerOverlayVolume.setVisible()
        player.handler.removeMessages(VideoPlayerActivity.FADE_OUT_VOLUME_INFO)
        player.handler.sendEmptyMessageDelayed(VideoPlayerActivity.FADE_OUT_VOLUME_INFO, 1000L)
        dimStatusBar(true)
        player.service?.let { service ->
            resetSleepTimer(service)
        }
    }

    /**
     * Dim the status bar and/or navigation icons when needed on Android 3.x.
     * Hide it on Android 4.0 and later
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun dimStatusBar(dim: Boolean) {
        if (player.isNavMenu) return

        var visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        var navbar = 0
        if (dim || player.isLocked) {
            player.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            navbar = navbar or (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LOW_PROFILE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            if (VlcMigrationHelper.isKitKatOrLater) visibility = visibility or View.SYSTEM_UI_FLAG_IMMERSIVE
            visibility = visibility or View.SYSTEM_UI_FLAG_FULLSCREEN
        } else {
            player.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            visibility = visibility or View.SYSTEM_UI_FLAG_VISIBLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        playerUiContainer.setPadding(0, 0, 0, 0)
        playerUiContainer.fitsSystemWindows = !player.isLocked

        if (AndroidDevices.hasNavBar)
            visibility = visibility or navbar
        player.window.decorView.systemUiVisibility = visibility
    }

    /**
     * show overlay
     * @param forceCheck: adjust the timeout in function of playing state
     */
    fun showOverlay(forceCheck: Boolean = false) {
        if (forceCheck) overlayTimeout = 0
        showOverlayTimeout(0)
    }

    /**
     * show overlay
     */
    fun showOverlayTimeout(timeout: Int) {
        player.service?.let { service ->
            if (player.tipsDelegate.currentTip != null) return
            if (player.isInPictureInPictureMode) return
            initOverlay()
            if (!::hudBinding.isInitialized) return
            overlayTimeout = when {
                service.playlistManager.videoStatsOn.value == true -> VideoPlayerActivity.OVERLAY_INFINITE
                player.isTalkbackIsEnabled() -> VideoPlayerActivity.OVERLAY_INFINITE
                Settings.videoHudDelay == -1 -> VideoPlayerActivity.OVERLAY_INFINITE
                isBookmarkShown() -> VideoPlayerActivity.OVERLAY_INFINITE
                timeout != 0 -> timeout
                service.isPlaying -> when (Settings.videoHudDelay) {
                    -1 -> VideoPlayerActivity.OVERLAY_INFINITE
                    else -> Settings.videoHudDelay * 1000
                }
                else -> VideoPlayerActivity.OVERLAY_INFINITE
            }
            if (player.isNavMenu) {
                player.isShowing = true
                return
            }
            if (!player.isShowing) {
                player.isShowing = true
                if (!player.isLocked) {
                    showControls(true)
                }
                if (!isBookmarkShown()) dimStatusBar(false)

                enterAnimate(arrayOf(hudBinding.progressOverlay, hudBackground), 100.dp.toFloat()) {
                    if (overlayTimeout != VideoPlayerActivity.OVERLAY_INFINITE)
                        player.handler.sendMessageDelayed(player.handler.obtainMessage(VideoPlayerActivity.FADE_OUT), overlayTimeout.toLong())
                    hudBinding.playerOverlayPlay.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    if (isBookmarkShown())try {
                        if (player.isTalkbackIsEnabled()) bookmarkListDelegate?.addBookmarkButton?.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    } catch (e: Exception) {
                    }
                }
                enterAnimate(arrayOf(hudRightBinding.hudRightOverlay, hudRightBackground), -100.dp.toFloat())

                hingeArrowLeft?.animate()?.alpha(1F)
                hingeArrowRight?.animate()?.alpha(1F)

                if (!player.displayManager.isPrimary)
                    overlayBackground.setVisible()
                updateOverlayPausePlay(true)
                player.handler.removeMessages(VideoPlayerActivity.FADE_OUT)
            } else {
                player.handler.removeMessages(VideoPlayerActivity.FADE_OUT)
                if (overlayTimeout != VideoPlayerActivity.OVERLAY_INFINITE)
                    player.handler.sendMessageDelayed(player.handler.obtainMessage(VideoPlayerActivity.FADE_OUT), overlayTimeout.toLong())
            }

            resetSleepTimer(service)
        }
    }

    private fun resetSleepTimer(service: PlaybackService) {
        if (!service.resetOnInteraction) return
        val sleepTime = Calendar.getInstance()
        sleepTime.timeInMillis = System.currentTimeMillis() + service.sleepTimerInterval
        PlaybackService.playerSleepTime.value = sleepTime
    }

    fun updateOverlayPausePlay(skipAnim: Boolean = false) {
        if (!::hudBinding.isInitialized) return
        player.service?.let { service ->
            if (service.isPausable) {

                if (skipAnim) {
                    hudBinding.playerOverlayPlay.setImageResource(if (service.isPlaying)
                        R.drawable.ic_pause_player
                    else
                        R.drawable.ic_play_player)
                } else {
                    val drawable = if (service.isPlaying) playToPause else pauseToPlay
                    hudBinding.playerOverlayPlay.setImageDrawable(drawable)
                    if (service.isPlaying != wasPlaying) drawable.start()
                }
                hudBinding.playerOverlayPlay.contentDescription = player.getString(if (service.isPlaying) R.string.pause else R.string.play)

                wasPlaying = service.isPlaying
            }
            hudBinding.playerOverlayPlay.requestFocus()
            if (::playlistAdapter.isInitialized) {
                playlistAdapter.setCurrentlyPlaying(service.isPlaying)
            }
        }
    }

    private fun enterAnimate(views: Array<View?>, translationStart: Float, endListener:(()->Unit)? = null) = views.forEach { view ->
        view.setVisible()
        view?.alpha = 0f
        view?.translationY = translationStart
        view?.animate()?.alpha(1F)?.translationY(0F)?.setDuration(150L)?.setListener(null)?.withEndAction {
            endListener?.invoke()
        }
    }

    private fun exitAnimate(views: Array<View?>, translationEnd: Float) = views.forEach { view ->
        view?.animate()?.alpha(0F)?.translationY(translationEnd)?.setDuration(150L)?.setListener(object : Animator.AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                view.setInvisible()
            }
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
            override fun onAnimationStart(animation: Animator) {}
        })
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun initOverlay() {
        player.service?.let { service ->
            val vscRight = player.findViewById<ViewStubCompat>(R.id.player_hud_right_stub)
            vscRight?.let {
                it.setVisible()
                hudRightBinding = DataBindingUtil.bind(player.findViewById(R.id.hud_right_overlay)) ?: return
                if (!player.isBenchmark && player.enableCloneMode && !player.settings.contains(KEY_ENABLE_CLONE_MODE)) {
                    UiTools.snackerConfirm(player, player.getString(R.string.video_save_clone_mode)) { player.settings.putSingle(KEY_ENABLE_CLONE_MODE, true) }
                }
            }

            val vsc = player.findViewById<ViewStubCompat>(R.id.player_hud_stub)
            if (vsc != null) {
                seekButtons = player.settings.getBoolean(ENABLE_SEEK_BUTTONS, false)
                vsc.setVisible()
                hudBinding = DataBindingUtil.bind(player.findViewById(R.id.progress_overlay)) ?: return
                hudBinding.player = player
                hudBinding.progress = service.playlistManager.player.progress
                abRepeatAddMarker = hudBinding.abRepeatContainer.findViewById(R.id.ab_repeat_add_marker)
                service.playlistManager.abRepeat.observe(player) { abvalues ->
                    if (abvalues.start != -1L && abvalues.stop != -1L && player.settings.getBoolean(KEY_ALWAYS_FAST_SEEK, false)) {
                        hudBinding.fastSeekWarning.setVisible()
                    } else {
                        hudBinding.fastSeekWarning.setGone()
                    }
                    hudBinding.abRepeatA = if (abvalues.start == -1L) -1F else abvalues.start / service.playlistManager.player.getLength().toFloat()
                    hudBinding.abRepeatB = if (abvalues.stop == -1L) -1F else abvalues.stop / service.playlistManager.player.getLength().toFloat()
                    hudBinding.abRepeatMarkerA.visibility = if (abvalues.start == -1L) View.GONE else View.VISIBLE
                    hudBinding.abRepeatMarkerB.visibility = if (abvalues.stop == -1L) View.GONE else View.VISIBLE
                    service.manageAbRepeatStep(hudBinding.abRepeatReset, hudBinding.abRepeatStop, hudBinding.abRepeatContainer, abRepeatAddMarker)
                    if (player.settings.getBoolean(VIDEO_TRANSITION_SHOW, true)) showOverlayTimeout(if (abvalues.start == -1L || abvalues.stop == -1L) VideoPlayerActivity.OVERLAY_INFINITE else Settings.videoHudDelay * 1000)
                }
                service.playlistManager.abRepeatOn.observe(player) {
                    abRepeatAddMarker.visibility = if (it) View.VISIBLE else View.GONE
                    hudBinding.abRepeatMarkerGuidelineContainer.visibility = if (it) View.VISIBLE else View.GONE
                    if (it) showOverlay(true)
                    if (it) {
                        hudBinding.playerOverlayLength.nextFocusUpId = R.id.ab_repeat_add_marker
                        hudBinding.playerOverlayTime.nextFocusUpId = R.id.ab_repeat_add_marker
                    }
                    if (it) showOverlayTimeout(VideoPlayerActivity.OVERLAY_INFINITE)

                    service.manageAbRepeatStep(hudBinding.abRepeatReset, hudBinding.abRepeatStop, hudBinding.abRepeatContainer, abRepeatAddMarker)
                }
                service.playlistManager.delayValue.observe(player) {
                    player.delayDelegate.delayChanged(it, service)
                }
                service.playlistManager.videoStatsOn.observe(player) {
                    if (it) showOverlay(true) else hideOverlay(false)
                    player.statsDelegate.container = hudBinding.statsContainer
                    player.statsDelegate.initPlotView(hudBinding)
                    if (it) player.statsDelegate.start() else player.statsDelegate.stop()
                }
                hudBinding.statsClose.setOnClickListener { service.playlistManager.videoStatsOn.postValue(false) }

                hudBinding.lifecycleOwner = player
                updateOrientationIcon()
                overlayBackground = player.findViewById(R.id.player_overlay_background)
                if (!AndroidDevices.isChromeBook && !player.isTv
                        && player.settings.getBoolean(KEY_ENABLE_CASTING, true)) {
                    PlaybackService.renderer.observe(player) { rendererItem -> hudRightBinding.videoRenderer.setImageDrawable(AppCompatResources.getDrawable(player, if (rendererItem == null) R.drawable.ic_player_renderer else R.drawable.ic_player_renderer_on)) }
                    RendererDelegate.renderers.observe(player) { updateRendererVisibility() }
                }

                setTitle(service.currentMediaWrapper?.title)
                manageTitleConstraints()
                updateTitleConstraints()
                updateHudMargins()

                initSeekButton()


                resetHudLayout()
                updateOverlayPausePlay(true)
                updateSeekable(service.isSeekable)
                updatePausable(service.isPausable)
                player.updateNavStatus()
                setListeners(true)
                initPlaylistUi()
                updateScreenshotButton()
                if (foldingFeature != null) manageHinge()
            } else if (::hudBinding.isInitialized) {
                hudBinding.progress = service.playlistManager.player.progress
                hudBinding.lifecycleOwner = player
            }
        }
    }

    fun setTitle(title: String?) {
        if (!::hudBinding.isInitialized) return
        hudRightBinding.playerOverlayTitle.text = title
        if (title?.startsWith("fd://") == false) {
            hudRightBinding.playerOverlayTitle.setVisible()
            hudRightBinding.playerOverlayTitleWarning.setGone()
        } else {
            hudRightBinding.playerOverlayTitle.setGone()
            hudRightBinding.playerOverlayTitleWarning.setVisible()
        }
    }

    fun updateSeekable(seekable: Boolean) {
        if (!::hudBinding.isInitialized) return
        hudBinding.playerOverlayRewind.isEnabled = seekable
        hudBinding.playerOverlayRewind.setImageResource(if (seekable)
            R.drawable.ic_player_rewind_10
        else
            R.drawable.ic_player_rewind_10_disabled)
        hudBinding.playerOverlayForward.isEnabled = seekable
        hudBinding.playerOverlayForward.setImageResource(if (seekable)
            R.drawable.ic_player_forward_10
        else
            R.drawable.ic_player_forward_10_disabled)
        if (!player.isLocked)
            hudBinding.playerOverlaySeekbar.isEnabled = seekable
    }

    fun setListeners(enabled: Boolean) {
        if (::hudBinding.isInitialized) {
            hudBinding.playerOverlaySeekbar.setOnSeekBarChangeListener(if (enabled) player.seekListener else null)
            hudBinding.abRepeatReset.setOnClickListener(player)
            hudBinding.abRepeatStop.setOnClickListener(player)
            hudBinding.fastSeekWarning.setOnClickListener {
               UiTools.snacker(player, R.string.ab_repeat_fastseek_warning, false)
            }
            abRepeatAddMarker.setOnClickListener(player)
            hudBinding.orientationToggle.setOnClickListener(if (enabled) player else null)
            hudBinding.orientationToggle.setOnLongClickListener(if (enabled) player else null)
            hudBinding.swipeToUnlock.setOnStartTouchingListener { showOverlayTimeout(VideoPlayerActivity.OVERLAY_INFINITE) }
            hudBinding.swipeToUnlock.setOnStopTouchingListener { showOverlayTimeout(Settings.videoHudDelay * 1000) }
            hudBinding.swipeToUnlock.setOnUnlockListener {
                player.lifecycleScope.launch(Dispatchers.IO) {
                    if (!player.checkPIN())
                        player.isLocked = false
                    withContext(Dispatchers.Main) {
                        player.toggleLock()
                    }
                }
            }
            hudRightBinding.playerOverlayTitleWarning.setOnClickListener(player)
            hudBinding.playerOverlaySeekbar.setOnClickListener {
                if (player.service?.isPaused == true)
                    player.togglePlayPause()
            }
        }
        if (::hudRightBinding.isInitialized){
            hudRightBinding.playerOverlayNavmenu.setOnClickListener(if (enabled) player else null)
            UiTools.setViewOnClickListener(hudRightBinding.videoRenderer, if (enabled) player else null)
            hudRightBinding.playbackSpeedQuickAction.setOnLongClickListener {
                player.service?.setRate(1F, true)
                showControls(true)
                true
            }
            hudRightBinding.sleepQuickAction.setOnLongClickListener {
                player.service?.setSleepTimer(null)
                showControls(true)
                true
            }
            hudRightBinding.audioDelayQuickAction.setOnLongClickListener {
                player.service?.setAudioDelay(0L)
                showControls(true)
                true
            }
            hudRightBinding.spuDelayQuickAction.setOnLongClickListener {
                player.service?.setSpuDelay(0L)
                showControls(true)
                true
            }
            hudRightBinding.quickActionsContainer.setOnTouchListener { _, _ ->
                showOverlay()
                false
            }
        }
    }

    fun updatePausable(pausable: Boolean) {
        if (!::hudBinding.isInitialized) return
        hudBinding.playerOverlayPlay.isEnabled = pausable
        if (!pausable)
            hudBinding.playerOverlayPlay.setImageResource(R.drawable.ic_play_player_disabled)
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun resetHudLayout() {
        if (!::hudBinding.isInitialized) return
        if (!player.isTv && !AndroidDevices.isChromeBook) {
            hudBinding.orientationToggle.setVisible()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSeekButton() {
        if (!::hudBinding.isInitialized) return
        hudBinding.playerOverlayRewind.setOnClickListener(player)
        hudBinding.playerOverlayForward.setOnClickListener(player)
        hudBinding.playerOverlayRewind.setOnLongClickListener(player)
        hudBinding.playerOverlayForward.setOnLongClickListener(player)
        hudBinding.playerOverlayRewind.setOnKeyListener(OnRepeatListenerKey(clickListener = player, listenerLifecycle = player.lifecycle))
        hudBinding.playerOverlayForward.setOnKeyListener(OnRepeatListenerKey(clickListener = player, listenerLifecycle = player.lifecycle))
    }

    fun updateOrientationIcon() {
        if (::hudBinding.isInitialized) {
            val drawable = if (!player.orientationMode.locked) {
                R.drawable.ic_player_rotate
            } else if (player.orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || player.orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE || player.orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                R.drawable.ic_player_lock_landscape
            } else {
                R.drawable.ic_player_lock_portrait
            }
            hudBinding.orientationToggle.setImageDrawable(ContextCompat.getDrawable(player, drawable))
        }
        if (::hudRightBinding.isInitialized) {
            if (!player.isLocked && player.orientationMode.locked && Settings.getInstance(player).getBoolean(SHOW_ORIENTATION_BUTTON, true)) {
                val drawable = if (player.orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || player.orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE || player.orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                    R.drawable.ic_player_lock_landscape
                } else {
                    R.drawable.ic_player_lock_portrait
                }
                hudRightBinding.orientationQuickAction.setVisible()
                hudRightBinding.orientationQuickAction.chipIcon = ContextCompat.getDrawable(player, drawable)
            } else hudRightBinding.orientationQuickAction.setGone()
        }
    }

    fun nextOrientation() {
        val orientations = arrayOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
        val orientation = when (player.orientationMode.orientation) {
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> orientations[0]
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> orientations[1]
            else -> orientations[orientations.indexOf(player.orientationMode.orientation) + 1]
        }
        player.setOrientation(orientation)
        val string = when (orientation) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> player.getString(R.string.screen_orientation_portrait)
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> player.getString(R.string.screen_orientation_portrait_reverse)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> player.getString(R.string.screen_orientation_landscape)
            else -> player.getString(R.string.screen_orientation_landscape_reverse)
        }
        showInfo(string, 1000)
        updateOrientationIcon()
        showOverlay()
    }

    fun updateRendererVisibility() {
        if (::hudRightBinding.isInitialized) hudRightBinding.videoRenderer.visibility = if (player.isLocked || RendererDelegate.renderers.value.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private val titleConstraintSetLandscape = ConstraintSet()
    private val titleConstraintSetPortrait = ConstraintSet()
    private fun manageTitleConstraints() {
        titleConstraintSetLandscape.clone(hudRightBinding.hudRightOverlay)
        titleConstraintSetPortrait.clone(hudRightBinding.hudRightOverlay)
        titleConstraintSetPortrait.setMargin(hudRightBinding.playerOverlayTitle.id, ConstraintSet.START, 16.dp)
        titleConstraintSetPortrait.setMargin(hudRightBinding.playerOverlayTitle.id, ConstraintSet.END, 16.dp)
        titleConstraintSetPortrait.connect(hudRightBinding.playerOverlayTitle.id, ConstraintSet.TOP, hudRightBinding.iconBarrier.id, ConstraintSet.BOTTOM, 0.dp)
    }

    fun updateTitleConstraints() {
        if (::hudRightBinding.isInitialized) when (player.resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> titleConstraintSetPortrait
            else -> titleConstraintSetLandscape
        }.applyTo(hudRightBinding.hudRightOverlay)
    }


    fun updateHudMargins() {
        //here, we override the default Android overscan
        val overscanHorizontal = if (player.isTv) 32.dp else 8.dp
        val overscanVertical = if (player.isTv) player.resources.getDimension(R.dimen.tv_overscan_vertical).toInt() else 8.dp
        if (::hudBinding.isInitialized) {
            val largeMargin = player.resources.getDimension(R.dimen.large_margins_center)
            val smallMargin = player.resources.getDimension(R.dimen.small_margins_sides)


            applyMargin(hudBinding.playerOverlayTracks, if (!player.isTv) smallMargin.toInt() else overscanHorizontal, false)
            applyMargin(hudBinding.playerOverlayAdvFunction, if (!player.isTv) smallMargin.toInt() else overscanHorizontal, true)

            hudBinding.playerOverlaySeekbar.setPadding(overscanHorizontal, 0, overscanHorizontal, 0)
            hudBinding.bookmarkMarkerContainer.setPadding(overscanHorizontal, 0, overscanHorizontal, 0)

            if (player.isTv) {
                applyMargin(hudBinding.playerOverlayTime, overscanHorizontal, false)
                applyMargin(hudBinding.playerOverlayLength, overscanHorizontal, true)
            }

            if (player.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                hudBinding.playerSpaceLeft.setGone()
                hudBinding.playerSpaceRight.setGone()
                applyMargin(hudBinding.playerOverlaySeekbar, 0, true)
                applyMargin(hudBinding.playerOverlaySeekbar, 0, false)

                applyMargin(hudBinding.playlistPrevious, 0, true)
                applyMargin(hudBinding.playerOverlayRewind, 0, true)
                applyMargin(hudBinding.playlistNext, 0, false)
                applyMargin(hudBinding.playerOverlayForward, 0, false)
                applyMargin(hudBinding.orientationToggle, 0, false)
                applyMargin(hudBinding.playerResize, 0, true)
            } else {
                hudBinding.playerSpaceLeft.setVisible()
                hudBinding.playerSpaceRight.setVisible()
                applyMargin(hudBinding.playerOverlaySeekbar, 20.dp, true)
                applyMargin(hudBinding.playerOverlaySeekbar, 20.dp, false)

                applyMargin(hudBinding.playlistPrevious, largeMargin.toInt(), true)
                applyMargin(hudBinding.playerOverlayRewind, largeMargin.toInt(), true)
                applyMargin(hudBinding.playlistNext, largeMargin.toInt(), false)
                applyMargin(hudBinding.playerOverlayForward, largeMargin.toInt(), false)
                applyMargin(hudBinding.orientationToggle, smallMargin.toInt(), false)
                applyMargin(hudBinding.playerResize, smallMargin.toInt(), true)
            }
        }
        if (::hudRightBinding.isInitialized) {
            applyVerticalMargin(hudRightBinding.playerOverlayTitle, overscanVertical)
        }
    }

    private fun applyMargin(view: View, margin: Int, isEnd: Boolean) = (view.layoutParams as ConstraintLayout.LayoutParams).apply {
        if (isEnd) marginEnd = margin else marginStart = margin
        view.layoutParams = this
    }

    private fun applyVerticalMargin(view: View, margin: Int) = (view.layoutParams as ConstraintLayout.LayoutParams).apply {
        bottomMargin = margin
        view.layoutParams = this
    }

    fun updateScreenshotButton() {
        hudRightBinding.playerScreenshot.visibility =  if (!player.isLocked && Settings.getInstance(player).getString(SCREENSHOT_MODE, "0") in arrayOf("1", "3")) View.VISIBLE else View.GONE
        hudRightBinding.playerScreenshot.setOnClickListener(player)
    }

    private fun initPlaylistUi() {
        if (!::playlistAdapter.isInitialized) {
            playlistAdapter = PlaylistAdapter(player)
            val layoutManager = LinearLayoutManager(player, RecyclerView.VERTICAL, false)
            playlist.layoutManager = layoutManager
        }
        if (player.playlistModel == null) {
            player.playlistModel = ViewModelProvider(player)[PlaylistModel::class.java].apply {
                playlistAdapter.setModel(this)
                dataset.observe(player, player.playlistObserver)
            }
        }
        if (player.service?.hasPlaylist() == true) {
            hudRightBinding.playlistToggle.setVisible()
            if (::hudBinding.isInitialized) {
                hudBinding.playlistPrevious.setVisible()
                hudBinding.playlistNext.setVisible()
            }
        } else hudRightBinding.playlistToggle.setGone()
        hudRightBinding.playlistToggle.setOnClickListener(player)
        closeButton.setOnClickListener { togglePlaylist() }
        hingeArrowLeft?.setOnClickListener {
            Settings.getInstance(player).putSingle(HINGE_ON_RIGHT, false)
            manageHinge()
            showOverlay()
        }
        hingeArrowRight?.setOnClickListener {
            Settings.getInstance(player).putSingle(HINGE_ON_RIGHT, true)
            manageHinge()
            showOverlay()
        }

        val callback = SwipeDragItemTouchHelperCallback(playlistAdapter, true)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(playlist)
    }

    fun togglePlaylist() {
        if (player.isPlaylistVisible) {
            playlistContainer.setGone()
            playlist.setOnClickListener(null)
            UiTools.setKeyboardVisibility(playlistContainer, false)
            return
        }
        hideOverlay(true)
        playlistContainer.setVisible()
        playlist.adapter = playlistAdapter
        player.onSelectionSet(playlistAdapter.currentIndex)
        player.update()
        if (player.isTalkbackIsEnabled()) playlistSearchText.editText?.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    fun showControls(show: Boolean) {
        if (show && player.isInPictureInPictureMode) return
        if (::hudBinding.isInitialized) {
            hudBinding.playerOverlayPlay.visibility = if (show) View.VISIBLE else View.INVISIBLE
            if (seekButtons) {
                hudBinding.playerOverlayRewind.visibility = if (show) View.VISIBLE else View.INVISIBLE
                hudBinding.playerOverlayRewindText.text = "${Settings.videoJumpDelay}"
                hudBinding.playerOverlayRewind.contentDescription = player.getString(R.string.talkback_action_rewind, Settings.videoJumpDelay.toString())
                hudBinding.playerOverlayRewindText.visibility = if (show) View.VISIBLE else View.INVISIBLE
                hudBinding.playerOverlayForward.visibility = if (show) View.VISIBLE else View.INVISIBLE
                hudBinding.playerOverlayForwardText.text = "${Settings.videoJumpDelay}"
                hudBinding.playerOverlayForward.contentDescription = player.getString(R.string.talkback_action_forward, Settings.videoJumpDelay.toString())
                hudBinding.playerOverlayForwardText.visibility = if (show) View.VISIBLE else View.INVISIBLE
            }
            hudBinding.playerOverlayTracks.visibility = if (show) View.VISIBLE else View.INVISIBLE
            hudBinding.playerOverlayAdvFunction.visibility = if (show) View.VISIBLE else View.INVISIBLE
            hudBinding.playerResize.visibility = if (show) View.VISIBLE else View.INVISIBLE
            if (hasPlaylist) {
                hudBinding.playlistPrevious.visibility = if (show) View.VISIBLE else View.INVISIBLE
                hudBinding.playlistNext.visibility = if (show) View.VISIBLE else View.INVISIBLE
            }
            hudBinding.orientationToggle.visibility = if (player.isTv || AndroidDevices.isChromeBook) View.INVISIBLE else if (show) View.VISIBLE else View.INVISIBLE
            if (!show) hudBinding.playerOverlaySeekbar.disableAccessibilityEvents() else hudBinding.playerOverlaySeekbar.enableAccessibilityEvents()
        }
        if (::hudRightBinding.isInitialized) {
            val secondary = player.displayManager.isSecondary
            if (secondary) hudRightBinding.videoSecondaryDisplay.setImageResource(R.drawable.ic_player_screenshare_stop)
            hudRightBinding.videoSecondaryDisplay.visibility = if (!show) View.GONE else if (UiTools.hasSecondaryDisplay(player.applicationContext)) View.VISIBLE else View.GONE
            hudRightBinding.videoSecondaryDisplay.contentDescription = player.resources.getString(if (secondary) R.string.video_remote_disable else R.string.video_remote_enable)

            hudRightBinding.playlistToggle.visibility = if (show && player.service?.hasPlaylist() == true) View.VISIBLE else View.GONE
            hudRightBinding.playerScreenshot.visibility = if (!player.isLocked && Settings.getInstance(player).getString(SCREENSHOT_MODE, "0") in arrayOf("1", "3")) View.VISIBLE else View.GONE
            hudRightBinding.playerOverlayNavmenu.visibility = if (player.menuIdx >= 0) View.VISIBLE else View.GONE
            hudRightBinding.sleepQuickAction.visibility = if (show && PlaybackService.playerSleepTime.value != null) View.VISIBLE else View.GONE


            hudRightBinding.spuDelayQuickAction.visibility = if (show && player.service?.spuDelay != 0L) View.VISIBLE else View.GONE
            hudRightBinding.audioDelayQuickAction.visibility = if (show && player.service?.audioDelay != 0L) View.VISIBLE else View.GONE
            hudRightBinding.clock.visibility = if (Settings.showTvUi) View.VISIBLE else View.GONE

            hudRightBinding.playbackSpeedQuickAction.visibility = if (show && player.service?.rate != 1.0F) View.VISIBLE else View.GONE
            updatePlaybackSpeedChip()
            val format =  DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
            PlaybackService.playerSleepTime.value?.let {
                hudRightBinding.sleepQuickAction.text = format.format(it.time)
                hudRightBinding.sleepQuickAction.contentDescription = player.getString(R.string.sleep_in) + TalkbackUtil.millisToString(player, System.currentTimeMillis() - it.time.time)
            }
            hudRightBinding.spuDelayQuickAction.text = "${(player.service?.spuDelay ?: 0L) / 1000L} ms"
            hudRightBinding.audioDelayQuickAction.text = "${(player.service?.audioDelay ?: 0L) / 1000L} ms"

        }

    }

    fun updatePlaybackSpeedChip() {
        if (::hudRightBinding.isInitialized) {
            hudRightBinding.playbackSpeedQuickAction.text = player.service?.rate?.formatRateString()
            hudRightBinding.playbackSpeedQuickAction.contentDescription = player.getString(R.string.playback_speed) + ". " + player.service?.rate?.formatRateString()
            if (player.service?.rate == 1.0F) hudRightBinding.playbackSpeedQuickAction.setGone()
            if (Settings.getInstance(player).getBoolean(KEY_PLAYBACK_SPEED_VIDEO_GLOBAL, false)) {
                hudRightBinding.playbackSpeedQuickAction.chipIcon = ContextCompat.getDrawable(player, R.drawable.ic_speed_all)
            } else {
                hudRightBinding.playbackSpeedQuickAction.chipIcon = ContextCompat.getDrawable(player, R.drawable.ic_speed)
            }
        }
    }

    /**
     * hider overlay
     */
    fun hideOverlay(fromUser: Boolean, forceTalkback: Boolean = false) {
        if (!fromUser && (player.isTalkbackIsEnabled() && !forceTalkback)) return
        if (player.isShowing) {
            if (isBookmarkShown()) hideBookmarks()
            player.handler.removeMessages(VideoPlayerActivity.FADE_OUT)
            if (!player.displayManager.isPrimary) {
                overlayBackground?.startAnimation(AnimationUtils.loadAnimation(player, android.R.anim.fade_out))
                overlayBackground.setInvisible()
            }

            exitAnimate(arrayOf(hudBinding.progressOverlay, hudBackground),100.dp.toFloat())
            exitAnimate(arrayOf(hudRightBinding.hudRightOverlay, hudRightBackground),-100.dp.toFloat())
            hingeArrowLeft?.animate()?.alpha(0F)
            hingeArrowRight?.animate()?.alpha(0F)

            showControls(false)
            player.isShowing = false
            dimStatusBar(true)
            playlistSearchText.editText?.setText("")
        } else if (!fromUser) {
            /*
             * Try to hide the Nav Bar again.
             * It seems that you can't hide the Nav Bar if you previously
             * showed it in the last 1-2 seconds.
             */
            dimStatusBar(true)
        }
    }

    fun focusPlayPause() {
        if (::hudBinding.isInitialized) hudBinding.playerOverlayPlay.requestFocus()
    }

    fun toggleOverlay() {
        if (!player.isShowing) showOverlay()
        else hideOverlay(true)
    }

    /**
     * Lock player
     */
    fun lockScreen() {
        orientationLockedBeforeLock = player.orientationMode.locked
        if (!player.orientationMode.locked) player.toggleOrientationLock()
        if (isHudBindingInitialized()) {
            hudBinding.playerOverlayTime.isEnabled = false
            hudBinding.playerOverlaySeekbar.isEnabled = false
            hudBinding.playerOverlayLength.isEnabled = false
            hudBinding.playlistNext.isEnabled = false
            hudBinding.playlistPrevious.isEnabled = false
            hudBinding.swipeToUnlock.setVisible()
            //make sure the title and unlock views are not conflicting with the cutout / gestures
            (playerUiContainer.layoutParams as? FrameLayout.LayoutParams)?.let {
                if (AndroidUtil.isPOrLater) {
                    it.topMargin =
                        player.window.decorView.rootWindowInsets.displayCutout?.safeInsetTop ?: 0
                    it.bottomMargin =
                        (player.window.decorView.rootWindowInsets.displayCutout?.safeInsetBottom
                            ?: 0) + 24.dp
                } else {
                    it.topMargin = 0
                    it.bottomMargin = 24.dp
                }
            }

        }
        hideOverlay(true)
        player.lockBackButton = true
        player.isLocked = true
        updateOrientationIcon()
        updateScreenshotButton()
    }

    /**
     * Remove player lock
     */
    fun unlockScreen() {
        (playerUiContainer.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.topMargin = 0
            it.bottomMargin = 0
        }
        player.orientationMode.locked = orientationLockedBeforeLock
        player.requestedOrientation = player.getScreenOrientation(player.orientationMode)
        if (isHudBindingInitialized()) {
            hudBinding.playerOverlayTime.isEnabled = true
            hudBinding.playerOverlaySeekbar.isEnabled = player.service?.isSeekable != false
            hudBinding.playerOverlayLength.isEnabled = true
            hudBinding.playlistNext.isEnabled = true
            hudBinding.playlistPrevious.isEnabled = true
        }
        player.isShowing = false
        player.isLocked = false
        showOverlay()
        updateOrientationIcon()
        updateScreenshotButton()
        player.lockBackButton = false
    }

    private fun pickSubtitles() {
        val uri = player.videoUri ?: return
        val media = if (uri.scheme.isSchemeFile() || uri.scheme.isSchemeNetwork()) MediaWrapperImpl(FileUtils.getParent(uri.toString())!!.toUri()) else null
        player.isShowingDialog = true
        val filePickerIntent = Intent(player, FilePickerActivity::class.java)
        filePickerIntent.putExtra(KEY_MEDIA, media)
        player.startActivityForResult(filePickerIntent, 0)

    }

    private fun downloadSubtitles() = player.service?.currentMediaWrapper?.let {
        MediaUtils.getSubs(player, it)
    }

    fun showBookmarks() {
        player.service?.let {
            if (bookmarkListDelegate == null) {
                bookmarkListDelegate = BookmarkListDelegate(player, it, player.bookmarkModel, true)
                bookmarkListDelegate?.markerContainer = hudBinding.bookmarkMarkerContainer
                bookmarkListDelegate?.visibilityListener = {
                    if (bookmarkListDelegate?.visible == true) showOverlayTimeout(VideoPlayerActivity.OVERLAY_INFINITE)
                    else showOverlayTimeout(Settings.videoHudDelay * 1000)
                }
                bookmarkListDelegate?.seekListener = { forward, long ->
                    player.jump(forward, long)
                }
            }
            bookmarkListDelegate?.show()
            val top = hudBinding.playerOverlayTime.top
            bookmarkListDelegate?.setProgressHeight((top + 12.dp).toFloat())
        }
    }

    fun rotateBookmarks() {
        if (bookmarkListDelegate != null && isBookmarkShown()) {
            //make sure the rotation is complete and layout is done before resetting the bookmarks' layout
            hudBinding.progressOverlay.post {
                bookmarkListDelegate?.hide()
                showBookmarks()
            }
        }
    }

    fun isBookmarkShown() = bookmarkListDelegate != null && bookmarkListDelegate?.visible == true
    fun hideBookmarks() {
        bookmarkListDelegate?.hide()
    }

    fun getOverlayBrightness() = if (::playerOverlayBrightness.isInitialized) playerOverlayBrightness else null

    fun getOverlayVolume() = if (::playerOverlayVolume.isInitialized) playerOverlayVolume else null
    fun onDestroy() {
        bookmarkListDelegate = null
    }
}