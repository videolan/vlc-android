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
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.ViewStubCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaWrapperImpl
import org.videolan.resources.AndroidDevices
import org.videolan.tools.*
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.RendererDelegate
import org.videolan.vlc.databinding.PlayerHudBinding
import org.videolan.vlc.databinding.PlayerHudRightBinding
import org.videolan.vlc.gui.audio.PlaylistAdapter
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.browser.KEY_MEDIA
import org.videolan.vlc.gui.dialogs.VideoTracksDialog
import org.videolan.vlc.gui.helpers.OnRepeatListener
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.showVideoTrack
import org.videolan.vlc.gui.view.PlayerProgress
import org.videolan.vlc.manageAbRepeatStep
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.viewmodels.PlaylistModel

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class VideoPlayerOverlayDelegate (private val player: VideoPlayerActivity) {

    private lateinit var playerOverlayBrightness: ConstraintLayout
    private lateinit var brightnessValueText: TextView
    private lateinit var playerBrightnessProgress: PlayerProgress
    private lateinit var playerOverlayVolume: ConstraintLayout
    private lateinit var volumeValueText: TextView
    private lateinit var playerVolumeProgress: PlayerProgress
    var info: TextView? = null
    var overlayInfo: View? = null
    lateinit var playerUiContainer:RelativeLayout

    lateinit var hudBinding: PlayerHudBinding
    lateinit var hudRightBinding: PlayerHudRightBinding
    private var overlayBackground: View? = null


    private var overlayTimeout = 0
    private var wasPlaying = true

    lateinit var playToPause: AnimatedVectorDrawableCompat
    lateinit var pauseToPlay: AnimatedVectorDrawableCompat

    private val hudBackground: View by lazy { player.findViewById<View>(R.id.hud_background) }
    private val hudRightBackground: View by lazy { player.findViewById<View>(R.id.hud_right_background) }

    private lateinit var abRepeatAddMarker: Button

    private var seekButtons: Boolean = false
    var hasPlaylist: Boolean = false

    var enableSubs = true

    fun isHudBindingInitialized() = ::hudBinding.isInitialized
    fun isHudRightBindingInitialized() = ::hudRightBinding.isInitialized
    fun isPlaylistAdapterInitialized() = ::playlistAdapter.isInitialized

    private var orientationLockedBeforeLock: Boolean = false
    lateinit var closeButton: View
    lateinit var playlistContainer: View
    lateinit var playlist: RecyclerView
    lateinit var playlistSearchText: TextInputLayout
    lateinit var playlistAdapter: PlaylistAdapter

    fun showTracks() {
        player.showVideoTrack(
                {
                    when (it) {
                        R.id.audio_track_delay -> player.delayDelegate.showAudioDelaySetting()
                        R.id.subtitle_track_delay -> player.delayDelegate.showSubsDelaySetting()
                        R.id.subtitle_track_download -> downloadSubtitles()
                        R.id.subtitle_track_file -> pickSubtitles()
                    }
                }, { trackID: Int, trackType: VideoTracksDialog.TrackType ->
            when (trackType) {
                VideoTracksDialog.TrackType.AUDIO -> {
                    player.service?.let { service ->
                        service.setAudioTrack(trackID)
                        runIO(Runnable {
                            val mw = player.medialibrary.findMedia(service.currentMediaWrapper)
                            if (mw != null && mw.id != 0L) mw.setLongMeta(MediaWrapper.META_AUDIOTRACK, trackID.toLong())
                        })
                    }
                }
                VideoTracksDialog.TrackType.SPU -> player.service?.setSpuTrack(trackID)
                VideoTracksDialog.TrackType.VIDEO -> {
                    player.service?.let { service ->
                        player.seek(service.time)
                        service.setVideoTrack(trackID)
                        runIO(Runnable {
                            val mw = player.medialibrary.findMedia(service.currentMediaWrapper)
                            if (mw != null && mw.id != 0L) mw.setLongMeta(MediaWrapper.META_VIDEOTRACK, trackID.toLong())
                        })
                    }
                }
            }
        })
    }

    fun showInfo(@StringRes textId: Int, duration: Int) {
        showInfo(player.getString(textId), duration)
    }

    /**
     * Show text in the info view for "duration" milliseconds
     * @param text
     * @param duration
     */
    fun showInfo(text: String, duration: Int) {
        if (player.isInPictureInPictureMode) return
        initInfoOverlay()
        overlayInfo.setVisible()
        info.setVisible()
        info?.text = text
        player.handler.removeMessages(VideoPlayerActivity.FADE_OUT_INFO)
        player.handler.sendEmptyMessageDelayed(VideoPlayerActivity.FADE_OUT_INFO, duration.toLong())
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
    fun showVolumeBar(volume: Int, fromTouch: Boolean) {
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
            if (AndroidUtil.isKitKatOrLater) visibility = visibility or View.SYSTEM_UI_FLAG_IMMERSIVE
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
            if (player.isInPictureInPictureMode) return
            initOverlay()
            if (!::hudBinding.isInitialized) return
            overlayTimeout = when {
                timeout != 0 -> timeout
                service.isPlaying -> VideoPlayerActivity.OVERLAY_TIMEOUT
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
                dimStatusBar(false)

                enterAnimate(arrayOf(hudBinding.progressOverlay, hudBackground), 100.dp.toFloat())
                enterAnimate(arrayOf(hudRightBinding.hudRightOverlay, hudRightBackground), -100.dp.toFloat())

                if (!player.displayManager.isPrimary)
                    overlayBackground.setVisible()
                updateOverlayPausePlay(true)
            }
            player.handler.removeMessages(VideoPlayerActivity.FADE_OUT)
            if (overlayTimeout != VideoPlayerActivity.OVERLAY_INFINITE)
                player.handler.sendMessageDelayed(player.handler.obtainMessage(VideoPlayerActivity.FADE_OUT), overlayTimeout.toLong())
        }
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

                wasPlaying = service.isPlaying
            }
            hudBinding.playerOverlayPlay.requestFocus()
            if (::playlistAdapter.isInitialized) {
                playlistAdapter.setCurrentlyPlaying(service.isPlaying)
            }
        }
    }

    private fun enterAnimate(views: Array<View?>, translationStart: Float) = views.forEach { view ->
        view.setVisible()
        view?.alpha = 0f
        view?.translationY = translationStart
        view?.animate()?.alpha(1F)?.translationY(0F)?.setDuration(150L)?.setListener(null)
    }

    private fun exitAnimate(views: Array<View?>, translationEnd: Float) = views.forEach { view ->
        view?.animate()?.alpha(0F)?.translationY(translationEnd)?.setDuration(150L)?.setListener(object : Animator.AnimatorListener {
            override fun onAnimationEnd(animation: Animator?) {
                view.setInvisible()
            }
            override fun onAnimationCancel(animation: Animator?) {}
            override fun onAnimationRepeat(animation: Animator?) {}
            override fun onAnimationStart(animation: Animator?) {}
        })
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun initOverlay() {
        player.service?.let { service ->
            val vscRight = player.findViewById<ViewStubCompat>(R.id.player_hud_right_stub)
            vscRight?.let {
                it.setVisible()
                hudRightBinding = DataBindingUtil.bind(player.findViewById(R.id.hud_right_overlay)) ?: return
                if (!player.isBenchmark && player.enableCloneMode && !player.settings.contains("enable_clone_mode")) {
                    UiTools.snackerConfirm(hudRightBinding.videoSecondaryDisplay, player.getString(R.string.video_save_clone_mode), Runnable { player.settings.putSingle("enable_clone_mode", true) })
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
                service.playlistManager.abRepeat.observe(player, Observer { abvalues ->
                    hudBinding.abRepeatA = if (abvalues.start == -1L) -1F else abvalues.start / service.playlistManager.player.getLength().toFloat()
                    hudBinding.abRepeatB = if (abvalues.stop == -1L) -1F else abvalues.stop / service.playlistManager.player.getLength().toFloat()
                    hudBinding.abRepeatMarkerA.visibility = if (abvalues.start == -1L) View.GONE else View.VISIBLE
                    hudBinding.abRepeatMarkerB.visibility = if (abvalues.stop == -1L) View.GONE else View.VISIBLE
                    service.manageAbRepeatStep(hudBinding.abRepeatReset, hudBinding.abRepeatStop, hudBinding.abRepeatContainer, abRepeatAddMarker)
                    if (player.settings.getBoolean(VIDEO_TRANSITION_SHOW, true)) showOverlayTimeout(if (abvalues.start == -1L || abvalues.stop == -1L) VideoPlayerActivity.OVERLAY_INFINITE else VideoPlayerActivity.OVERLAY_TIMEOUT)
                })
                service.playlistManager.abRepeatOn.observe(player, Observer {
                    abRepeatAddMarker.visibility = if (it) View.VISIBLE else View.GONE
                    hudBinding.abRepeatMarkerGuidelineContainer.visibility = if (it) View.VISIBLE else View.GONE
                    if (it) showOverlay(true)
                    if (it) {
                        hudBinding.playerOverlayLength.nextFocusUpId = R.id.ab_repeat_add_marker
                        hudBinding.playerOverlayTime.nextFocusUpId = R.id.ab_repeat_add_marker
                    }
                    if (it) showOverlayTimeout(VideoPlayerActivity.OVERLAY_INFINITE)

                    service.manageAbRepeatStep(hudBinding.abRepeatReset, hudBinding.abRepeatStop, hudBinding.abRepeatContainer, abRepeatAddMarker)
                })
                service.playlistManager.delayValue.observe(player, Observer {
                    player.delayDelegate.delayChanged(it, service)
                })
                service.playlistManager.videoStatsOn.observe(player, Observer {
                    if (it) showOverlay(true)
                    player.statsDelegate.container = hudBinding.statsContainer
                    player.statsDelegate.initPlotView(hudBinding)
                    if (it) player.statsDelegate.start() else player.statsDelegate.stop()
                })
                hudBinding.statsClose.setOnClickListener { service.playlistManager.videoStatsOn.postValue(false) }

                hudBinding.lifecycleOwner = player
                updateOrientationIcon()
                overlayBackground = player.findViewById(R.id.player_overlay_background)
                if (!AndroidDevices.isChromeBook && !player.isTv
                        && player.settings.getBoolean("enable_casting", true)) {
                    PlaybackService.renderer.observe(player, Observer { rendererItem -> hudRightBinding.videoRenderer.setImageDrawable(AppCompatResources.getDrawable(player, if (rendererItem == null) R.drawable.ic_player_renderer else R.drawable.ic_player_renderer_on)) })
                    RendererDelegate.renderers.observe(player, Observer<List<RendererItem>> { rendererItems -> updateRendererVisibility() })
                }

                hudRightBinding.playerOverlayTitle.text = service.currentMediaWrapper?.title
                manageTitleConstraints()
                updateTitleConstraints()
                updateHudMargins()

                if (seekButtons) initSeekButton()


                resetHudLayout()
                updateOverlayPausePlay(true)
                updateSeekable(service.isSeekable)
                updatePausable(service.isPausable)
                player.updateNavStatus()
                setListeners(true)
                initPlaylistUi()
            } else if (::hudBinding.isInitialized) {
                hudBinding.progress = service.playlistManager.player.progress
                hudBinding.lifecycleOwner = player
            }
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
            abRepeatAddMarker.setOnClickListener(player)
            hudBinding.orientationToggle.setOnClickListener(if (enabled) player else null)
            hudBinding.orientationToggle.setOnLongClickListener(if (enabled) player else null)
            hudBinding.swipeToUnlock.setOnStartTouchingListener { showOverlayTimeout(VideoPlayerActivity.OVERLAY_INFINITE) }
            hudBinding.swipeToUnlock.setOnStopTouchingListener { showOverlayTimeout(VideoPlayerActivity.OVERLAY_TIMEOUT) }
            hudBinding.swipeToUnlock.setOnUnlockListener { player.toggleLock() }
        }
        if (::hudRightBinding.isInitialized){
            hudRightBinding.playerOverlayNavmenu.setOnClickListener(if (enabled) player else null)
            UiTools.setViewOnClickListener(hudRightBinding.videoRenderer, if (enabled) player else null)
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
        hudBinding.playerOverlayRewind.setOnTouchListener(OnRepeatListener(player))
        hudBinding.playerOverlayForward.setOnTouchListener(OnRepeatListener(player))
    }

    fun updateOrientationIcon() {
        if (::hudBinding.isInitialized) {
            val drawable = if (!player.orientationMode.locked) {
                R.drawable.ic_player_rotate
            } else if (player.orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || player.orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                R.drawable.ic_player_lock_landscape
            } else {
                R.drawable.ic_player_lock_portrait
            }
            hudBinding.orientationToggle.setImageDrawable(ContextCompat.getDrawable(player, drawable))
        }
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
            applyMargin(hudBinding.playlistPrevious, largeMargin.toInt(), true)
            applyMargin(hudBinding.playerOverlayRewind, largeMargin.toInt(), true)
            applyMargin(hudBinding.playlistNext, largeMargin.toInt(), false)
            applyMargin(hudBinding.playerOverlayForward, largeMargin.toInt(), false)

            applyMargin(hudBinding.playerOverlayTracks, if (!player.isTv) smallMargin.toInt() else overscanHorizontal, false)
            applyMargin(hudBinding.orientationToggle, smallMargin.toInt(), false)
            applyMargin(hudBinding.playerResize, smallMargin.toInt(), true)
            applyMargin(hudBinding.playerOverlayAdvFunction, if (!player.isTv) smallMargin.toInt() else overscanHorizontal, true)

            hudBinding.playerOverlaySeekbar.setPadding(overscanHorizontal, 0, overscanHorizontal, 0)

            if (player.isTv) {
                applyMargin(hudBinding.playerOverlayTimeContainer, overscanHorizontal, false)
                applyMargin(hudBinding.playerOverlayLengthContainer, overscanHorizontal, true)
            }

            if (player.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                hudBinding.playerSpaceLeft.setGone()
                hudBinding.playerSpaceRight.setGone()
                applyMargin(hudBinding.playerOverlaySeekbar, 0, true)
                applyMargin(hudBinding.playerOverlaySeekbar, 0, false)
            } else {
                hudBinding.playerSpaceLeft.setVisible()
                hudBinding.playerSpaceRight.setVisible()
                applyMargin(hudBinding.playerOverlaySeekbar, 20.dp, true)
                applyMargin(hudBinding.playerOverlaySeekbar, 20.dp, false)
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

    private fun initPlaylistUi() {
        if (player.service?.hasPlaylist() == true) {
            if (!::playlistAdapter.isInitialized) {
                playlistAdapter = PlaylistAdapter(player)
                val layoutManager = LinearLayoutManager(player, RecyclerView.VERTICAL, false)
                playlist.layoutManager = layoutManager
            }
            if (player.playlistModel == null) {
                player.playlistModel = ViewModelProvider(player).get(PlaylistModel::class.java).apply {
                    playlistAdapter.setModel(this)
                    dataset.observe(player, player.playlistObserver)
                }
            }
            hudRightBinding.playlistToggle.setVisible()
            if (::hudBinding.isInitialized) {
                hudBinding.playlistPrevious.setVisible()
                hudBinding.playlistNext.setVisible()
            }
            hudRightBinding.playlistToggle.setOnClickListener(player)
            closeButton.setOnClickListener { togglePlaylist() }

            val callback = SwipeDragItemTouchHelperCallback(playlistAdapter, true)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(playlist)
        }
    }

    fun togglePlaylist() {
        if (player.isPlaylistVisible) {
            playlistContainer.setGone()
            playlist.setOnClickListener(null)
            return
        }
        hideOverlay(true)
        playlistContainer.setVisible()
        playlist.adapter = playlistAdapter
        player.update()
    }

    fun showControls(show: Boolean) {
        if (show && player.isInPictureInPictureMode) return
        if (::hudBinding.isInitialized) {
            hudBinding.playerOverlayPlay.visibility = if (show) View.VISIBLE else View.INVISIBLE
            if (seekButtons) {
                hudBinding.playerOverlayRewind.visibility = if (show) View.VISIBLE else View.INVISIBLE
                hudBinding.playerOverlayForward.visibility = if (show) View.VISIBLE else View.INVISIBLE
            }
            hudBinding.playerOverlayTracks.visibility = if (show) View.VISIBLE else View.INVISIBLE
            hudBinding.playerOverlayAdvFunction.visibility = if (show) View.VISIBLE else View.INVISIBLE
            hudBinding.playerResize.visibility = if (show) View.VISIBLE else View.INVISIBLE
            if (hasPlaylist) {
                hudBinding.playlistPrevious.visibility = if (show) View.VISIBLE else View.INVISIBLE
                hudBinding.playlistNext.visibility = if (show) View.VISIBLE else View.INVISIBLE
            }
            hudBinding.orientationToggle.visibility = if (player.isTv || AndroidDevices.isChromeBook) View.INVISIBLE else if (show) View.VISIBLE else View.INVISIBLE
        }
        if (::hudRightBinding.isInitialized) {
            val secondary = player.displayManager.isSecondary
            if (secondary) hudRightBinding.videoSecondaryDisplay.setImageResource(R.drawable.ic_player_screenshare_stop)
            hudRightBinding.videoSecondaryDisplay.visibility = if (!show) View.GONE else if (UiTools.hasSecondaryDisplay(player.applicationContext)) View.VISIBLE else View.GONE
            hudRightBinding.videoSecondaryDisplay.contentDescription = player.resources.getString(if (secondary) R.string.video_remote_disable else R.string.video_remote_enable)

            hudRightBinding.playlistToggle.visibility = if (show && player.service?.hasPlaylist() == true) View.VISIBLE else View.GONE
        }
    }

    /**
     * hider overlay
     */
    fun hideOverlay(fromUser: Boolean) {
        if (player.isShowing) {
            player.handler.removeMessages(VideoPlayerActivity.FADE_OUT)
            player.overlayTips.setInvisible()
            if (!player.displayManager.isPrimary) {
                overlayBackground?.startAnimation(AnimationUtils.loadAnimation(player, android.R.anim.fade_out))
                overlayBackground.setInvisible()
            }

            exitAnimate(arrayOf(hudBinding.progressOverlay, hudBackground),100.dp.toFloat())
            exitAnimate(arrayOf(hudRightBinding.hudRightOverlay, hudRightBackground),-100.dp.toFloat())

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
        if (!player.orientationMode.locked) player.toggleOrientation()
        if (isHudBindingInitialized()) {
            hudBinding.playerOverlayTime.isEnabled = false
            hudBinding.playerOverlaySeekbar.isEnabled = false
            hudBinding.playerOverlayLength.isEnabled = false
            hudBinding.playlistNext.isEnabled = false
            hudBinding.playlistPrevious.isEnabled = false
            hudBinding.swipeToUnlock.setVisible()
        }
        hideOverlay(true)
        player.lockBackButton = true
        player.isLocked = true
    }

    /**
     * Remove player lock
     */
    fun unlockScreen() {
        player.orientationMode.locked = orientationLockedBeforeLock
        player.requestedOrientation = player.getScreenOrientation(player.orientationMode)
        if (isHudBindingInitialized()) {
            hudBinding.playerOverlayTime.isEnabled = true
            hudBinding.playerOverlaySeekbar.isEnabled = player.service?.isSeekable != false
            hudBinding.playerOverlayLength.isEnabled = true
            hudBinding.playlistNext.isEnabled = true
            hudBinding.playlistPrevious.isEnabled = true
        }
        updateOrientationIcon()
        player.isShowing = false
        player.isLocked = false
        showOverlay()
        player.lockBackButton = false
    }

    private fun pickSubtitles() {
        val uri = player.videoUri ?: return
        val media = MediaWrapperImpl(FileUtils.getParent(uri.toString())!!.toUri())
        player.isShowingDialog = true
        val filePickerIntent = Intent(player, FilePickerActivity::class.java)
        filePickerIntent.putExtra(KEY_MEDIA, media)
        player.startActivityForResult(filePickerIntent, 0)

    }

    private fun downloadSubtitles() = player.service?.currentMediaWrapper?.let {
        MediaUtils.getSubs(player, it)
    }
}