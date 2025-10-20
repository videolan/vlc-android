/*****************************************************************************
 * VideoPlayerActivity.java
 *
 * Copyright © 2011-2017 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.video

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.KeyguardManager
import android.app.PictureInPictureParams
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Parcelable
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.RotateAnimation
import android.view.inputmethod.BaseInputConnection
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.BaseContextWrappingDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.databinding.BindingAdapter
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.Dialog
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.libvlc.util.DisplayManager
import org.videolan.libvlc.util.VLCVideoLayout
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Bookmark
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.EXIT_PLAYER
import org.videolan.resources.MOBILE_MAIN_ACTIVITY
import org.videolan.resources.PLAYLIST_TYPE_ALL
import org.videolan.resources.PLAY_DISABLE_HARDWARE
import org.videolan.resources.PLAY_EXTRA_FROM_START
import org.videolan.resources.PLAY_EXTRA_ITEM_LOCATION
import org.videolan.resources.PLAY_EXTRA_ITEM_TITLE
import org.videolan.resources.PLAY_EXTRA_OPENED_POSITION
import org.videolan.resources.PLAY_EXTRA_START_TIME
import org.videolan.resources.PLAY_EXTRA_SUBTITLES_LOCATION
import org.videolan.resources.PLAY_FROM_SERVICE
import org.videolan.resources.PLAY_FROM_VIDEOGRID
import org.videolan.resources.TV_AUDIOPLAYER_ACTIVITY
import org.videolan.resources.buildPkgString
import org.videolan.resources.util.parcelable
import org.videolan.resources.util.parcelableList
import org.videolan.tools.BRIGHTNESS_VALUE
import org.videolan.tools.DISPLAY_UNDER_NOTCH
import org.videolan.tools.ENABLE_BRIGHTNESS_GESTURE
import org.videolan.tools.ENABLE_DOUBLE_TAP_PLAY
import org.videolan.tools.ENABLE_DOUBLE_TAP_SEEK
import org.videolan.tools.ENABLE_FASTPLAY
import org.videolan.tools.ENABLE_SCALE_GESTURE
import org.videolan.tools.ENABLE_SEEK_BUTTONS
import org.videolan.tools.ENABLE_SWIPE_SEEK
import org.videolan.tools.ENABLE_VOLUME_GESTURE
import org.videolan.tools.KEY_AUDIO_BOOST
import org.videolan.tools.KEY_AUDIO_PREFERRED_LANGUAGE
import org.videolan.tools.KEY_ENABLE_CLONE_MODE
import org.videolan.tools.KEY_SUBTITLE_PREFERRED_LANGUAGE
import org.videolan.tools.KEY_VIDEO_APP_SWITCH
import org.videolan.tools.KEY_VIDEO_CONFIRM_RESUME
import org.videolan.tools.KEY_VIDEO_MATCH_FRAME_RATE
import org.videolan.tools.LAST_LOCK_ORIENTATION
import org.videolan.tools.LOCK_USE_SENSOR
import org.videolan.tools.POPUP_FORCE_LEGACY
import org.videolan.tools.PREF_TIPS_SHOWN
import org.videolan.tools.SAVE_BRIGHTNESS
import org.videolan.tools.SCREENSHOT_MODE
import org.videolan.tools.SCREEN_ORIENTATION
import org.videolan.tools.Settings
import org.videolan.tools.VIDEO_PAUSED
import org.videolan.tools.VIDEO_RATIO
import org.videolan.tools.VIDEO_RESUME_TIME
import org.videolan.tools.VIDEO_RESUME_URI
import org.videolan.tools.VIDEO_TRANSITION_SHOW
import org.videolan.tools.dp
import org.videolan.tools.getContextWithLocale
import org.videolan.tools.isStarted
import org.videolan.tools.isVisible
import org.videolan.tools.putSingle
import org.videolan.tools.setGone
import org.videolan.tools.setInvisible
import org.videolan.tools.setVisible
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.VlcMigrationHelper
import org.videolan.vlc.getAllTracks
import org.videolan.vlc.getSelectedVideoTrack
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.HeaderMediaListActivity
import org.videolan.vlc.gui.HeaderMediaListActivity.Companion.ARTIST_FROM_ALBUM
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.audio.PlaylistAdapter
import org.videolan.vlc.gui.browser.EXTRA_MRL
import org.videolan.vlc.gui.dialogs.CONFIRM_BOOKMARK_RENAME_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.EqualizerFragmentDialog
import org.videolan.vlc.gui.dialogs.PlaybackSpeedDialog
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_MEDIA
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_NEW_NAME
import org.videolan.vlc.gui.dialogs.RenderersDialog
import org.videolan.vlc.gui.dialogs.SleepTimerDialog
import org.videolan.vlc.gui.dialogs.VLCBottomSheetDialogFragment.Companion.shouldInterceptRemote
import org.videolan.vlc.gui.dialogs.adapters.VlcTrack
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.KeycodeListener
import org.videolan.vlc.gui.helpers.PlayerKeyListenerDelegate
import org.videolan.vlc.gui.helpers.PlayerOptionsDelegate
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.UiTools.showPinIfNeeded
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate
import org.videolan.vlc.interfaces.IPlaybackSettingsController
import org.videolan.vlc.media.NO_LENGTH_PROGRESS_MAX
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.ResumeStatus
import org.videolan.vlc.media.WaitConfirmation
import org.videolan.vlc.repository.ExternalSubRepository
import org.videolan.vlc.repository.SlaveRepository
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.ContextOption.CTX_ADD_TO_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_FAV_ADD
import org.videolan.vlc.util.ContextOption.CTX_FAV_REMOVE
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_ALBUM
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_ARTIST
import org.videolan.vlc.util.ContextOption.CTX_REMOVE_FROM_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_SHARE
import org.videolan.vlc.util.ContextOption.CTX_STOP_AFTER_THIS
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.FileUtils.getUri
import org.videolan.vlc.util.FlagSet
import org.videolan.vlc.util.FrameRateManager
import org.videolan.vlc.util.IDialogManager
import org.videolan.vlc.util.LocaleUtil
import org.videolan.vlc.util.LocaleUtil.localeEquivalent
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.Util
import org.videolan.vlc.util.hasNotch
import org.videolan.vlc.util.isTalkbackIsEnabled
import org.videolan.vlc.util.share
import org.videolan.vlc.viewmodels.BookmarkModel
import org.videolan.vlc.viewmodels.PlaylistModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt


open class VideoPlayerActivity : AppCompatActivity(), PlaybackService.Callback, PlaylistAdapter.IPlayer, OnClickListener, OnLongClickListener, StoragePermissionsDelegate.CustomActionController, TextWatcher, IDialogManager, KeycodeListener {

    var hasPhysicalNotch: Boolean = false
    private var subtitlesExtraPath: String? = null
    private lateinit var startedScope: CoroutineScope
    private var videoRemoteJob: Job? = null
    var service: PlaybackService? = null
    lateinit var medialibrary: Medialibrary
    var videoLayout: VLCVideoLayout? = null
    lateinit var displayManager: DisplayManager
    var rootView: View? = null
    var videoUri: Uri? = null
    var savedMediaList: ArrayList<MediaWrapper>? = null
    var savedMediaIndex: Int = 0
    private var askResume = true

    var playlistModel: PlaylistModel? = null

    lateinit var settings: SharedPreferences

    private var isDragging: Boolean = false
    var isShowing: Boolean = false
    var isShowingDialog: Boolean = false
    internal var isLoading: Boolean = false
        private set
    private var isPlaying = false
    private var loadingImageView: ImageView? = null
    var enableCloneMode: Boolean = false
    lateinit var orientationMode: PlayerOrientationMode

    private var currentAudioTrack = "-2"
    private var currentSpuTrack = "-2"

    var isLocked = false

    /* -1 is a valid track (Disable) */
    private var lastAudioTrack = "-2"
    private var lastSpuTrack = "-2"
    var lockBackButton = false
    private var wasPaused = false
    private var savedTime: Long = -1

    lateinit var windowLayoutInfo: WindowLayoutInfo
    private var currentConfirmationDialog: AlertDialog? = null
    val resumeDialogObserver: (t: WaitConfirmation?) -> Unit = {
        if (it != null)
            showConfirmResumeDialog(it)
        else
            currentConfirmationDialog?.dismiss()
    }

    /**
     * For uninterrupted switching between audio and video mode
     */
    private var switchingView: Boolean = false
    private var switchToPopup: Boolean = false

    //Volume
    internal lateinit var audiomanager: AudioManager
        private set
    internal var audioMax: Int = 0
        private set
    internal var isAudioBoostEnabled: Boolean = false
        private set
    private var isMute = false
    private var volSave: Int = 0
    internal var volume: Float = 0.toFloat()
    internal var originalVol: Float = 0.toFloat()
    private var warningToast: Toast? = null

    internal var fov: Float = 0.toFloat()
    lateinit var touchDelegate: VideoTouchDelegate
    val statsDelegate: VideoStatsDelegate by lazy(LazyThreadSafetyMode.NONE) { VideoStatsDelegate(this, { overlayDelegate.showOverlayTimeout(OVERLAY_INFINITE) }, { overlayDelegate.showOverlay(true) }) }
    val delayDelegate: VideoDelayDelegate by lazy(LazyThreadSafetyMode.NONE) { VideoDelayDelegate(this@VideoPlayerActivity) }
    val screenshotDelegate: VideoPlayerScreenshotDelegate by lazy(LazyThreadSafetyMode.NONE) { VideoPlayerScreenshotDelegate(this@VideoPlayerActivity) }
    val overlayDelegate: VideoPlayerOverlayDelegate by lazy(LazyThreadSafetyMode.NONE) { VideoPlayerOverlayDelegate(this@VideoPlayerActivity) }
    val resizeDelegate: VideoPlayerResizeDelegate by lazy(LazyThreadSafetyMode.NONE) { VideoPlayerResizeDelegate(this@VideoPlayerActivity) }
    val orientationDelegate: VideoPlayerOrientationDelegate by lazy(LazyThreadSafetyMode.NONE) { VideoPlayerOrientationDelegate(this@VideoPlayerActivity) }
    private val playerKeyListenerDelegate: PlayerKeyListenerDelegate by lazy(LazyThreadSafetyMode.NONE) { PlayerKeyListenerDelegate(this@VideoPlayerActivity) }
    val tipsDelegate: VideoTipsDelegate by lazy(LazyThreadSafetyMode.NONE) { VideoTipsDelegate(this@VideoPlayerActivity) }
    var isTv: Boolean = false

    private val dialogsDelegate = DialogDelegate()
    private var baseContextWrappingDelegate: AppCompatDelegate? = null
    var waitingForPin = false

    /**
     * Flag to indicate whether the media should be paused once loaded
     * (e.g. lock screen, or to restore the pause state)
     */
    private var playbackStarted = false

    // Navigation handling (DVD, Blu-Ray...)
    var menuIdx = -1
    var isNavMenu = false

    /* for getTime and seek */
    private var forcedTime: Long = -1
    private var lastTime: Long = -1

    private var alertDialog: AlertDialog? = null

    var isBenchmark = false

    private val addedExternalSubs = ArrayList<org.videolan.vlc.mediadb.models.ExternalSub>()
    private var downloadedSubtitleLiveData: LiveData<List<org.videolan.vlc.mediadb.models.ExternalSub>>? = null
    private var previousMediaPath: String? = null

    private val isInteractive: Boolean
        @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
        get() {
            val pm = applicationContext.getSystemService<PowerManager>()!!
            return if (VlcMigrationHelper.isLolliPopOrLater) pm.isInteractive else pm.isScreenOn
        }

    val playlistObserver = Observer<List<MediaWrapper>> { mediaWrappers -> if (mediaWrappers != null) overlayDelegate.playlistAdapter.update(mediaWrappers) }

    private var addNextTrack = false

    internal val isPlaybackSettingActive: Boolean
        get() = delayDelegate.playbackSetting != IPlaybackSettingsController.DelayState.OFF

    /**
     * Handle resize of the surface and the overlay
     */
    val handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            service?.run {
                when (msg.what) {
                    FADE_OUT -> overlayDelegate.hideOverlay(false)
                    FADE_OUT_INFO -> overlayDelegate.fadeOutInfo(overlayDelegate.overlayInfo)
                    FADE_OUT_BRIGHTNESS_INFO -> overlayDelegate.fadeOutInfo(overlayDelegate.getOverlayBrightness())
                    FADE_OUT_VOLUME_INFO -> overlayDelegate.fadeOutInfo(overlayDelegate.getOverlayVolume())
                    START_PLAYBACK -> startPlayback()
                    AUDIO_SERVICE_CONNECTION_FAILED -> exit(RESULT_CONNECTION_FAILED)
                    RESET_BACK_LOCK -> lockBackButton = true
                    CHECK_VIDEO_TRACKS -> if (videoTracksCount < 1 && audioTracksCount > 0) {
                        Log.i(TAG, "No video track, open in audio mode")
                        switchToAudioMode(true)
                    }
                    LOADING_ANIMATION -> startLoading()
                    HIDE_INFO -> overlayDelegate.hideOverlay(true)
                    SHOW_INFO -> overlayDelegate.showOverlay()
                    HIDE_SEEK -> touchDelegate.hideSeekOverlay()
                    HIDE_SETTINGS -> delayDelegate.endPlaybackSetting()
                    FADE_OUT_SCREENSHOT -> screenshotDelegate.hide()
                    else -> {
                    }
                }
            }
        }
    }

    private val switchAudioRunnable = Runnable {
        if (displayManager.isPrimary && service?.hasMedia() == true && service?.videoTracksCount == 0) {
            Log.i(TAG, "Video track lost, switching to audio")
            switchingView = true
            exit(RESULT_VIDEO_TRACK_LOST)
        }
    }

    /**
     * handle changes of the seekbar (slicer)
     */
    val seekListener = object : OnSeekBarChangeListener {

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            isDragging = true
            overlayDelegate.showOverlayTimeout(OVERLAY_INFINITE)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            isDragging = false
            overlayDelegate.showOverlay(true)
            seek(seekBar.progress.toLong(), fromUser = true, fast = false)
        }

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!isFinishing && fromUser && service?.isSeekable == true) {
                seek(progress.toLong(), fromUser, isDragging)
                if (service?.length != 0L) overlayDelegate.showInfo(Tools.millisToString(progress.toLong()), 1000)
            }
            if (fromUser) {
                overlayDelegate.showOverlay(true)
                overlayDelegate.hudBinding.playerOverlaySeekbar.forceAccessibilityUpdate()
            }
        }
    }

    internal val isOnPrimaryDisplay: Boolean
        get() = displayManager.isPrimary

    val currentScaleType: MediaPlayer.ScaleType
        get() = service?.mediaplayer?.videoScale ?: MediaPlayer.ScaleType.SURFACE_BEST_FIT

    private val isOptionsListShowing: Boolean
        get() = optionsDelegate?.isShowing() == true

    /* XXX: After a seek, playbackService.getTime can return the position before or after
             * the seek position. Therefore we return forcedTime in order to avoid the seekBar
             * to move between seek position and the actual position.
             * We have to wait for a valid position (that is after the seek position).
             * to re-init lastTime and forcedTime to -1 and return the actual position.
             */
    val time: Long
        get() {
            var time = service?.getTime() ?: 0L
            if (forcedTime != -1L && lastTime != -1L) {
                if (lastTime > forcedTime) {
                    if (time in (forcedTime + 1)..lastTime || time > lastTime) {
                        forcedTime = -1
                        lastTime = forcedTime
                    }
                } else {
                    if (time > forcedTime) {
                        forcedTime = -1
                        lastTime = forcedTime
                    }
                }
            } else if (time == 0L) service?.currentMediaWrapper?.time?.let { time = it }
            return if (forcedTime == -1L) time else forcedTime
        }

    private val downloadedSubtitleObserver = Observer<List<org.videolan.vlc.mediadb.models.ExternalSub>> { externalSubs ->
        for (externalSub in externalSubs) {
            if (!addedExternalSubs.contains(externalSub)) {
                service?.addSubtitleTrack(externalSub.subtitlePath, currentSpuTrack == "-2")
                addedExternalSubs.add(externalSub)
            }
        }
    }

    private val screenRotation: Int
        get() {
            val wm = applicationContext.getSystemService<WindowManager>()!!
            return wm.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }

    private var optionsDelegate: PlayerOptionsDelegate? = null

    lateinit var bookmarkModel: BookmarkModel
    val isPlaylistVisible: Boolean
        get() = overlayDelegate.playlistContainer.visibility == View.VISIBLE

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent == null) return
            service?.let { service ->
                when (intent.action) {
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED, BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        val savedDelay = settings.getLong(KEY_BLUETOOTH_DELAY, 0L)
                        val currentDelay = service.audioDelay
                        if (savedDelay != 0L) {
                            val connected = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1) == BluetoothA2dp.STATE_CONNECTED
                            if (connected && currentDelay == 0L)
                                toggleBtDelay(true)
                            else if (!connected && savedDelay == currentDelay)
                                toggleBtDelay(false)
                        }
                    }
                }
            }
        }
    }

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PLAY_FROM_SERVICE) onNewIntent(intent)
            else if (intent.action == EXIT_PLAYER) exitOK()
        }
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale(AppContextProvider.locale)
    }

    override fun getDelegate() = baseContextWrappingDelegate
            ?: BaseContextWrappingDelegate(super.getDelegate()).apply { baseContextWrappingDelegate = this }

    override fun createConfigurationContext(overrideConfiguration: Configuration) = super.createConfigurationContext(overrideConfiguration).getContextWithLocale(AppContextProvider.locale)

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dialogsDelegate.observeDialogs(this, this)
        Util.checkCpuCompatibility(this)

        settings = Settings.getInstance(this)

        /* Services and miscellaneous */
        audiomanager = applicationContext.getSystemService<AudioManager>()!!
        audioMax = audiomanager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        isAudioBoostEnabled = settings.getBoolean(KEY_AUDIO_BOOST, true)

        enableCloneMode = clone ?: settings.getBoolean(KEY_ENABLE_CLONE_MODE, false)
        displayManager = DisplayManager(this, PlaybackService.renderer, false, enableCloneMode, isBenchmark)
        setContentView(if (displayManager.isPrimary) R.layout.player else R.layout.player_remote_control)


        rootView = findViewById(R.id.player_root)


        overlayDelegate.playlist = findViewById(R.id.video_playlist)
        overlayDelegate.playlistSearchText = findViewById(R.id.playlist_search_text)
        overlayDelegate.playlistContainer = findViewById(R.id.video_playlist_container)
        overlayDelegate.closeButton = findViewById(R.id.close_button)
        overlayDelegate.hingeArrowRight = findViewById(R.id.hinge_go_right)
        overlayDelegate.hingeArrowLeft = findViewById(R.id.hinge_go_left)
        overlayDelegate.playlistSearchText.editText?.addTextChangedListener(this)

        overlayDelegate.playerUiContainer = findViewById(R.id.player_ui_container)

        val screenOrientationSetting = Integer.valueOf(settings.getString(SCREEN_ORIENTATION, "99" /*SCREEN ORIENTATION SENSOR*/)!!)
        val sensor = settings.getBoolean(LOCK_USE_SENSOR, true)
        orientationMode = when (screenOrientationSetting) {
            99 -> PlayerOrientationMode(false)
            101 -> PlayerOrientationMode(true, if (sensor) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            102 -> PlayerOrientationMode(true, if (sensor) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            103 -> PlayerOrientationMode(true, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
            98 -> PlayerOrientationMode(true, settings.getInt(LAST_LOCK_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE))
            else -> PlayerOrientationMode(true, getOrientationForLock())
        }

        videoLayout = findViewById(R.id.video_layout)

        /* Loading view */
        loadingImageView = findViewById(R.id.player_overlay_loading)
        overlayDelegate.dimStatusBar(true)
        handler.sendEmptyMessageDelayed(LOADING_ANIMATION, LOADING_ANIMATION_DELAY.toLong())

        switchingView = false

        askResume = settings.getString(KEY_VIDEO_CONFIRM_RESUME, "0") == "2"
        sDisplayRemainingTime = settings.getBoolean(KEY_REMAINING_TIME_DISPLAY, false)
        // Clear the resume time, since it is only used for resumes in external
        // videos.
        settings.putSingle(VIDEO_RESUME_TIME, -1L)
        // Paused flag - per session too, like the subs list.
        this.volumeControlStream = AudioManager.STREAM_MUSIC

        // 100 is the value for screen_orientation_start_lock
        try {
            requestedOrientation = getScreenOrientation(orientationMode)
            //as there is no ActivityInfo.SCREEN_ORIENTATION_SENSOR_REVERSE_LANDSCAPE, now that we are in reverse landscape, enable the sensor if needed
            if (screenOrientationSetting == 103 && sensor){
                orientationMode = PlayerOrientationMode(true,ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                requestedOrientation = getScreenOrientation(orientationMode)
            }

            if (orientationMode.locked) settings.putSingle(LAST_LOCK_ORIENTATION, requestedOrientation)
        } catch (ignored: IllegalStateException) {
            Log.w(TAG, "onCreate: failed to set orientation")
        }
        overlayDelegate.updateOrientationIcon()

        // Extra initialization when no secondary display is detected
        isTv = Settings.showTvUi
        if (displayManager.isPrimary) {
            // Orientation
            // Tips
            if (!BuildConfig.DEBUG && !isTv && !settings.getBoolean(PREF_TIPS_SHOWN, false)
                && !isBenchmark
            ) {
                tipsDelegate.init()
            }
        }


        medialibrary = Medialibrary.getInstance()
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        val yRange = dm.widthPixels.coerceAtMost(dm.heightPixels)
        val xRange = dm.widthPixels.coerceAtLeast(dm.heightPixels)
        val sc = ScreenConfig(dm, xRange, yRange, resources.configuration.orientation)
        touchDelegate = VideoTouchDelegate(this, generateTouchFlags(), sc, isTv)
        UiTools.setRotationAnimation(this)
        if (savedInstanceState != null) {
            savedTime = savedInstanceState.getLong(KEY_TIME)
            savedMediaList = savedInstanceState.parcelableList(KEY_MEDIA_LIST)
            savedMediaIndex = savedInstanceState.getInt(KEY_MEDIA_INDEX)
            val list = savedInstanceState.getBoolean(KEY_LIST, false)
            if (list) {
                intent.removeExtra(PLAY_EXTRA_ITEM_LOCATION)
            } else {
                videoUri = savedInstanceState.parcelable<Parcelable>(KEY_URI) as Uri?
            }
        }

        bookmarkModel = BookmarkModel.get(this)
        overlayDelegate.playToPause = AnimatedVectorDrawableCompat.create(this, R.drawable.anim_play_pause_video)!!
        overlayDelegate.pauseToPlay = AnimatedVectorDrawableCompat.create(this, R.drawable.anim_pause_play_video)!!

        ViewCompat.getWindowInsetsController(window.decorView)?.let { windowInsetsController ->
            windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
        }

        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@VideoPlayerActivity)
                        .windowLayoutInfo(this@VideoPlayerActivity)
                        .collect { layoutInfo ->
                            overlayDelegate.foldingFeature = layoutInfo.displayFeatures
                                    .firstOrNull() as? FoldingFeature
                            windowLayoutInfo = layoutInfo
                        }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (optionsDelegate?.isShowing() == true) {
                    optionsDelegate?.hide()
                } else if (resizeDelegate.isShowing()) {
                    resizeDelegate.hideResizeOverlay()
                } else if (orientationDelegate.isShowing()) {
                    orientationDelegate.hideOrientationOverlay()
                } else if (lockBackButton) {
                    lockBackButton = false
                    handler.sendEmptyMessageDelayed(RESET_BACK_LOCK, 2000)
                    Toast.makeText(applicationContext, getString(R.string.back_quit_lock), Toast.LENGTH_SHORT).show()
                } else if (isPlaylistVisible) {
                    overlayDelegate.togglePlaylist()
                } else if (isPlaybackSettingActive) {
                    delayDelegate.endPlaybackSetting()
                } else if (isShowing && service?.playlistManager?.videoStatsOn?.value == true) {
                    //hides video stats if they are displayed
                    service?.playlistManager?.videoStatsOn?.postValue(false)
                } else if (overlayDelegate.isBookmarkShown()) {
                    overlayDelegate.hideBookmarks()
                } else if ((AndroidDevices.isAndroidTv || isTalkbackIsEnabled()) && isShowing && !isLocked) {
                    overlayDelegate.hideOverlay(true)
                } else {
                    exitOK()
                }
            }
        })
        supportFragmentManager.setFragmentResultListener(CONFIRM_BOOKMARK_RENAME_DIALOG_RESULT, this) { requestKey, bundle ->
            val media = bundle.parcelable<MediaLibraryItem>(RENAME_DIALOG_MEDIA) ?: return@setFragmentResultListener
            val name = bundle.getString(RENAME_DIALOG_NEW_NAME) ?: return@setFragmentResultListener
            overlayDelegate.bookmarkListDelegate?.renameBookmark(media as Bookmark, name)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        hasPhysicalNotch = hasNotch()
        if (hasPhysicalNotch) {
            window.attributes.layoutInDisplayCutoutMode = settings.getInt(DISPLAY_UNDER_NOTCH, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
        }
    }

    /**
     * Generates the touch flags for the [overlayDelegate] based on the controls settings
     *
     * @return the flag corresponding to the gesture the user wants to use
     */
    private fun generateTouchFlags() = if (!isTv) {
        val audioTouch = (!VlcMigrationHelper.isLolliPopOrLater || !audiomanager.isVolumeFixed) && settings.getBoolean(ENABLE_VOLUME_GESTURE, true)
        val brightnessTouch = !AndroidDevices.isChromeBook && settings.getBoolean(ENABLE_BRIGHTNESS_GESTURE, true)
        ((if (audioTouch) TOUCH_FLAG_AUDIO_VOLUME else 0)
                + (if (brightnessTouch) TOUCH_FLAG_BRIGHTNESS else 0)
                + (if (settings.getBoolean(ENABLE_DOUBLE_TAP_SEEK, true)) TOUCH_FLAG_DOUBLE_TAP_SEEK else 0)
                + (if (settings.getBoolean(ENABLE_DOUBLE_TAP_PLAY, true)) TOUCH_FLAG_PLAY else 0)
                + (if (settings.getBoolean(ENABLE_SWIPE_SEEK, true)) TOUCH_FLAG_SWIPE_SEEK else 0)
                + (if (settings.getString(SCREENSHOT_MODE, "0") in arrayOf("2", "3")) TOUCH_FLAG_SCREENSHOT else 0)
                + (if (settings.getBoolean(ENABLE_SCALE_GESTURE, true)) TOUCH_FLAG_SCALE else 0)
                + (if (settings.getBoolean(ENABLE_FASTPLAY, false) && PlaybackService.renderer.value == null) TOUCH_FLAG_FASTPLAY else 0))
    } else 0

    override fun fireDialog(dialog: Dialog) {
        DialogActivity.dialog = dialog
        startActivity(Intent(DialogActivity.KEY_DIALOG, null, this, DialogActivity::class.java))
    }

    override fun dialogCanceled(dialog: Dialog?) {
        (dialog?.context as? DialogFragment)?.dismiss()
    }

    override fun afterTextChanged(s: Editable?) {
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

        if (s == null) return
        val length = s.length
        if (length > 0) {
            playlistModel?.filter(s)
        } else {
            playlistModel?.filter(null)
        }
    }

    override fun onResume() {
        overridePendingTransition(0, 0)
        super.onResume()
        isShowingDialog = false
        waitingForPin = false

        /*
         * Set listeners here to avoid NPE when activity is closing
         */
        overlayDelegate.setListeners(true)

        if (isLocked && !orientationMode.locked) requestedOrientation = orientationMode.orientation
        overlayDelegate.updateOrientationIcon()
        arrayOf(FADE_OUT_VOLUME_INFO, FADE_OUT_BRIGHTNESS_INFO, FADE_OUT, FADE_OUT_INFO, FADE_OUT_SCREENSHOT).forEach {
            handler.removeMessages(it)
            handler.sendEmptyMessage(it)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (playbackStarted) service?.run {
            if (overlayDelegate.isHudRightBindingInitialized()) {
                overlayDelegate.setTitle(currentMediaWrapper?.title)
                        ?: return@run
            }
            var uri: Uri? = if (intent.hasExtra(PLAY_EXTRA_ITEM_LOCATION)) {
                intent.extras?.parcelable<Parcelable>(PLAY_EXTRA_ITEM_LOCATION) as Uri?
            } else intent.data
            if (uri == null || uri == videoUri) return
            if ("file" == uri.scheme && uri.path?.startsWith("/sdcard") == true) {
                val convertedUri = FileUtils.convertLocalUri(uri)
                if (convertedUri == videoUri) return
                else uri = convertedUri
            }
            videoUri = uri
            if (isPlaylistVisible) {
                overlayDelegate.playlistAdapter.currentIndex = currentMediaPosition
                overlayDelegate.playlistContainer.setGone()
            }
            if (settings.getBoolean(VIDEO_TRANSITION_SHOW, true)) showTitle()
            initUI()
            lastTime = -1
            forcedTime = lastTime
            enableSubs()
            if (this.isPlaying) loadMedia(forceUsingNew = true)
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onPause() {
        val finishing = isFinishing
        if (finishing)
            overridePendingTransition(0, 0)
        else
            overlayDelegate.hideOverlay(true)
        super.onPause()
        overlayDelegate.setListeners(false)

        /* Stop the earliest possible to avoid vout error */

        if (!isInPictureInPictureMode
                && (finishing || (AndroidUtil.isNougatOrLater && !AndroidUtil.isOOrLater //Video on background on Nougat Android TVs
                        && AndroidDevices.isAndroidTv && !requestVisibleBehind(true))))
            stopPlayback()
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onUserLeaveHint() {
        if (!isInPictureInPictureMode && displayManager.isPrimary && !isShowingDialog &&
                "2" == settings.getString(KEY_VIDEO_APP_SWITCH, "0") && isInteractive && service?.hasRenderer() == false)
            switchToPopup()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (videoUri != null && "content" != videoUri?.scheme) {
            outState.putLong(KEY_TIME, savedTime)
            if (playlistModel == null) outState.putParcelable(KEY_URI, videoUri)
        }
        val mediaList = service?.playlistManager?.getMediaList() ?: savedMediaList
        val mediaIndex = service?.playlistManager?.currentIndex ?: savedMediaIndex
        if (mediaList != null) {
            outState.putParcelableArrayList(KEY_MEDIA_LIST, ArrayList(mediaList))
            outState.putInt(KEY_MEDIA_INDEX, mediaIndex)
            savedMediaList = null
        }
        videoUri = null
        outState.putBoolean(KEY_LIST, overlayDelegate.hasPlaylist)
    }

    @TargetApi(Build.VERSION_CODES.O)
    fun switchToPopup() {
        if (isBenchmark) return
        optionsDelegate?.hide()
        //look for dialogs and close them
        supportFragmentManager.fragments.forEach { (it as? DialogFragment)?.dismiss() }
        val mw = service?.currentMediaWrapper
        if (mw == null || !AndroidDevices.pipAllowed || !isStarted()) return

        val forceLegacy = Settings.getInstance(this).getBoolean(POPUP_FORCE_LEGACY, false)
        if (AndroidDevices.hasPiP && !forceLegacy) {
            Permissions.checkPiPPermission(this)
            if (AndroidUtil.isOOrLater)
                try {
                    val track = service?.playlistManager?.player?.mediaplayer?.getSelectedVideoTrack()
                            ?: return
                    val width = track.getWidth()
                    val height = track.getHeight()
                    val paramBuilder = PictureInPictureParams.Builder()
                    if (width != 0 && height != 0 && (width.toFloat() / height.toFloat()) in 0.418410f..2.39f)
                        paramBuilder.setAspectRatio(Rational(width, height))
                    paramBuilder.setActions(listOf())
                    service?.updateWidgetState()
                    enterPictureInPictureMode(paramBuilder.build())
                } catch (e: IllegalArgumentException) { // Fallback with default parameters
                    Log.w(TAG, e.message, e)
                    enterPictureInPictureMode()
                }
            else enterPictureInPictureMode()
        } else {
            if (Permissions.canDrawOverlays(this)) {
                switchingView = true
                switchToPopup = true
                if (service?.isPlaying != true) mw.addFlags(MediaWrapper.MEDIA_PAUSED)
                cleanUI()
                exitOK()
            } else Permissions.checkDrawOverlaysPermission(this)
        }
    }

    override fun onVisibleBehindCanceled() {
        super.onVisibleBehindCanceled()
        stopPlayback()
        exitOK()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        this.resources.configuration.orientation = newConfig.orientation
        super.onConfigurationChanged(newConfig)

        if (::touchDelegate.isInitialized) {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(dm)
            val sc = ScreenConfig(dm,
                    dm.widthPixels.coerceAtLeast(dm.heightPixels),
                    dm.widthPixels.coerceAtMost(dm.heightPixels),
                    newConfig.orientation)
            touchDelegate.screenConfig = sc
        }
        overlayDelegate.resetHudLayout()
        overlayDelegate.showControls(isShowing)
        statsDelegate.onConfigurationChanged()
        overlayDelegate.updateHudMargins()
        overlayDelegate.updateTitleConstraints()
        overlayDelegate.rotateBookmarks()
        screenshotDelegate.hide()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus)
            WindowCompat.getInsetsController(window, window.decorView).systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        super.onWindowFocusChanged(hasFocus)
    }

    private fun simulateKeyPress(context: Context, key: Int) {
        val a = context as Activity
        a.window.decorView.rootView
        val inputConnection = BaseInputConnection(
            a.window.decorView.rootView,
            true
        )
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, key)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, key)
        inputConnection.sendKeyEvent(downEvent)
        inputConnection.sendKeyEvent(upEvent)
    }

    override fun onStart() {
        medialibrary.pauseBackgroundOperations()
        super.onStart()
        startedScope = MainScope()
        PlaybackService.start(this)
        PlaybackService.serviceFlow.onEach { onServiceChanged(it) }.launchIn(startedScope)
        videoRemoteJob = lifecycleScope.launch {
            videoRemoteFlow.collect { action ->
                when (action) {
                    "up" -> KeyEvent.KEYCODE_DPAD_UP
                    "down" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "left" -> KeyEvent.KEYCODE_DPAD_LEFT
                    "center" -> KeyEvent.KEYCODE_DPAD_CENTER
                    "back" -> KeyEvent.KEYCODE_BACK
                    "skip-next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "skip-previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "pip" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "play", "pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    else -> null
                }?.let { keyCode ->
                    if (shouldInterceptRemote.value == true) return@let
                    simulateKeyPress(this@VideoPlayerActivity, keyCode)
                    videoRemoteFlow.emit(null)
                }


            }
        }
        restoreBrightness()
        val filter = IntentFilter(PLAY_FROM_SERVICE)
        filter.addAction(EXIT_PLAYER)
        LocalBroadcastManager.getInstance(this).registerReceiver(
                serviceReceiver, filter)
        val btFilter = IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        btFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        registerReceiver(btReceiver, btFilter)
        overlayDelegate.overlayInfo.setInvisible()
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun onStop() {
        super.onStop()
        service?.playlistManager?.let {
            savedMediaList = ArrayList(it.getMediaList())
            savedMediaIndex = it.currentIndex
        }
        startedScope.cancel()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver)

        unregisterReceiver(btReceiver)
        alertDialog?.dismiss()
        val isPlayingPopup = service?.isPlayingPopup == true
        val isSystemPip = (service?.isInPiPMode?.value == true) && !isPlayingPopup
        if (displayManager.isPrimary && !isFinishing && service?.isPlaying == true
                && "1" == settings.getString(KEY_VIDEO_APP_SWITCH, "0") && !PlaybackService.hasRenderer()
                && (!isSystemPip || !isInteractive)) {
            switchToAudioMode(false)
        }
        cleanUI()
        stopPlayback()
        service?.playlistManager?.videoStatsOn?.postValue(false)
        if (isInteractive && !isPlayingPopup)
            service?.isInPiPMode?.value = false

        if (savedTime != -1L) settings.putSingle(VIDEO_RESUME_TIME, savedTime)

        saveBrightness()
        service?.playlistManager?.resetResumeStatus()

        service?.removeCallback(this)
        service = null
        // Clear Intent to restore playlist on activity restart
        intent = Intent()
        handler.removeCallbacksAndMessages(null)
        removeDownloadedSubtitlesObserver()
        previousMediaPath = null
        addedExternalSubs.clear()
        medialibrary.resumeBackgroundOperations()
        videoRemoteJob?.cancel()
    }

    private fun saveBrightness() {
        // Save brightness if user wants to
        if (settings.getBoolean(SAVE_BRIGHTNESS, false)) {
            val brightness = window.attributes.screenBrightness
            if (brightness != -1f) settings.putSingle(BRIGHTNESS_VALUE, brightness)
        }
    }

    private fun restoreBrightness() {
        if (settings.getBoolean(SAVE_BRIGHTNESS, false)) {
            val brightness = settings.getFloat(BRIGHTNESS_VALUE, -1f)
            if (brightness != -1f) setWindowBrightness(brightness)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playlistModel?.run {
            dataset.removeObserver(playlistObserver)
            onCleared()
        }
        optionsDelegate = null
        overlayDelegate.onDestroy()

        // Dismiss the presentation when the activity is not visible.
        displayManager.release()
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun startPlayback() {
        /* start playback only when audio service and both surfaces are ready */
        if (playbackStarted) return

        service?.run {
            playbackStarted = true

            val vlcVout = vout
            if (vlcVout != null && vlcVout.areViewsAttached()) {
                if (isPlayingPopup) {
                    stop(video = true)
                } else
                    vlcVout.detachViews()
            }
            val mediaPlayer = mediaplayer
            if (!displayManager.isOnRenderer) videoLayout?.let {
                mediaPlayer.attachViews(it, displayManager, true, false)
                val size = if (isBenchmark) MediaPlayer.ScaleType.SURFACE_FILL else MediaPlayer.ScaleType.entries[settings.getInt(VIDEO_RATIO, MediaPlayer.ScaleType.SURFACE_BEST_FIT.ordinal)]
                mediaPlayer.videoScale = size
            }

            initUI()

            loadMedia()
        }
    }

    private fun initUI() {

        /* Listen for changes to media routes. */
        if (!isBenchmark) displayManager.setMediaRouterCallback()

        rootView?.run { keepScreenOn = true }
    }

    private fun setPlaybackParameters() {
        service?.run {
            if (audioDelay != 0L && audioDelay != audioDelay) setAudioDelay(audioDelay)
            else if (audiomanager.isBluetoothA2dpOn || audiomanager.isBluetoothScoOn) toggleBtDelay(true)
            if (spuDelay != 0L && spuDelay != spuDelay) setSpuDelay(spuDelay)
        }
    }

    private fun stopPlayback() {
        if (!playbackStarted) return

        if (!displayManager.isPrimary && !isFinishing || service == null) {
            playbackStarted = false
            return
        }
        service?.run {
            val tv = Settings.showTvUi
            val interactive = isInteractive
            wasPaused = !isPlaying || (!tv && !interactive)
            if (wasPaused && !playQueueFinished) settings.putSingle(VIDEO_PAUSED, true)
            if (!isFinishing) {
                currentAudioTrack = audioTrack
                currentSpuTrack = spuTrack
                if (tv && !waitingForPin) finish() // Leave player on TV, restauration can be difficult
            }

            if (isMute) mute(false)

            playbackStarted = false

            handler.removeCallbacksAndMessages(null)
            if (hasMedia() && switchingView) {
                if (BuildConfig.DEBUG) Log.d(TAG, "mLocation = \"$videoUri\"")
                if (switchToPopup)
                    switchToPopup(currentMediaPosition)
                else {
                    mediaplayer.detachViews()
                    currentMediaWrapper?.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                    showWithoutParse(currentMediaPosition)
                }
                return
            }

            if (isSeekable) {
                savedTime = time
                val length = length
                //remove saved position if in the last 5 seconds
                if (length - savedTime < 5000)
                    savedTime = 0
                else
                    savedTime -= 2000 // go back 2 seconds, to compensate loading time
            }
            stop(video = true)
        }
    }

    /**
     * Takes a screenshot from the surface view and forwards it to the [screenshotDelegate]
     *
     */
    fun takeScreenshot() {
        if (AndroidUtil.isOOrLater && !Permissions.canWriteStorage(this)) {
            Permissions.askWriteStoragePermission(this, false){}
            return
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                videoLayout?.findViewById<FrameLayout>(R.id.player_surface_frame)?.let {
                    val surfaceView = it.findViewById<SurfaceView>(R.id.surface_video)
                    surfaceView?.let { surface ->
                        var width = 0
                        var height = 0
                        service?.currentVideoTrack?.let {
                            width = it.getWidth()
                            height = it.getHeight()
                        }
                        if (width == 0) width = surface.width
                        if (height == 0) height = surface.height
                        try {
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
                            AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_SCREENSHOTS_URI_DIRECTORY.toFile().mkdirs()
                            val dst = File(AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_SCREENSHOTS_URI_DIRECTORY.path + "/vlc_${simpleDateFormat.format(Date())}.jpg")

                            PixelCopy.request(surface, bitmap, { copyResult ->
                                if (copyResult != 0) {
                                    UiTools.snacker(this@VideoPlayerActivity, R.string.screenshot_error)
                                    return@request
                                }
                                val coords = IntArray(2)
                                surfaceView.getLocationOnScreen(coords)
                                if (BitmapUtil.saveOnDisk(bitmap, dst.absolutePath, publish = true, context = this@VideoPlayerActivity))
                                    screenshotDelegate.takeScreenshot(dst, bitmap, coords, surface.width, surface.height)
                                else
                                    UiTools.snacker(this@VideoPlayerActivity, R.string.screenshot_error)
                            }, Handler(Looper.getMainLooper()))
                        } catch (e: Exception) {
                            Log.e(TAG, e.message, e)
                            UiTools.snacker(this@VideoPlayerActivity, R.string.screenshot_error)
                        }
                    }
                }

            }
        }

    }

    private fun cleanUI() {

        rootView?.run { keepScreenOn = false }

        /* Stop listening for changes to media routes. */
        if (!isBenchmark) displayManager.removeMediaRouterCallback()

        if (!displayManager.isSecondary) service?.mediaplayer?.detachViews()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return

        if (data.hasExtra(EXTRA_MRL)) {
            val subtitleUri = data.getStringExtra(EXTRA_MRL)!!.toUri()
            service?.addSubtitleTrack(getUri(subtitleUri) ?: subtitleUri, false)
            service?.currentMediaWrapper?.let {
                SlaveRepository.getInstance(this).saveSlave(it.location, IMedia.Slave.Type.Subtitle, 2, data.getStringExtra(EXTRA_MRL)!!)
            }
            addNextTrack = true
        } else if (BuildConfig.DEBUG) Log.d(TAG, "Subtitle selection dialog was cancelled")
    }

    open fun exit(resultCode: Int) {
        if (isFinishing) return
        val resultIntent = Intent(ACTION_RESULT)
        videoUri?.let { uri ->
            service?.run {
                if (AndroidUtil.isNougatOrLater)
                    resultIntent.putExtra(EXTRA_URI, uri.toString())
                else
                    resultIntent.data = videoUri
                resultIntent.putExtra(EXTRA_POSITION, time)
                resultIntent.putExtra(EXTRA_DURATION, length)
            }
        }
        setResult(resultCode, resultIntent)
        finish()
    }

    private fun exitOK() {
        exit(RESULT_OK)
    }

    override fun onTrackballEvent(event: MotionEvent): Boolean {
        if (isLoading) return false
        overlayDelegate.showOverlay()
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val result = !isLoading && ::touchDelegate.isInitialized && touchDelegate.dispatchGenericMotionEvent(event)
        return if (result) true else super.dispatchGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (service == null || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B)
            return super.onKeyDown(keyCode, event)
        if (isOptionsListShowing) return false
        if (isPlaybackSettingActive && keyCode != KeyEvent.KEYCODE_J && keyCode != KeyEvent.KEYCODE_K
                && keyCode != KeyEvent.KEYCODE_G && keyCode != KeyEvent.KEYCODE_H) return false
        if (isLoading) {
            when (keyCode) {
                KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_MEDIA_STOP -> {
                    exitOK()
                    return true
                }
            }
            return false
        }
        if (isShowing || fov == 0f && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !overlayDelegate.playlistContainer.isVisible())
            overlayDelegate.showOverlayTimeout(Settings.videoHudDelay * 1000)
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                touchDelegate.seekDelta(Settings.videoDoubleTapJumpDelay * 1000)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                touchDelegate.seekDelta(-Settings.videoDoubleTapJumpDelay * 1000)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (overlayDelegate.isHudBindingInitialized() && overlayDelegate.hudBinding.progressOverlay.isVisible())
                    return false
                when {
                    isNavMenu -> return navigateDvdMenu(keyCode)
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        //prevent conflict with remote control
                    -> return super.onKeyDown(keyCode, event)
                    else -> doPlayPause()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                when {
                    isNavMenu -> return navigateDvdMenu(keyCode)
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> return super.onKeyDown(keyCode, event)
                    else -> doPlayPause()
                }
                return true
            }
            KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, KeyEvent.KEYCODE_BUTTON_X -> {
                onAudioSubClick(if (overlayDelegate.isHudBindingInitialized()) overlayDelegate.hudBinding.playerOverlayTracks else null)
                return true
            }
            KeyEvent.KEYCODE_A -> {
                resizeVideo()
                return true
            }
            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                updateMute()
                return true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_MEDIA_STOP -> {
                exitOK()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isNavMenu)
                    return navigateDvdMenu(keyCode)
                else if (isLocked) {
                    overlayDelegate.showOverlayTimeout(Settings.videoHudDelay * 1000)
                } else if (!isShowing && !overlayDelegate.playlistContainer.isVisible() && !resizeDelegate.isShowing()) {
                    if (event.isAltPressed && event.isCtrlPressed) {
                        touchDelegate.seekDelta(-300000)
                    } else if (event.isShiftPressed && event.isCtrlPressed) {
                        touchDelegate.seekDelta(-30000)
                    } else if (event.isShiftPressed) {
                        touchDelegate.seekDelta(-5000)
                    } else if (event.isCtrlPressed) {
                        touchDelegate.seekDelta(-60000)
                    } else if (fov == 0f)
                        touchDelegate.seekDelta(-Settings.videoDoubleTapJumpDelay * 1000)
                    else
                        service?.updateViewpoint(-5f, 0f, 0f, 0f, false)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isNavMenu)
                    return navigateDvdMenu(keyCode)
                else if (isLocked) {
                    overlayDelegate.showOverlayTimeout(Settings.videoHudDelay * 1000)
                } else if (!isShowing && !overlayDelegate.playlistContainer.isVisible() && !resizeDelegate.isShowing()) {
                    if (event.isAltPressed && event.isCtrlPressed) {
                        touchDelegate.seekDelta(300000)
                    } else if (event.isShiftPressed && event.isCtrlPressed) {
                        touchDelegate.seekDelta(30000)
                    } else if (event.isShiftPressed) {
                        touchDelegate.seekDelta(5000)
                    } else if (event.isCtrlPressed) {
                        touchDelegate.seekDelta(60000)
                    } else if (fov == 0f)
                        touchDelegate.seekDelta(Settings.videoDoubleTapJumpDelay * 1000)
                    else
                        service?.updateViewpoint(5f, 0f, 0f, 0f, false)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isNavMenu)
                    return navigateDvdMenu(keyCode)
                else if (isLocked) {
                    overlayDelegate.showOverlayTimeout(Settings.videoHudDelay * 1000)
                } else if (event.isCtrlPressed) {
                    volumeUp()
                    return true
                } else if (!isShowing && !overlayDelegate.playlistContainer.isVisible()) {
                    if (fov == 0f)
                        showAdvancedOptions()
                    else
                        service?.updateViewpoint(0f, -5f, 0f, 0f, false)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isNavMenu)
                    return navigateDvdMenu(keyCode)
                else if (isLocked) {
                    overlayDelegate.showOverlayTimeout(Settings.videoHudDelay * 1000)
                } else if (event.isCtrlPressed) {
                    volumeDown()
                    return true
                } else if (!isShowing && fov != 0f) {
                    service?.updateViewpoint(0f, 5f, 0f, 0f, false)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (isNavMenu)
                    return navigateDvdMenu(keyCode)
                else if (isLocked) {
                    overlayDelegate.showOverlayTimeout(Settings.videoHudDelay * 1000)
                } else if (!isShowing && !resizeDelegate.isShowing()) {
                    doPlayPause()
                    return true
                }
            }
            KeyEvent.KEYCODE_ENTER -> return if (isNavMenu)
                navigateDvdMenu(keyCode)
            else
                super.onKeyDown(keyCode, event)
            KeyEvent.KEYCODE_J -> {
                delayDelegate.delayAudioOrSpu(delta = -50000L, delayState = IPlaybackSettingsController.DelayState.AUDIO)
                handler.removeMessages(HIDE_SETTINGS)
                handler.sendEmptyMessageDelayed(HIDE_SETTINGS, 4000L)
                return true
            }
            KeyEvent.KEYCODE_K -> {
                delayDelegate.showDelayControls()
                delayDelegate.delayAudioOrSpu(delta = 50000L, delayState = IPlaybackSettingsController.DelayState.AUDIO)
                handler.removeMessages(HIDE_SETTINGS)
                handler.sendEmptyMessageDelayed(HIDE_SETTINGS, 4000L)
                return true
            }
            KeyEvent.KEYCODE_G -> {
                delayDelegate.delayAudioOrSpu(delta = -50000L, delayState = IPlaybackSettingsController.DelayState.SUBS)
                handler.removeMessages(HIDE_SETTINGS)
                handler.sendEmptyMessageDelayed(HIDE_SETTINGS, 4000L)
                return true
            }
            KeyEvent.KEYCODE_T -> {
                overlayDelegate.showOverlay()
            }
            KeyEvent.KEYCODE_H -> {
                if (event.isCtrlPressed) {
                    overlayDelegate.showOverlay()
                } else {
                    delayDelegate.delayAudioOrSpu(delta = 50000L, delayState = IPlaybackSettingsController.DelayState.SUBS)
                    handler.removeMessages(HIDE_SETTINGS)
                    handler.sendEmptyMessageDelayed(HIDE_SETTINGS, 4000L)
                }
                return true
            }
            KeyEvent.KEYCODE_Z -> {
                resizeVideo()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volumeDown()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volumeUp()
                return true
            }
            KeyEvent.KEYCODE_CAPTIONS -> {
                onAudioSubClick(if (overlayDelegate.isHudBindingInitialized()) overlayDelegate.hudBinding.playerOverlayTracks else null)
                return true
            }
            KeyEvent.KEYCODE_C -> {
                resizeVideo()
                return true
            }
        }
        if (playerKeyListenerDelegate.onKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun showEqualizer() {
        val newFragment = EqualizerFragmentDialog()
        newFragment.onDismissListener = DialogInterface.OnDismissListener { overlayDelegate.dimStatusBar(true) }
        newFragment.show(supportFragmentManager, "equalizer")
    }

    override fun next() {
        if (service?.hasNext() == true) {
            service?.next()
            overlayDelegate.showInfo(getString(R.string.next), 1000)
            overlayDelegate.showOverlay()
        }
    }

    override fun previous() {
        service?.let { service ->
            service.previous(false)
            overlayDelegate.showInfo(getString(R.string.previous), 1000)
            overlayDelegate.showOverlay()
        }
    }

    override fun stop() {
        service?.let { service ->
            service.stop()
            overlayDelegate.showInfo(getString(R.string.stop), 1000)
        }
    }

    override fun seek(delta: Int) {
        touchDelegate.seekDelta(delta)
    }


    override fun togglePlayPause() {
        doPlayPause()
    }

    override fun increaseRate() {
        service?.increaseRate()
    }

    override fun decreaseRate() {
        service?.decreaseRate()
    }

    override fun resetRate() {
        service?.resetRate()
    }

    override fun bookmark() {
        bookmarkModel.addBookmark(this)
        UiTools.snackerConfirm(this, getString(R.string.bookmark_added), confirmMessage = R.string.show) {
            overlayDelegate.showOverlay()
            overlayDelegate.showBookmarks()
        }
    }

    override fun isReady() = true

    // Directly managed here
    override fun isReadyForDirectional() = false

    override fun showAdvancedOptions() {
        if (optionsDelegate == null) service?.let {
            optionsDelegate = PlayerOptionsDelegate(this, it)
            optionsDelegate!!.setBookmarkClickedListener {
                lifecycleScope.launch { if (!showPinIfNeeded()) overlayDelegate.showBookmarks() else overlayDelegate.showOverlay() }
            }
        }
        optionsDelegate?.show()
        overlayDelegate.hideOverlay(fromUser = false, forceTalkback = true)
    }

    private fun volumeUp() {
        if (isMute) {
            updateMute()
        } else service?.let { service ->
            var volume = if (audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC) < audioMax)
                audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC) + 1
            else
                (service.volume.toFloat() * audioMax / 100 + 1).roundToInt()
            volume = volume.coerceAtLeast(0).coerceAtMost(audioMax * if (isAudioBoostEnabled) 2 else 1)
            setAudioVolume(volume)
        }
    }

    private fun volumeDown() {
        service?.let { service ->
            var vol = if (service.volume > 100)
                (((service.volume * audioMax).div(100)) - 1)
            else
                audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC) - 1
            vol = vol.coerceAtLeast(0).coerceAtMost(audioMax * if (isAudioBoostEnabled) 2 else 1)
            originalVol = vol.toFloat()
            setAudioVolume(vol)
        }
    }

    internal fun navigateDvdMenu(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                service?.navigate(MediaPlayer.Navigate.Up)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                service?.navigate(MediaPlayer.Navigate.Down)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                service?.navigate(MediaPlayer.Navigate.Left)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                service?.navigate(MediaPlayer.Navigate.Right)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_A -> {
                service?.navigate(MediaPlayer.Navigate.Activate)
                return true
            }
            else -> return false
        }
    }

    override fun update() {
        if (service == null || !overlayDelegate.isPlaylistAdapterInitialized()) return
        playlistModel?.update()
    }

    override fun onMediaEvent(event: IMedia.Event) {
        when (event.type) {
            IMedia.Event.ParsedChanged -> updateNavStatus()
            IMedia.Event.MetaChanged -> {
            }
        }
    }

    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        service?.let { service ->
            when (event.type) {
                MediaPlayer.Event.Playing -> onPlaying()
                MediaPlayer.Event.Paused -> overlayDelegate.updateOverlayPausePlay()
                MediaPlayer.Event.Opening -> {
                    forcedTime = -1
                    if (!subtitlesExtraPath.isNullOrEmpty()) {
                        service.addSubtitleTrack(subtitlesExtraPath!!, true)
                        subtitlesExtraPath = null
                    }
                }
                MediaPlayer.Event.Vout -> {
                    updateNavStatus()
                    if (event.voutCount > 0)
                        service.mediaplayer.updateVideoSurfaces()
                    if (menuIdx == -1)
                        handleVout(event.voutCount)
                }
                MediaPlayer.Event.ESAdded -> {
                    if (menuIdx == -1) {
                        val mw = service.currentMediaWrapper ?: return
                        if (event.esChangedType == IMedia.Track.Type.Audio) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val media = medialibrary.findMedia(mw)
                                var preferredTrack = "0"
                                val preferredAudioLang = settings.getString(KEY_AUDIO_PREFERRED_LANGUAGE, "")
                                if (!preferredAudioLang.isNullOrEmpty()) {
                                    /** ⚠️limitation: See [LocaleUtil] header comment */
                                    val allTracks = getCurrentMediaTracks()
                                    service.audioTracks?.iterator()?.let { audioTracks ->
                                        while (audioTracks.hasNext()) {
                                            val next = audioTracks.next()
                                            val realTrack = allTracks.find { it.id.toString() == next.getId() }
                                            if (LocaleUtil.getLocaleFromVLC(realTrack?.language
                                                            ?: "") == preferredAudioLang) {
                                                preferredTrack = next.getId()
                                                break
                                            }
                                        }
                                    }
                                }
                                val audioTrack = when (val savedTrack = media.getMetaString(MediaWrapper.META_AUDIOTRACK) ?: "0") {
                                    "0" -> preferredTrack
                                    else -> savedTrack
                                }
                                if (audioTrack != "0" || currentAudioTrack != "-2")
                                    service.setAudioTrack(audioTrack.toString())
                            }
                        } else if (event.esChangedType == IMedia.Track.Type.Text) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val media = medialibrary.findMedia(mw)
                                var preferredTrack = "0"
                                val preferredSpuLang = settings.getString(KEY_SUBTITLE_PREFERRED_LANGUAGE, "")
                                if (!preferredSpuLang.isNullOrEmpty()) {
                                    val allTracks = getCurrentMediaTracks()
                                    service.spuTracks?.iterator()?.let { spuTracks ->
                                        while (spuTracks.hasNext()) {
                                            val next = spuTracks.next()
                                            val realTrack = allTracks.find { it.id.toString() == next.getId() }
                                            if (LocaleUtil.getLocaleFromVLC(realTrack?.language
                                                            ?: "") in preferredSpuLang.localeEquivalent()) {
                                                preferredTrack = next.getId()
                                                break
                                            }
                                        }
                                    }
                                }
                                val spuTrack = when (val savedTrack = media.getMetaString(MediaWrapper.META_SUBTITLE_TRACK) ?: "0") {
                                    "0" -> preferredTrack
                                    else -> savedTrack
                                }
                                if (addNextTrack) {
                                    val tracks = service.spuTracks
                                    @Suppress("UNCHECKED_CAST")
                                    if ((tracks as Array<VlcTrack>).isNotEmpty()) service.setSpuTrack(tracks[tracks.size - 1].getId())
                                    addNextTrack = false
                                } else if (spuTrack != "0" || currentSpuTrack != "-2") {
                                    service.setSpuTrack(spuTrack)
                                    lastSpuTrack = "-2"
                                }
                            }
                        }
                    }
                    if (menuIdx == -1 && event.esChangedType == IMedia.Track.Type.Video) {
                        handler.removeMessages(CHECK_VIDEO_TRACKS)
                        handler.sendEmptyMessageDelayed(CHECK_VIDEO_TRACKS, 1000)
                        lifecycleScope.launch(Dispatchers.IO) {
                            service.currentMediaWrapper?.let { mw ->
                                val media = medialibrary.findMedia(mw)
                                val videoTrack = media.getMetaString(MediaWrapper.META_VIDEOTRACK) ?: "0"
                                if (videoTrack != "0" && media.id != 0L) service.setVideoTrack(videoTrack)
                            }
                        }
                    }
                }
                MediaPlayer.Event.ESDeleted -> {
                    if (menuIdx == -1 && event.esChangedType == IMedia.Track.Type.Video) {
                        handler.removeMessages(CHECK_VIDEO_TRACKS)
                        handler.sendEmptyMessageDelayed(CHECK_VIDEO_TRACKS, 1000)
                    }
                }
                MediaPlayer.Event.ESSelected -> if (event.esChangedType == IMedia.Track.Type.Video) {
                    val vt = service.currentVideoTrack
                    if (vt != null)
                        fov = if (vt.getProjection() == IMedia.VideoTrack.Projection.Rectangular) 0f else DEFAULT_FOV
                }
                MediaPlayer.Event.SeekableChanged -> overlayDelegate.updateSeekable(event.seekable)
                MediaPlayer.Event.PausableChanged -> overlayDelegate.updatePausable(event.pausable)
                MediaPlayer.Event.Buffering -> {
                    if (isPlaying) {
                        if (event.buffering == 100f)
                            stopLoading()
                        else if (!handler.hasMessages(LOADING_ANIMATION) && !isLoading
                                && (!::touchDelegate.isInitialized || !touchDelegate.isSeeking()) && !isDragging)
                            handler.sendEmptyMessageDelayed(LOADING_ANIMATION, LOADING_ANIMATION_DELAY.toLong())
                    }
                }
            }
        }
    }

    private var currentTracks: Pair<String, List<IMedia.Track>>? = null

    /**
     * Extract all the tracks from the current media
     * The tracks are also cached in [currentTracks] to avoid some native calls
     *
     * @return a list of [IMedia.Track]
     */
    private fun getCurrentMediaTracks():List<IMedia.Track> {

        service?.let { service ->
            val allTracks= ArrayList<IMedia.Track>()
            service.mediaplayer.media?.let { media ->
                if (currentTracks?.first == media.uri.toString()) return currentTracks!!.second
                allTracks.addAll(media.getAllTracks())
                currentTracks = Pair(media.uri.toString(), allTracks)
            }
            return allTracks
        }
        return listOf()
    }

    private fun onPlaying() {
        val mw = service?.currentMediaWrapper ?: return
        isPlaying = true
        overlayDelegate.hasPlaylist = service?.hasPlaylist() == true
        setPlaybackParameters()
        stopLoading()
        overlayDelegate.updateOverlayPausePlay()
        updateNavStatus()
        if (!mw.hasFlag(MediaWrapper.MEDIA_PAUSED) && Settings.videoHudDelay != -1)
            handler.sendEmptyMessageDelayed(FADE_OUT, Settings.videoHudDelay.toLong() * 1000)
        else {
            mw.removeFlags(MediaWrapper.MEDIA_PAUSED)
            wasPaused = false
        }
        setESTracks()
        if (overlayDelegate.isHudRightBindingInitialized() && (overlayDelegate.hudRightBinding.playerOverlayTitle.length() == 0 || PlaybackService.hasRenderer()))
            overlayDelegate.setTitle(mw.title)
        // Get possible subtitles
        observeDownloadedSubtitles()
        optionsDelegate?.setup()
        settings.edit { remove(VIDEO_PAUSED) }
        if (isInPictureInPictureMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val track = service?.playlistManager?.player?.mediaplayer?.getSelectedVideoTrack() ?: return
            val ar =
                Rational(track.getWidth().coerceAtMost((track.getHeight() * 2.39f).toInt()), track.getHeight())
            if (ar.isFinite && !ar.isZero) {
                setPictureInPictureParams(
                    PictureInPictureParams.Builder().setAspectRatio(ar).build()
                )
            }
        }
        if (tipsDelegate.currentTip != null) pause()

        //if possible, match display with content frame rate
        val preferMatchFrameRate =
            settings.getBoolean(KEY_VIDEO_MATCH_FRAME_RATE, false)
        if (preferMatchFrameRate) {
            val surfaceView = rootView?.findViewById<View>(R.id.surface_video) as SurfaceView
            FrameRateManager(this, service!!).matchFrameRate(surfaceView, window)
        }
        overlayDelegate.updatePlaybackSpeedChip()
    }

    private fun handleVout(voutCount: Int) {
        handler.removeCallbacks(switchAudioRunnable)

        val vlcVout = service?.vout ?: return
        if (displayManager.isPrimary && vlcVout.areViewsAttached() && voutCount == 0) {
            handler.postDelayed(switchAudioRunnable, 4000)
        }
    }

    override fun recreate() {
        handler.removeCallbacks(switchAudioRunnable)
        super.recreate()
    }

    fun switchToAudioMode(showUI: Boolean) {
        if (service == null) return
        switchingView = true
        PlaylistManager.playingAsAudio = showUI
        // Show the MainActivity if it is not in background.
        if (showUI && intent.getBooleanExtra(FROM_EXTERNAL, false)) {
            val i = Intent().apply {
                setClassName(applicationContext, if (isTv) TV_AUDIOPLAYER_ACTIVITY else MOBILE_MAIN_ACTIVITY)
            }
            startActivity(i)
        }
        exitOK()
    }

    override fun isInPictureInPictureMode(): Boolean {
        return service?.isInPiPMode?.value == true
    }

    override fun setPictureInPictureParams(params: PictureInPictureParams) {
        try {
            super.setPictureInPictureParams(params)
        } catch (e: IllegalArgumentException) {
            if (BuildConfig.DEBUG) throw e
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        service?.isInPiPMode?.value = isInPictureInPictureMode
        service?.mediaplayer?.updateVideoSurfaces()
    }

    internal fun sendMouseEvent(action: Int, x: Int, y: Int) {
        service?.vout?.sendMouseEvent(action, 0, x, y)
    }

    /**
     * show/hide the overlay
     */

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return service != null && touchDelegate.onTouchEvent(event)
    }

    internal fun updateViewpoint(yaw: Float, pitch: Float, fov: Float): Boolean {
        return service?.updateViewpoint(yaw, pitch, 0f, fov, false) == true
    }

    internal fun initAudioVolume() = service?.let { service ->
        if (service.volume <= 100) {
            volume = audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            originalVol = audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        } else {
            volume = service.volume.toFloat() * audioMax / 100
        }
    }

    //Toast that appears only once
    fun displayWarningToast() {
        warningToast?.cancel()
        warningToast = Toast.makeText(application, R.string.audio_boost_warning, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.LEFT or Gravity.BOTTOM, 16.dp, 0)
            show()
        }
    }

    internal fun setAudioVolume(volume: Int) {
        var vol = volume
        if (AndroidUtil.isNougatOrLater && (vol <= 0) xor isMute) {
            mute(!isMute)
            return  //Android N+ throws "SecurityException: Not allowed to change Do Not Disturb state"
        }

        /* Since android 4.3, the safe volume warning dialog is displayed only with the FLAG_SHOW_UI flag.
         * We don't want to always show the default UI volume, so show it only when volume is not set. */
        service?.let { service ->
            if (vol <= audioMax) {
                service.setVolume(100)
                if (vol != audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                    try {
                        audiomanager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                        // High Volume warning can block volume setting
                        if (audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC) != vol)
                            audiomanager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, AudioManager.FLAG_SHOW_UI)
                    } catch (ignored: RuntimeException) {
                    }
                    //Some device won't allow us to change volume
                }
                vol = (vol * 100 / audioMax.toFloat()).roundToInt()
            } else {
                vol = (vol * 100 / audioMax.toFloat()).roundToInt()
                service.setVolume(vol.toFloat().roundToInt())
            }
            overlayDelegate.showVolumeBar(vol)
            volSave = service.volume
        }
    }

    private fun mute(mute: Boolean) {
        service?.let { service ->
            isMute = mute
            if (isMute) volSave = service.volume
            service.setVolume(if (isMute) 0 else volSave)
        }
    }

    private fun updateMute() {
        mute(!isMute)
        overlayDelegate.showInfo(if (isMute) R.string.sound_off else R.string.sound_on, 1000)
    }

    internal fun changeBrightness(delta: Float) {
        // Estimate and adjust Brightness
        val lp = window.attributes
        var brightness = (lp.screenBrightness + delta).coerceIn(0.01f, 1f)
        setWindowBrightness(brightness)
        brightness = (brightness * 100).roundToInt().toFloat()
        overlayDelegate.showBrightnessBar(brightness.toInt())
    }

    private fun setWindowBrightness(brightness: Float) {
        val lp = window.attributes
        lp.screenBrightness = brightness
        // Set Brightness
        window.attributes = lp
    }

    open fun onAudioSubClick(anchor: View?) {
        overlayDelegate.showTracks()
        overlayDelegate.hideOverlay(false)
    }

    private val ctxReceiver: CtxActionReceiver = object : CtxActionReceiver {
        override fun onCtxAction(position: Int, option: ContextOption) {
            if (position in 0 until overlayDelegate.playlistAdapter.itemCount) when (option) {
                CTX_ADD_TO_PLAYLIST -> {
                    val mw = overlayDelegate.playlistAdapter.getItemByPosition(position)
                    addToPlaylist(listOf(mw))
                }
                CTX_REMOVE_FROM_PLAYLIST -> service?.run {
                    remove(position)
                }
                CTX_STOP_AFTER_THIS -> {
                    val pos = if (playlistModel?.service?.playlistManager?.stopAfter != position) position else -1
                    playlistModel?.stopAfter(pos)
                    overlayDelegate.playlistAdapter.stopAfter = pos
                }
                CTX_GO_TO_ALBUM -> {
                    val i = Intent(this@VideoPlayerActivity, HeaderMediaListActivity::class.java)
                    i.putExtra(AudioBrowserFragment.TAG_ITEM, overlayDelegate.playlistAdapter.getItemByPosition(position).album)
                    startActivity(i)
                }
                CTX_GO_TO_ARTIST -> lifecycleScope.launch(Dispatchers.IO) {
                    val artist = overlayDelegate.playlistAdapter.getItemByPosition(position).artist
                    val i = Intent(this@VideoPlayerActivity, SecondaryActivity::class.java)
                    i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUMS_SONGS)
                    i.putExtra(AudioBrowserFragment.TAG_ITEM, artist)
                    i.putExtra(ARTIST_FROM_ALBUM, true)
                    i.flags = i.flags or Intent.FLAG_ACTIVITY_NO_HISTORY
                    startActivity(i)
                }
                CTX_FAV_ADD, CTX_FAV_REMOVE -> lifecycleScope.launch {
                    overlayDelegate.playlistAdapter.getItemByPosition(position).isFavorite = option == CTX_FAV_ADD
                    overlayDelegate.playlistAdapter.notifyItemChanged(position)
                }
                CTX_SHARE -> lifecycleScope.launch { share(overlayDelegate.playlistAdapter.getItemByPosition(position)) }
                else -> {}
            }
        }
    }

    override fun onPopupMenu(view: View, position: Int, item: MediaWrapper?) {
        val flags = FlagSet(ContextOption::class.java).apply {
            addAll(CTX_REMOVE_FROM_PLAYLIST, CTX_STOP_AFTER_THIS)
            if (item?.uri?.scheme != "content") addAll(CTX_ADD_TO_PLAYLIST,  CTX_SHARE)
            if (item?.album != null) add(CTX_GO_TO_ALBUM)
            if (item?.artist != null) add(CTX_GO_TO_ARTIST)
            if (item?.isFavorite == true) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
        }
        showContext(this, ctxReceiver, position, item, flags)
    }

    override fun getLifeCycle() = this.lifecycle

    override fun onSelectionSet(position: Int) = overlayDelegate.playlist.scrollToPosition(position)

    override fun playItem(position: Int, item: MediaWrapper) {
        service?.saveMediaMeta()
        service?.playlistManager?.getMedia(position)
        service?.playlistManager?.getMediaList()?.let {
            if (it[position] == item)  service?.playIndex(position)
            else {
                for ((index, media) in it.withIndex()) if (item == media) {
                    service?.playIndex(index)
                }
            }
        }
        overlayDelegate.togglePlaylist()
    }

    fun jump (forward:Boolean, long: Boolean) {
        val jumpDelay = if (long) Settings.videoLongJumpDelay else Settings.videoJumpDelay
        val delay = if (forward) jumpDelay * 1000 else -(jumpDelay * 1000)
        touchDelegate.seekDelta(if (LocaleUtil.isRtl()) -delay  else delay)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.orientation_toggle -> toggleOrientationLock()
            R.id.playlist_toggle -> overlayDelegate.togglePlaylist()
            R.id.player_overlay_forward -> {
                jump(forward = true, long = false)
                overlayDelegate.showOverlay()
            }
            R.id.player_overlay_rewind -> {
                jump(forward = false, long = false)
                overlayDelegate.showOverlay()
            }
            R.id.ab_repeat_add_marker -> service?.playlistManager?.setABRepeatValue(
                service?.playlistManager?.getCurrentMedia(), overlayDelegate.hudBinding.playerOverlaySeekbar.progress.toLong())
            R.id.ab_repeat_reset -> service?.playlistManager?.resetABRepeatValues(service?.playlistManager?.getCurrentMedia())
            R.id.ab_repeat_stop -> {
                service?.playlistManager?.resetABRepeatValues(service?.playlistManager?.getCurrentMedia())
                service?.playlistManager?.clearABRepeat()
            }
            R.id.player_overlay_navmenu -> showNavMenu()
            R.id.player_overlay_length, R.id.player_overlay_time -> toggleTimeDisplay()
            R.id.video_renderer -> if (supportFragmentManager.findFragmentByTag("renderers") == null)
                RenderersDialog().show(supportFragmentManager, "renderers")
            R.id.video_secondary_display -> {
                clone = displayManager.isSecondary
                recreate()
            }
            R.id.playback_speed_quick_action -> {
                val newFragment = PlaybackSpeedDialog.newInstance()
                newFragment.onDismissListener = DialogInterface.OnDismissListener { overlayDelegate.dimStatusBar(true) }
                newFragment.show(supportFragmentManager, "playback_speed")
                overlayDelegate.hideOverlay(false)
            }
            R.id.sleep_quick_action -> {
                val newFragment = SleepTimerDialog.newInstance()
                newFragment.onDismissListener = DialogInterface.OnDismissListener { overlayDelegate.dimStatusBar(true) }
                newFragment.show(supportFragmentManager, "time")
                overlayDelegate.hideOverlay(false)
            }
            R.id.audio_delay_quick_action -> {
                delayDelegate.showAudioDelaySetting()
                overlayDelegate.hideOverlay(false)
            }
            R.id.spu_delay_quick_action -> {
                delayDelegate.showSubsDelaySetting()
                overlayDelegate.hideOverlay(false)
            }
            R.id.player_screenshot -> {
                overlayDelegate.hideOverlay(false)
                takeScreenshot()
            }
            R.id.orientation_quick_action -> {
                overlayDelegate.nextOrientation()
            }
            R.id.player_overlay_title_warning -> {
                val snackbar = UiTools.snackerMessageInfinite(this, getString(R.string.player_title_fd_warning))
                snackbar?.setAction(R.string.ok) {
                    snackbar.dismiss()
                }
                snackbar?.show()
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        when (v.id) {
            R.id.orientation_toggle -> {
                orientationDelegate.displayOrientation()
                return true
            }
            R.id.player_overlay_forward -> {
                jump(forward = true, long = true)
                return true
            }
            R.id.player_overlay_rewind -> {
                jump(forward = false, long = true)
                return true
            }
        }
        return false
    }

    fun toggleTimeDisplay() {
        sDisplayRemainingTime = !sDisplayRemainingTime
        overlayDelegate.showOverlay()
        settings.putSingle(KEY_REMAINING_TIME_DISPLAY, sDisplayRemainingTime)
    }

    fun toggleLock() {
        if (isLocked)
            overlayDelegate.unlockScreen()
        else
            overlayDelegate.lockScreen()
        overlayDelegate.updateRendererVisibility()
    }

    override fun onStorageAccessGranted() {
        handler.sendEmptyMessage(START_PLAYBACK)
    }

    private fun showNavMenu() {
        if (menuIdx >= 0) service?.titleIdx = menuIdx
    }

    fun doPlayPause() {
        if (service?.isPausable != true) return
        if (service?.isPlaying == true) {
            overlayDelegate.showOverlayTimeout(OVERLAY_INFINITE)
            pause()
        } else {
            if (Settings.videoHudDelay != -1) handler.sendEmptyMessageDelayed(FADE_OUT, 800L)
            play()
        }
    }

    fun seek(position: Long, fromUser: Boolean = false) {
        service?.let { seek(position, it.length, fromUser) }
    }

    fun seek(position: Long, fromUser: Boolean = false, fast:Boolean = false) {
        service?.let { seek(position, it.length, fromUser, fast) }
    }

    internal fun seek(position: Long, length: Long, fromUser: Boolean = false, fast:Boolean = false) {
        service?.let { service ->
            forcedTime = position
            lastTime = service.getTime()
            service.seek(position, length.toDouble(), fromUser, fast)
            service.playlistManager.player.updateProgress(position)
        }
    }

    fun resizeVideo() = resizeDelegate.resizeVideo()

    fun displayResize() = resizeDelegate.displayResize()

    private fun showTitle() {
        if (isNavMenu) return

        var visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        var navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        navbar = navbar or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        if (AndroidDevices.hasNavBar) visibility = visibility or navbar
        window.decorView.systemUiVisibility = visibility
    }

    private fun setESTracks() {
        if (lastAudioTrack >= "-1") {
            service?.setAudioTrack(lastAudioTrack)
            lastAudioTrack = "-2"
        }
        if (lastSpuTrack >=" -1") {
            service?.setSpuTrack(lastSpuTrack)
            lastSpuTrack = "-2"
        }
    }

    /**
     *
     */
    fun play() {
        service?.play()
        rootView?.run { keepScreenOn = true }
    }

    /**
     *
     */
    private fun pause() {
        service?.pause()
        rootView?.run { keepScreenOn = false }
    }


    /**
     * External extras:
     * - position (long) - position of the video to start with (in ms)
     * - subtitles_location (String) - location of a subtitles file to load
     * - from_start (boolean) - Whether playback should start from start or from resume point
     * - title (String) - video title, will be guessed from file if not set.
     */
    @SuppressLint("SdCardPath")
    @TargetApi(12)
    protected open fun loadMedia(fromStart: Boolean = false, forceUsingNew:Boolean = false) {
        if (fromStart) {
            askResume = false
            intent.putExtra(PLAY_EXTRA_FROM_START, fromStart)
        }
        service?.let { service ->
            isPlaying = false
            var title: String? = null
            var fromStart = settings.getString(KEY_VIDEO_CONFIRM_RESUME, "0") == "1"
            var itemTitle: String? = null
            var positionInPlaylist = -1
            val intent = intent
            val extras = intent.extras
            var startTime = 0L
            val currentMedia = service.currentMediaWrapper
            val hasMedia = currentMedia != null
            val isPlaying = service.isPlaying
            /*
         * If the activity has been paused by pressing the power button, then
         * pressing it again will show the lock screen.
         * But onResume will also be called, even if vlc-android is still in
         * the background.
         * To workaround this, pause playback if the lockscreen is displayed.
         */
            val km = applicationContext.getSystemService<KeyguardManager>()!!
            if (km.inKeyguardRestrictedInputMode())
                wasPaused = true
            if (wasPaused && BuildConfig.DEBUG)
                Log.d(TAG, "Video was previously paused, resuming in paused mode")
            intent.data?.let {
                val translatedPath = try {
                    FileUtils.getPathFromURI(it)
                } catch (e: IllegalStateException) {
                    ""
                }
                videoUri = if (translatedPath.isNotEmpty()) translatedPath.toUri() else it
            }
            if (extras != null) {
                if (intent.hasExtra(PLAY_EXTRA_ITEM_LOCATION)) {
                    videoUri = extras.parcelable(PLAY_EXTRA_ITEM_LOCATION)
                    intent.removeExtra(PLAY_EXTRA_ITEM_LOCATION)
                }
                fromStart = fromStart or extras.getBoolean(PLAY_EXTRA_FROM_START, false)
                // Consume fromStart option after first use to prevent
                // restarting again when playback is paused.
                intent.putExtra(PLAY_EXTRA_FROM_START, false)
                askResume = askResume and !fromStart
                startTime = if (fromStart) 0L else extras.getLong(PLAY_EXTRA_START_TIME) // position passed in by intent (ms)
                if (!fromStart && startTime == 0L) {
                    startTime = extras.getInt(PLAY_EXTRA_START_TIME).toLong()
                }
                positionInPlaylist = extras.getInt(PLAY_EXTRA_OPENED_POSITION, -1)

                subtitlesExtraPath = extras.getString(PLAY_EXTRA_SUBTITLES_LOCATION)
                if (!subtitlesExtraPath.isNullOrEmpty()) service.addSubtitleTrack(subtitlesExtraPath!!, true)
                if (intent.hasExtra(PLAY_EXTRA_ITEM_TITLE))
                    itemTitle = extras.getString(PLAY_EXTRA_ITEM_TITLE)
            }
            val restorePlayback = hasMedia && currentMedia?.uri == videoUri
            if (startTime == 0L && savedTime > 0L && restorePlayback) startTime = savedTime

            var openedMedia: MediaWrapper? = null
            val resumePlaylist = service.isValidIndex(positionInPlaylist)
            val continueplayback = isPlaying && (restorePlayback || positionInPlaylist == service.currentMediaPosition)
            if (resumePlaylist) {
                // Provided externally from AudioService
                if (BuildConfig.DEBUG) Log.v(TAG, "Continuing playback from PlaybackService at index $positionInPlaylist")
                openedMedia = service.media[positionInPlaylist]
                itemTitle = openedMedia.title
                overlayDelegate.updateSeekable(service.isSeekable)
                overlayDelegate.updatePausable(service.isPausable)
            }
            if (videoUri != null) {
                var uri = videoUri ?: return
                var media: MediaWrapper? = null
                if (!continueplayback) {
                    if (!resumePlaylist) {
                        // restore last position
                        media = medialibrary.getMedia(uri)
                        if (media == null && uri.scheme == "file" &&
                                uri.path?.startsWith("/sdcard") == true) {
                            uri = FileUtils.convertLocalUri(uri)
                            videoUri = uri
                            media = medialibrary.getMedia(uri)
                        }
                    } else media = openedMedia
                    if (media != null) {
                        // in media library

                        lastAudioTrack = media.audioTrack.toString()
                        lastSpuTrack = media.spuTrack.toString()
                    } else if (!fromStart) {
                        // not in media library
                            val rTime = settings.getLong(VIDEO_RESUME_TIME, -1L)
                            val lastUri = settings.getString(VIDEO_RESUME_URI, "")
                            if (rTime > 0 && service.currentMediaLocation == lastUri) {
                                    settings.putSingle(VIDEO_RESUME_TIME, -1L)
                                    startTime = rTime
                            }
                    }
                }

                // Start playback & seek
                /* prepare playback */
                val medialoaded = media != null
                if (!medialoaded) media = if (hasMedia && !forceUsingNew) currentMedia else MLServiceLocator.getAbstractMediaWrapper(uri)
                itemTitle?.let { media?.title = Uri.decode(it) }
                if (wasPaused) media?.addFlags(MediaWrapper.MEDIA_PAUSED)
                if (intent.hasExtra(PLAY_DISABLE_HARDWARE)) media?.addFlags(MediaWrapper.MEDIA_NO_HWACCEL)
                media!!.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                media.addFlags(MediaWrapper.MEDIA_VIDEO)
                if (fromStart) media.addFlags(MediaWrapper.MEDIA_FROM_START)

                // Set resume point
                if (!continueplayback && !fromStart) {
                    if (startTime <= 0L && media.time > 0L) startTime = media.time
                    if (startTime > 0L) service.saveStartTime(startTime)
                }

                // Handle playback
                if (resumePlaylist) {
                    if (continueplayback) {
                        if (displayManager.isPrimary) service.flush()
                        onPlaying()
                    } else service.playIndex(positionInPlaylist)
                } else service.load(media)

                // Get the title
                if (itemTitle == null && "content" != uri.scheme) title = uri.lastPathSegment
            } else if (service.hasMedia() && !displayManager.isPrimary) {
                onPlaying()
            } else {
                service.loadLastPlaylist(PLAYLIST_TYPE_ALL)
            }
            if (itemTitle != null) title = itemTitle
            if (overlayDelegate.isHudRightBindingInitialized()) {
                overlayDelegate.setTitle(title)
            }

            if (wasPaused) {
                // XXX: Workaround to update the seekbar position
                forcedTime = startTime
                forcedTime = -1
                overlayDelegate.showOverlay(true)
            }
            enableSubs()
        }
    }

    private fun enableSubs() {
        videoUri?.let {
            val lastPath = it.lastPathSegment ?: return
            overlayDelegate.enableSubs = (lastPath.isNotEmpty() && !lastPath.endsWith(".ts") && !lastPath.endsWith(".m2ts")
                    && !lastPath.endsWith(".TS") && !lastPath.endsWith(".M2TS"))
        }
    }

    private fun removeDownloadedSubtitlesObserver() {
        downloadedSubtitleLiveData?.removeObserver(downloadedSubtitleObserver)
        downloadedSubtitleLiveData = null
    }

    private fun observeDownloadedSubtitles() {
        service?.let { service ->
            val uri = service.currentMediaWrapper?.uri ?: return
            val path = uri.path ?: return
            if (previousMediaPath == null || path != previousMediaPath) {
                previousMediaPath = path
                removeDownloadedSubtitlesObserver()
                downloadedSubtitleLiveData = ExternalSubRepository.getInstance(this).getDownloadedSubtitles(uri).apply {
                    observe(this@VideoPlayerActivity, downloadedSubtitleObserver)
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun getScreenOrientation(mode: PlayerOrientationMode): Int {
        return if (!mode.locked) {
            if (VlcMigrationHelper.isJellyBeanMR2OrLater)
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            mode.orientation
        }
    }

    private fun getOrientationForLock(): Int {
        val wm = applicationContext.getSystemService<WindowManager>()!!
        val display = wm.defaultDisplay
        val rot = screenRotation
        /*
         * Since getRotation() returns the screen's "natural" orientation,
         * which is not guaranteed to be SCREEN_ORIENTATION_PORTRAIT,
         * we have to invert the SCREEN_ORIENTATION value if it is "naturally"
         * landscape.
         */
        var defaultWide = display.width > display.height
        if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270)
            defaultWide = !defaultWide
        return if (defaultWide) {
            when (rot) {
                Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> 0
            }
        } else {
            when (rot) {
                Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> 0
            }
        }
    }

    private fun showConfirmResumeDialog(confirmation: WaitConfirmation) {
        if (isFinishing) return
        if (isInPictureInPictureMode) {
            lifecycleScope.launch { service?.playlistManager?.playIndex(confirmation.index, confirmation.flags, forceResume = true) }
            return
        }
        service?.pause()
        PlaybackService.waitConfirmation.postValue(confirmation.title)
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_video_resume, null)
        val resumeAllCheck = dialogView.findViewById<CheckBox>(R.id.video_resume_checkbox)
        alertDialog = AlertDialog.Builder(this@VideoPlayerActivity)
                .setTitle(confirmation.title)
                .setView(dialogView)
                .setPositiveButton(R.string.resume) { _, _ ->
                    if (resumeAllCheck.isChecked) service?.playlistManager?.videoResumeStatus = ResumeStatus.ALWAYS
                    lifecycleScope.launch { service?.playlistManager?.playIndex(confirmation.index, confirmation.flags, forceResume = true) }
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    if (resumeAllCheck.isChecked) service?.playlistManager?.videoResumeStatus = ResumeStatus.NEVER
                    lifecycleScope.launch { service?.playlistManager?.playIndex(confirmation.index, confirmation.flags, forceRestart = true) }
                }
                .setOnDismissListener {
                    currentConfirmationDialog = null
                    PlaybackService.waitConfirmation.postValue(null)
                }
                .create().apply {
                    setCancelable(false)
                    setOnKeyListener { dialog, keyCode, _ ->
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            dialog.dismiss()
                            finish()
                            true
                        } else false
                    }
                currentConfirmationDialog = this
                    show()
                }
    }

    fun toggleOrientationLock() {
        orientationMode.locked = !orientationMode.locked
        orientationMode.orientation = getOrientationForLock()

        requestedOrientation = getScreenOrientation(orientationMode)
        if (orientationMode.locked) settings.putSingle(LAST_LOCK_ORIENTATION, requestedOrientation)
        overlayDelegate.updateOrientationIcon()
    }

    private fun toggleBtDelay(connected: Boolean) {
        service?.setAudioDelay(if (connected) settings.getLong(KEY_BLUETOOTH_DELAY, 0) else 0L)
    }

    fun setOrientation(value: Int) {
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "setOrientation: $value")
        if (value == -1) {
            if (orientationMode.locked) toggleOrientationLock()
            return
        }
        orientationMode.locked = true
        orientationMode.orientation = value
        requestedOrientation = value
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "setOrientation requestedOrientation: $requestedOrientation")
        overlayDelegate.updateOrientationIcon()
    }

    /**
     * Start the video loading animation.
     */
    private fun startLoading() {
        if (isLoading) return
        isLoading = true
        val anim = AnimationSet(true)
        val rotate = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        rotate.duration = 800
        rotate.interpolator = DecelerateInterpolator()
        rotate.repeatCount = RotateAnimation.INFINITE
        anim.addAnimation(rotate)
        loadingImageView.setVisible()
        loadingImageView?.startAnimation(anim)
    }

    /**
     * Stop the video loading animation.
     */
    private fun stopLoading() {
        handler.removeMessages(LOADING_ANIMATION)
        if (!isLoading) return
        isLoading = false
        loadingImageView.setInvisible()
        loadingImageView?.clearAnimation()
    }

    fun onClickDismissTips(@Suppress("UNUSED_PARAMETER") v: View) {
        tipsDelegate.close()
    }

    fun onClickNextTips(@Suppress("UNUSED_PARAMETER") v: View?) {
        tipsDelegate.next()
    }

    fun updateNavStatus() {
        if (service == null) return
        menuIdx = -1
        lifecycleScope.launchWhenStarted {
            val titles = withContext(Dispatchers.IO) { service?.titles }
            if (isFinishing) return@launchWhenStarted
            isNavMenu = false
            if (titles != null) {
                val currentIdx = service?.titleIdx ?: return@launchWhenStarted
                for (i in titles.indices) {
                    val title = titles[i]
                    if (title.isMenu) {
                        menuIdx = i
                        break
                    }
                }
                val interactive = service?.mediaplayer?.let {
                    try {
                        (it.titles[it.title])?.isInteractive == true
                    } catch (e: NullPointerException) {
                        false
                    }
                } == true
                isNavMenu = menuIdx == currentIdx || interactive
            }

            if (isNavMenu) {
                /*
                         * Keep the overlay hidden in order to have touch events directly
                         * transmitted to navigation handling.
                         */
                overlayDelegate.hideOverlay(false)
            } else if (menuIdx != -1) setESTracks()

            if (overlayDelegate.isHudRightBindingInitialized()) overlayDelegate.hudRightBinding.playerOverlayNavmenu.setVisibility(if (menuIdx >= 0) View.VISIBLE else View.GONE)
            supportInvalidateOptionsMenu()
        }
    }

    open fun onServiceChanged(service: PlaybackService?) {
        if (service != null) {
            this.service = service
            if (savedMediaList != null && service.currentMediaWrapper == null) {
                service.append(savedMediaList!!, savedMediaIndex)
                savedMediaList = null
            }
            //We may not have the permission to access files
            if (!switchingView)
                handler.sendEmptyMessage(START_PLAYBACK)
            switchingView = false
            handler.post {
                // delay mediaplayer loading, prevent ANR
                if (service.volume > 100 && !isAudioBoostEnabled) service.setVolume(100)
                if (volSave > 100 && service.volume != volSave) service.setVolume(volSave)
            }
            service.addCallback(this)
            service.playlistManager.waitForConfirmation.observe(this, resumeDialogObserver)
            //if (isTalkbackIsEnabled()) overlayDelegate.showOverlayTimeout(OVERLAY_INFINITE)
        } else if (this.service != null) {
            this.service?.removeCallback(this)
            this.service?.playlistManager?.waitForConfirmation?.removeObserver(resumeDialogObserver)
            this.service = null
            handler.sendEmptyMessage(AUDIO_SERVICE_CONNECTION_FAILED)
            removeDownloadedSubtitlesObserver()
            previousMediaPath = null
        }
    }

    /**
     * Callback called when a Control setting has been changed in the advanced options
     */
    fun onChangedControlSetting(key: String) = when(key) {
        KEY_AUDIO_BOOST -> isAudioBoostEnabled = settings.getBoolean(KEY_AUDIO_BOOST, true)
        ENABLE_VOLUME_GESTURE, ENABLE_BRIGHTNESS_GESTURE, ENABLE_DOUBLE_TAP_SEEK, ENABLE_DOUBLE_TAP_PLAY, ENABLE_SWIPE_SEEK, ENABLE_SCALE_GESTURE, ENABLE_FASTPLAY -> touchDelegate.touchControls = generateTouchFlags()
        SCREENSHOT_MODE -> {
            touchDelegate.touchControls = generateTouchFlags()
            overlayDelegate.updateScreenshotButton()
        }
        ENABLE_SEEK_BUTTONS -> overlayDelegate.seekButtons = settings.getBoolean(ENABLE_SEEK_BUTTONS, false)
        else -> {}
    }

    companion object {

        private const val TAG = "VLC/VideoPlayerActivity"

        private val ACTION_RESULT = "player.result".buildPkgString()
        private const val EXTRA_POSITION = "extra_position"
        private const val EXTRA_DURATION = "extra_duration"
        private const val EXTRA_URI = "extra_uri"
        const val FROM_EXTERNAL = "from_external"
        private const val RESULT_CONNECTION_FAILED = RESULT_FIRST_USER + 1
        private const val RESULT_PLAYBACK_ERROR = RESULT_FIRST_USER + 2
        private const val RESULT_VIDEO_TRACK_LOST = RESULT_FIRST_USER + 3
        internal const val DEFAULT_FOV = 80f
        private const val KEY_TIME = "saved_time"
        private const val KEY_LIST = "saved_list"
        private const val KEY_MEDIA_LIST = "media_list"
        private const val KEY_MEDIA_INDEX = "media_index"
        private const val KEY_URI = "saved_uri"
        const val OVERLAY_INFINITE = -1
        const val FADE_OUT = 1
        const val FADE_OUT_INFO = 2
        private const val START_PLAYBACK = 3
        private const val AUDIO_SERVICE_CONNECTION_FAILED = 4
        private const val RESET_BACK_LOCK = 5
        private const val CHECK_VIDEO_TRACKS = 6
        private const val LOADING_ANIMATION = 7
        internal const val SHOW_INFO = 8
        internal const val HIDE_INFO = 9
        internal const val HIDE_SEEK = 10
        internal const val HIDE_SETTINGS = 11
        const val FADE_OUT_BRIGHTNESS_INFO = 12
        const val FADE_OUT_VOLUME_INFO = 13
        const val FADE_OUT_SCREENSHOT = 14
        private const val KEY_REMAINING_TIME_DISPLAY = "remaining_time_display"
        const val KEY_BLUETOOTH_DELAY = "key_bluetooth_delay"
        val videoRemoteFlow = MutableStateFlow<String?>(null)

        private const val LOADING_ANIMATION_DELAY = 1000

        @Volatile
        internal var sDisplayRemainingTime: Boolean = false

        private var clone: Boolean? = null

        fun start(context: Context, uri: Uri) {
            start(context, uri, null, false, -1)
        }

        fun start(context: Context, uri: Uri, fromStart: Boolean) {
            start(context, uri, null, fromStart, -1)
        }

        fun start(context: Context, uri: Uri, title: String) {
            start(context, uri, title, false, -1)
        }

        fun startOpened(context: Context, uri: Uri, openedPosition: Int) {
            start(context, uri, null, false, openedPosition)
        }

        private fun start(context: Context, uri: Uri, title: String?, fromStart: Boolean, openedPosition: Int) {
            val intent = getIntent(context, uri, title, fromStart, openedPosition)
            context.startActivity(intent, Util.getFullScreenBundle())
        }

        fun getIntent(action: String, mw: MediaWrapper, fromStart: Boolean, openedPosition: Int): Intent {
            return getIntent(action, AppContextProvider.appContext, mw.uri, mw.title, fromStart, openedPosition)
        }

        fun getIntent(context: Context, uri: Uri, title: String?, fromStart: Boolean, openedPosition: Int): Intent {
            return getIntent(PLAY_FROM_VIDEOGRID, context, uri, title, fromStart, openedPosition)
        }

        fun getIntent(action: String, context: Context, uri: Uri, title: String?, fromStart: Boolean, openedPosition: Int): Intent {
            val intent = Intent(context, VideoPlayerActivity::class.java)
            intent.action = action
            intent.putExtra(PLAY_EXTRA_ITEM_LOCATION, uri)
            intent.putExtra(PLAY_EXTRA_ITEM_TITLE, title)
            intent.putExtra(PLAY_EXTRA_FROM_START, fromStart)

            if (openedPosition != -1 || context !is Activity) {
                if (openedPosition != -1)
                    intent.putExtra(PLAY_EXTRA_OPENED_POSITION, openedPosition)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return intent
        }
    }
}

data class PlayerOrientationMode(
        var locked: Boolean = false,
        var orientation: Int = -1
)

@BindingAdapter("length", "time")
fun setPlaybackTime(view: TextView, length: Long, time: Long) {
    view.text = if (VideoPlayerActivity.sDisplayRemainingTime && length > 0)
        "-" + '\u00A0'.toString() + Tools.millisToString(length - time)
    else
        Tools.millisToString(length)
}

@BindingAdapter("constraintPercent")
fun setConstraintPercent(view: Guideline, percent: Float) {
    val constraintLayout = view.parent as ConstraintLayout
    val constraintSet = ConstraintSet()
    constraintSet.clone(constraintLayout)
    constraintSet.setGuidelinePercent(view.id, percent)
    constraintSet.applyTo(constraintLayout)
}

@BindingAdapter("mediamax")
fun setProgressMax(view: SeekBar, length: Long) {
    view.max =  if (length == 0L) NO_LENGTH_PROGRESS_MAX else length.toInt()
}
