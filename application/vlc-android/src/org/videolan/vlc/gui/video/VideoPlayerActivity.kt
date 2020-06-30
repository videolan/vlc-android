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

import android.animation.Animator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.KeyguardManager
import android.app.PictureInPictureParams
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.*
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.support.v4.media.session.PlaybackStateCompat
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.*
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.animation.*
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.ViewStubCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.textfield.TextInputLayout
import kotlinx.android.synthetic.main.player.*
import kotlinx.android.synthetic.main.player_overlay_brightness.*
import kotlinx.android.synthetic.main.player_overlay_volume.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.libvlc.util.DisplayManager
import org.videolan.libvlc.util.VLCVideoLayout
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.*
import org.videolan.tools.*
import org.videolan.vlc.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlayerHudBinding
import org.videolan.vlc.databinding.PlayerHudRightBinding
import org.videolan.vlc.gui.audio.EqualizerFragment
import org.videolan.vlc.gui.audio.PlaylistAdapter
import org.videolan.vlc.gui.browser.EXTRA_MRL
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.dialogs.RenderersDialog
import org.videolan.vlc.gui.helpers.OnRepeatListener
import org.videolan.vlc.gui.helpers.PlayerOptionsDelegate
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate
import org.videolan.vlc.interfaces.IPlaybackSettingsController
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.repository.ExternalSubRepository
import org.videolan.vlc.repository.SlaveRepository
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.Util
import org.videolan.vlc.viewmodels.PlaylistModel
import java.lang.Runnable
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
open class VideoPlayerActivity : AppCompatActivity(), PlaybackService.Callback, PlaylistAdapter.IPlayer, OnClickListener, OnLongClickListener, StoragePermissionsDelegate.CustomActionController, TextWatcher {

    private lateinit var startedScope : CoroutineScope
    private var wasPlaying = true
    var service: PlaybackService? = null
    private lateinit var medialibrary: Medialibrary
    private var videoLayout: VLCVideoLayout? = null
    lateinit var displayManager: DisplayManager
    private var rootView: View? = null
    private var videoUri: Uri? = null
    private var askResume = true

    private lateinit var closeButton: View
    private lateinit var playlistContainer: View
    private lateinit var playlist: RecyclerView
    private lateinit var playlistSearchText: TextInputLayout
    private lateinit var playlistAdapter: PlaylistAdapter
    private var playlistModel: PlaylistModel? = null
    private lateinit var abRepeatAddMarker: Button

    private lateinit var settings: SharedPreferences

    /** Overlay  */
    private var overlayBackground: View? = null

    private var isDragging: Boolean = false
    internal var isShowing: Boolean = false
        private set
    private var isShowingDialog: Boolean = false
    var info: TextView? = null
    var overlayInfo: View? = null
    internal var isLoading: Boolean = false
        private set
    private var isPlaying = false
    private var loadingImageView: ImageView? = null
    private var navMenu: ImageView? = null
    protected var enableCloneMode: Boolean = false
    private lateinit var orientationMode: PlayerOrientationMode
    private var orientationLockedBeforeLock: Boolean = false

    private var currentAudioTrack = -2
    private var currentSpuTrack = -2

    internal var isLocked = false
        private set
    /* -1 is a valid track (Disable) */
    private var lastAudioTrack = -2
    private var lastSpuTrack = -2
    private var overlayTimeout = 0
    private var lockBackButton = false
    private var wasPaused = false
    private var savedTime: Long = -1

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
    private val statsDelegate: VideoStatsDelegate by lazy(LazyThreadSafetyMode.NONE) { VideoStatsDelegate(this, { showOverlayTimeout(OVERLAY_INFINITE) }, { showOverlay(true) }) }
    val delayDelegate: VideoDelayDelegate by lazy(LazyThreadSafetyMode.NONE) { VideoDelayDelegate(this@VideoPlayerActivity) }
    private val overlayDelegate: VideoPlayerOverlayDelegate by lazy(LazyThreadSafetyMode.NONE) { VideoPlayerOverlayDelegate(this@VideoPlayerActivity) }
    private var isTv: Boolean = false

    // Tracks & Subtitles
    private var audioTracksList: Array<MediaPlayer.TrackDescription>? = null
    private var videoTracksList: Array<MediaPlayer.TrackDescription>? = null
    private var subtitleTracksList: Array<MediaPlayer.TrackDescription>? = null

    /**
     * Flag to indicate whether the media should be paused once loaded
     * (e.g. lock screen, or to restore the pause state)
     */
    private var playbackStarted = false

    // Tips
    private var overlayTips: View? = null

    // Navigation handling (DVD, Blu-Ray...)
    private var menuIdx = -1
    private var isNavMenu = false

    /* for getTime and seek */
    private var forcedTime: Long = -1
    private var lastTime: Long = -1

    private var alertDialog: AlertDialog? = null

    protected var isBenchmark = false

    private val addedExternalSubs = ArrayList<org.videolan.vlc.mediadb.models.ExternalSub>()
    private var downloadedSubtitleLiveData: LiveData<List<org.videolan.vlc.mediadb.models.ExternalSub>>? = null
    private var previousMediaPath: String? = null

    private val isInteractive: Boolean
        @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
        get() {
            val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            return if (AndroidUtil.isLolliPopOrLater) pm.isInteractive else pm.isScreenOn
        }

    private val playlistObserver = Observer<List<MediaWrapper>> { mediaWrappers -> if (mediaWrappers != null) playlistAdapter.update(mediaWrappers) }

    private var addNextTrack = false

    private lateinit var playToPause: AnimatedVectorDrawableCompat
    private lateinit var pauseToPlay: AnimatedVectorDrawableCompat

    private lateinit var vibrator: Vibrator

    internal val isPlaybackSettingActive: Boolean
        get() = delayDelegate.playbackSetting != IPlaybackSettingsController.DelayState.OFF

    /**
     * Handle resize of the surface and the overlay
     */
    val handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            service?.run {
                when (msg.what) {
                    FADE_OUT -> hideOverlay(false)
                    FADE_OUT_INFO -> fadeOutInfo(overlayInfo)
                    FADE_OUT_BRIGHTNESS_INFO -> fadeOutInfo(player_overlay_brightness)
                    FADE_OUT_VOLUME_INFO -> fadeOutInfo(player_overlay_volume)
                    START_PLAYBACK -> startPlayback()
                    AUDIO_SERVICE_CONNECTION_FAILED -> exit(RESULT_CONNECTION_FAILED)
                    RESET_BACK_LOCK -> lockBackButton = true
                    CHECK_VIDEO_TRACKS -> if (videoTracksCount < 1 && audioTracksCount > 0) {
                        Log.i(TAG, "No video track, open in audio mode")
                        switchToAudioMode(true)
                    } else {
                    }
                    LOADING_ANIMATION -> startLoading()
                    HIDE_INFO -> hideOverlay(true)
                    SHOW_INFO -> showOverlay()
                    HIDE_SEEK -> touchDelegate.hideSeekOverlay()
                    HIDE_SETTINGS -> delayDelegate.endPlaybackSetting()
                    else -> {}
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
    private val seekListener = object : OnSeekBarChangeListener {

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            isDragging = true
            showOverlayTimeout(OVERLAY_INFINITE)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            isDragging = false
            showOverlay(true)
        }

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!isFinishing && fromUser && service?.isSeekable == true) {
                seek(progress.toLong())
                showInfo(Tools.millisToString(progress.toLong()), 1000)
            }
            if (fromUser) {
                showOverlay(true)
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
            var time = service?.time ?: 0L
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

    private lateinit var hudBinding: PlayerHudBinding
    private lateinit var hudRightBinding: PlayerHudRightBinding
    private var seekButtons: Boolean = false
    private var hasPlaylist: Boolean = false

    var enableSubs = true

    private val downloadedSubtitleObserver = Observer<List<org.videolan.vlc.mediadb.models.ExternalSub>> { externalSubs ->
        for (externalSub in externalSubs) {
            if (!addedExternalSubs.contains(externalSub)) {
                service?.addSubtitleTrack(externalSub.subtitlePath, currentSpuTrack == -2)
                addedExternalSubs.add(externalSub)
            }
        }
    }

    private val screenRotation: Int
        get() {
            val wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return wm.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }

    private var optionsDelegate: PlayerOptionsDelegate? = null

    val isPlaylistVisible: Boolean
        get() = playlistContainer.visibility == View.VISIBLE

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

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale(AppContextProvider.locale))
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale(AppContextProvider.locale)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Util.checkCpuCompatibility(this)

        settings = Settings.getInstance(this)

        /* Services and miscellaneous */
        audiomanager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioMax = audiomanager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        isAudioBoostEnabled = settings.getBoolean("audio_boost", true)

        enableCloneMode = clone ?: settings.getBoolean("enable_clone_mode", false)
        displayManager = DisplayManager(this, PlaybackService.renderer, false, enableCloneMode, isBenchmark)
        setContentView(if (displayManager.isPrimary) R.layout.player else R.layout.player_remote_control)


        rootView = findViewById(R.id.player_root)


        playlist = findViewById(R.id.video_playlist)
        playlistSearchText = findViewById(R.id.playlist_search_text)
        playlistContainer = findViewById(R.id.video_playlist_container)
        closeButton = findViewById(R.id.close_button)
        playlistSearchText.editText?.addTextChangedListener(this)



        val screenOrientationSetting = Integer.valueOf(settings.getString(SCREEN_ORIENTATION, "99" /*SCREEN ORIENTATION SENSOR*/)!!)
        orientationMode = when (screenOrientationSetting) {
            99 -> PlayerOrientationMode(false)
            101 -> PlayerOrientationMode(true, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            102 -> PlayerOrientationMode(true, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            else -> PlayerOrientationMode(true, getOrientationForLock())
        }

        videoLayout = findViewById(R.id.video_layout)

        /* Loading view */
        loadingImageView = findViewById(R.id.player_overlay_loading)
        dimStatusBar(true)
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
        } catch (ignored: IllegalStateException) {
            Log.w(TAG, "onCreate: failed to set orientation")
        }
        updateOrientationIcon()

        // Extra initialization when no secondary display is detected
        isTv = Settings.showTvUi
        if (displayManager.isPrimary) {
            // Orientation
            // Tips
            if (!BuildConfig.DEBUG && !isTv && !settings.getBoolean(PREF_TIPS_SHOWN, false)
                    && !isBenchmark) {
                (findViewById<View>(R.id.player_overlay_tips) as ViewStubCompat).inflate()
                overlayTips = findViewById(R.id.overlay_tips_layout)
            }
        }


        medialibrary = Medialibrary.getInstance()
        val touch = if (!isTv) {
            val audioTouch = (!AndroidUtil.isLolliPopOrLater || !audiomanager.isVolumeFixed) && settings.getBoolean(ENABLE_VOLUME_GESTURE, true)
            val brightnessTouch = !AndroidDevices.isChromeBook && settings.getBoolean(ENABLE_BRIGHTNESS_GESTURE, true)
            ((if (audioTouch) TOUCH_FLAG_AUDIO_VOLUME else 0)
                    + (if (brightnessTouch) TOUCH_FLAG_BRIGHTNESS else 0)
                    + if (settings.getBoolean(ENABLE_DOUBLE_TAP_SEEK, true)) TOUCH_FLAG_SEEK else 0)
        } else 0
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        val yRange = dm.widthPixels.coerceAtMost(dm.heightPixels)
        val xRange = dm.widthPixels.coerceAtLeast(dm.heightPixels)
        val sc = ScreenConfig(dm, xRange, yRange, orientationMode.orientation)
        touchDelegate = VideoTouchDelegate(this, touch, sc, isTv)
        UiTools.setRotationAnimation(this)
        if (savedInstanceState != null) {
            savedTime = savedInstanceState.getLong(KEY_TIME)
            val list = savedInstanceState.getBoolean(KEY_LIST, false)
            if (list) {
                intent.removeExtra(PLAY_EXTRA_ITEM_LOCATION)
            } else {
                videoUri = savedInstanceState.getParcelable<Parcelable>(KEY_URI) as Uri?
            }
        }

        playToPause = AnimatedVectorDrawableCompat.create(this, R.drawable.anim_play_pause_video)!!
        pauseToPlay = AnimatedVectorDrawableCompat.create(this, R.drawable.anim_pause_play_video)!!
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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

    private fun hideSearchField(): Boolean {
        if (playlistSearchText.visibility != View.VISIBLE) return false
        playlistSearchText.editText?.apply {
            removeTextChangedListener(this@VideoPlayerActivity)
            setText("")
            addTextChangedListener(this@VideoPlayerActivity)
        }
        UiTools.setKeyboardVisibility(playlistSearchText, false)

        return true
    }

    override fun onResume() {
        overridePendingTransition(0, 0)
        super.onResume()
        isShowingDialog = false
        /*
         * Set listeners here to avoid NPE when activity is closing
         */
        setListeners(true)

        if (isLocked && !orientationMode.locked) requestedOrientation = orientationMode.orientation
        updateOrientationIcon()
    }

    private fun setListeners(enabled: Boolean) {
        navMenu?.setOnClickListener(if (enabled) this else null)
        if (::hudBinding.isInitialized) {
            hudBinding.playerOverlaySeekbar.setOnSeekBarChangeListener(if (enabled) seekListener else null)
            hudBinding.abRepeatReset.setOnClickListener(this)
            hudBinding.abRepeatStop.setOnClickListener(this)
            abRepeatAddMarker.setOnClickListener(this)
            hudBinding.orientationToggle.setOnClickListener(if (enabled) this else null)
            hudBinding.orientationToggle.setOnLongClickListener(if (enabled) this else null)
            hudBinding.swipeToUnlock.setOnStartTouchingListener { showOverlayTimeout(OVERLAY_INFINITE) }
            hudBinding.swipeToUnlock.setOnStopTouchingListener { showOverlayTimeout(OVERLAY_TIMEOUT) }
            hudBinding.swipeToUnlock.setOnUnlockListener { toggleLock() }
        }
        if (::hudRightBinding.isInitialized) UiTools.setViewOnClickListener(hudRightBinding.videoRenderer, if (enabled) this else null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (playbackStarted) service?.run {
            if (::hudBinding.isInitialized) {
                hudRightBinding.playerOverlayTitle.text = currentMediaWrapper?.title ?: return@run
            }
            var uri: Uri? = if (intent.hasExtra(PLAY_EXTRA_ITEM_LOCATION)) {
                intent.extras?.getParcelable<Parcelable>(PLAY_EXTRA_ITEM_LOCATION) as Uri?
            } else intent.data
            if (uri == null || uri == videoUri) return
            if (TextUtils.equals("file", uri.scheme) && uri.path?.startsWith("/sdcard") == true) {
                val convertedUri = FileUtils.convertLocalUri(uri)
                if (convertedUri == videoUri) return
                else uri = convertedUri
            }
            videoUri = uri
            if (isPlaylistVisible) {
                playlistAdapter.currentIndex = currentMediaPosition
                playlistContainer.setGone()
            }
            if (settings.getBoolean("video_transition_show", true)) showTitle()
            initUI()
            lastTime = -1
            forcedTime = lastTime
            enableSubs()
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onPause() {
        val finishing = isFinishing
        if (finishing)
            overridePendingTransition(0, 0)
        else
            hideOverlay(true)
        super.onPause()
        setListeners(false)

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
        videoUri = null
        outState.putBoolean(KEY_LIST, hasPlaylist)
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
            if (AndroidUtil.isOOrLater)
                try {
                    val track = service?.playlistManager?.player?.mediaplayer?.currentVideoTrack
                            ?: return
                    val ar = Rational(track.width.coerceAtMost((track.height * 2.39f).toInt()), track.height)
                    enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ar).build())
                } catch (e: IllegalArgumentException) { // Fallback with default parameters
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
        super.onConfigurationChanged(newConfig)

        if (touchDelegate != null) {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(dm)
            val sc = ScreenConfig(dm,
                    dm.widthPixels.coerceAtLeast(dm.heightPixels),
                    dm.widthPixels.coerceAtMost(dm.heightPixels),
                    orientationMode.orientation)
            touchDelegate.screenConfig = sc
        }
        resetHudLayout()
        showControls(isShowing)
        statsDelegate.onConfigurationChanged()
        updateHudMargins()
        updateTitleConstraints()
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun resetHudLayout() {
        if (!::hudBinding.isInitialized) return
        if (!isTv && !AndroidDevices.isChromeBook) {
            hudBinding.orientationToggle.setVisible()
        }
    }

    override fun onStart() {
        medialibrary.pauseBackgroundOperations()
        super.onStart()
        startedScope = MainScope()
        PlaybackService.start(this)
        PlaybackService.serviceFlow.onEach { onServiceChanged(it) }.launchIn(startedScope)
        restoreBrightness()
        val filter = IntentFilter(PLAY_FROM_SERVICE)
        filter.addAction(EXIT_PLAYER)
        LocalBroadcastManager.getInstance(this).registerReceiver(
                serviceReceiver, filter)
        val btFilter = IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        btFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        registerReceiver(btReceiver, btFilter)
        overlayInfo.setInvisible()
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun onStop() {
        super.onStop()
        startedScope.cancel()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver)

        unregisterReceiver(btReceiver)
        alertDialog?.dismiss()
        if (displayManager.isPrimary && !isFinishing && service?.isPlaying == true
                && "1" == settings.getString(KEY_VIDEO_APP_SWITCH, "0")) {
            switchToAudioMode(false)
        }

        cleanUI()
        stopPlayback()

        if (savedTime != -1L) settings.putSingle(VIDEO_RESUME_TIME, savedTime)

        saveBrightness()

        service?.removeCallback(this)
        service = null
        // Clear Intent to restore playlist on activity restart
        intent = Intent()
        handler.removeCallbacksAndMessages(null)
        removeDownloadedSubtitlesObserver()
        previousMediaPath = null
        addedExternalSubs.clear()
        medialibrary.resumeBackgroundOperations()
        service?.playlistManager?.videoStatsOn?.postValue(false)
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
                val size = if (isBenchmark) MediaPlayer.ScaleType.SURFACE_FILL else MediaPlayer.ScaleType.values()[settings.getInt(VIDEO_RATIO, MediaPlayer.ScaleType.SURFACE_BEST_FIT.ordinal)]
                mediaPlayer.videoScale = size
            }

            initUI()

            loadMedia()
        }
    }

    private fun initPlaylistUi() {
        if (service?.hasPlaylist() == true) {
            if (!::playlistAdapter.isInitialized) {
                playlistAdapter = PlaylistAdapter(this)
                val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
                playlist.layoutManager = layoutManager
            }
            if (playlistModel == null) {
                playlistModel = ViewModelProviders.of(this).get(PlaylistModel::class.java).apply {
                    playlistAdapter.setModel(this)
                    dataset.observe(this@VideoPlayerActivity, playlistObserver)
                }
            }
            hudRightBinding.playlistToggle.setVisible()
            if (::hudBinding.isInitialized) {
                hudBinding.playlistPrevious.setVisible()
                hudBinding.playlistNext.setVisible()
            }
            hudRightBinding.playlistToggle.setOnClickListener(this@VideoPlayerActivity)
            closeButton.setOnClickListener { togglePlaylist() }

            val callback = SwipeDragItemTouchHelperCallback(playlistAdapter, true)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(playlist)
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
            if (wasPaused) settings.putSingle(VIDEO_PAUSED, true)
            if (!isFinishing) {
                currentAudioTrack = audioTrack
                currentSpuTrack = spuTrack
                if (tv) finish() // Leave player on TV, restauration can be difficult
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
            service?.addSubtitleTrack(Uri.parse(data.getStringExtra(EXTRA_MRL)), false)
            service?.currentMediaWrapper?.let {
                SlaveRepository.getInstance(this).saveSlave(it.location, IMedia.Slave.Type.Subtitle, 2, data.getStringExtra(EXTRA_MRL))
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
            setResult(resultCode, resultIntent)
            finish()
        }
    }

    private fun exitOK() {
        exit(Activity.RESULT_OK)
    }

    override fun onTrackballEvent(event: MotionEvent): Boolean {
        if (isLoading) return false
        showOverlay()
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        return !isLoading && ::touchDelegate.isInitialized && touchDelegate.dispatchGenericMotionEvent(event)
    }

    override fun onBackPressed() {
        if (optionsDelegate?.isShowing() == true) {
            optionsDelegate?.hide()
        } else if (lockBackButton) {
            lockBackButton = false
            handler.sendEmptyMessageDelayed(RESET_BACK_LOCK, 2000)
            Toast.makeText(applicationContext, getString(R.string.back_quit_lock), Toast.LENGTH_SHORT).show()
        } else if (isPlaylistVisible) {
            togglePlaylist()
        } else if (isPlaybackSettingActive) {
            delayDelegate.endPlaybackSetting()
        } else if (isTv && isShowing && !isLocked) {
            hideOverlay(true)
        } else {
            exitOK()
            super.onBackPressed()
        }
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
        if (isShowing || fov == 0f && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !playlistContainer.isVisible())
            showOverlayTimeout(OVERLAY_TIMEOUT)
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                touchDelegate.seekDelta(10000)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                touchDelegate.seekDelta(-10000)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                touchDelegate.seekDelta(60000)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                touchDelegate.seekDelta(-60000)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (::hudBinding.isInitialized && hudBinding.progressOverlay.isVisible())
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
            KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_MENU -> {
                showAdvancedOptions()
                return true
            }
            KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, KeyEvent.KEYCODE_BUTTON_X -> {
                onAudioSubClick(if (::hudBinding.isInitialized) hudBinding.playerOverlayTracks else null)
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
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed) {
                    val newFragment = EqualizerFragment()
                    newFragment.onDismissListener = DialogInterface.OnDismissListener { dimStatusBar(true) }
                    newFragment.show(supportFragmentManager, "equalizer")
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isNavMenu)
                    return navigateDvdMenu(keyCode)
                else if (isLocked) {
                    showOverlayTimeout(OVERLAY_TIMEOUT)
                }
                else if (!isShowing && !playlistContainer.isVisible()) {
                    if (event.isAltPressed && event.isCtrlPressed) {
                        touchDelegate.seekDelta(-300000)
                    } else if (event.isCtrlPressed) {
                        touchDelegate.seekDelta(-60000)
                    } else if (fov == 0f)
                        touchDelegate.seekDelta(-10000)
                    else
                        service?.updateViewpoint(-5f, 0f, 0f, 0f, false)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isNavMenu)
                    return navigateDvdMenu(keyCode)
                else if (isLocked) {
                    showOverlayTimeout(OVERLAY_TIMEOUT)
                }
                else if (!isShowing && !playlistContainer.isVisible()) {
                    if (event.isAltPressed && event.isCtrlPressed) {
                        touchDelegate.seekDelta(300000)
                    } else if (event.isCtrlPressed) {
                        touchDelegate.seekDelta(60000)
                    } else if (fov == 0f)
                        touchDelegate.seekDelta(10000)
                    else
                        service?.updateViewpoint(5f, 0f, 0f, 0f, false)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isNavMenu)
                    return navigateDvdMenu(keyCode)
                else if (isLocked) {
                    showOverlayTimeout(OVERLAY_TIMEOUT)
                }
                else if (event.isCtrlPressed) {
                    volumeUp()
                    return true
                } else if (!isShowing && !playlistContainer.isVisible()) {
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
                    showOverlayTimeout(OVERLAY_TIMEOUT)
                }
                else if (event.isCtrlPressed) {
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
                    showOverlayTimeout(OVERLAY_TIMEOUT)
                }
                else if (!isShowing) {
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
                showOverlay()
            }
            KeyEvent.KEYCODE_H -> {
                if (event.isCtrlPressed) {
                    showOverlay()
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
            KeyEvent.KEYCODE_N -> {
                next()
                return true
            }
            KeyEvent.KEYCODE_P -> {
                previous()
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
                selectSubtitles()
                return true
            }
            KeyEvent.KEYCODE_PLUS -> {
                service?.run { setRate(rate * 1.2f, true) }
                return true
            }
            KeyEvent.KEYCODE_EQUALS -> {
                if (event.isShiftPressed) {
                    service?.run { setRate(rate * 1.2f, true) }
                    return true
                } else service?.run { setRate(1f, true) }
                return false
            }
            KeyEvent.KEYCODE_MINUS -> {
                service?.run { setRate(rate / 1.2f, true) }
                return true
            }
            KeyEvent.KEYCODE_C -> {
                resizeVideo()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun volumeUp() {
        if (isMute) {
            updateMute()
        } else service?.let { service ->
            var volume = if (audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC) < audioMax)
                audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC) + 1
            else
                Math.round(service.volume.toFloat() * audioMax / 100 + 1)
            volume = Math.min(Math.max(volume, 0), audioMax * if (isAudioBoostEnabled) 2 else 1)
            setAudioVolume(volume)
        }
    }

    private fun volumeDown() {
        service?.let { service ->
            var vol = if (service.volume > 100)
                Math.round(service.volume.toFloat() * audioMax / 100 - 1)
            else
                audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC) - 1
            vol = Math.min(Math.max(vol, 0), audioMax * if (isAudioBoostEnabled) 2 else 1)
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

    fun focusPlayPause() {
        if (::hudBinding.isInitialized) hudBinding.playerOverlayPlay.requestFocus()
    }

    /**
     * Lock player
     */
    private fun lockScreen() {
        orientationLockedBeforeLock = orientationMode.locked
        if (!orientationMode.locked) toggleOrientation()
        if (::hudBinding.isInitialized) {
            hudBinding.playerOverlayTime.isEnabled = false
            hudBinding.playerOverlaySeekbar.isEnabled = false
            hudBinding.playerOverlayLength.isEnabled = false
            hudBinding.playlistNext.isEnabled = false
            hudBinding.playlistPrevious.isEnabled = false
            hudBinding.swipeToUnlock.setVisible()
        }
        hideOverlay(true)
        lockBackButton = true
        isLocked = true
    }

    /**
     * Remove player lock
     */
    private fun unlockScreen() {
        orientationMode.locked = orientationLockedBeforeLock
        requestedOrientation = getScreenOrientation(orientationMode)
        if (::hudBinding.isInitialized) {
            hudBinding.playerOverlayTime.isEnabled = true
            hudBinding.playerOverlaySeekbar.isEnabled = service?.isSeekable != false
            hudBinding.playerOverlayLength.isEnabled = true
            hudBinding.playlistNext.isEnabled = true
            hudBinding.playlistPrevious.isEnabled = true
        }
        updateOrientationIcon()
        isShowing = false
        isLocked = false
        showOverlay()
        lockBackButton = false
    }


    private fun hapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)  vibrator.vibrate(VibrationEffect.createOneShot(50, 80))
        else vibrator.vibrate(50)
    }

    /**
     * Show the brightness value with  bar
     * @param brightness the brightness value
     */
    private fun showBrightnessBar(brightness: Int) {
        findViewById<ViewStubCompat>(R.id.player_brightness_stub)?.setVisible()
        if (player_overlay_brightness.visibility != View.VISIBLE) hapticFeedback()
        player_overlay_brightness.setVisible()
        brightness_value_text.text = "$brightness%"
        playerBrightnessProgress.setValue(brightness)
        player_overlay_brightness.setVisible()
        handler.removeMessages(FADE_OUT_BRIGHTNESS_INFO)
        handler.sendEmptyMessageDelayed(FADE_OUT_BRIGHTNESS_INFO, 1000L)
        dimStatusBar(true)
    }

    /**
     * Show the volume value with  bar
     * @param volume the volume value
     */
    private fun showVolumeBar(volume: Int) {
        findViewById<ViewStubCompat>(R.id.player_volume_stub)?.setVisible()
        if (player_overlay_volume.visibility != View.VISIBLE)  hapticFeedback()
        volume_value_text.text = "$volume%"
        playerVolumeProgress.isDouble = isAudioBoostEnabled
        playerVolumeProgress.setValue(volume)
        player_overlay_volume.setVisible()
        handler.removeMessages(FADE_OUT_VOLUME_INFO)
        handler.sendEmptyMessageDelayed(FADE_OUT_VOLUME_INFO, 1000L)
        dimStatusBar(true)
    }

    /**
     * Show text in the info view for "duration" milliseconds
     * @param text
     * @param duration
     */
    internal fun showInfo(text: String, duration: Int) {
        if (isInPictureInPictureMode) return
        initInfoOverlay()
        overlayInfo.setVisible()
        info.setVisible()
        info?.text = text
        handler.removeMessages(FADE_OUT_INFO)
        handler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration.toLong())
    }

    fun initInfoOverlay() {
        val vsc = findViewById<ViewStubCompat>(R.id.player_info_stub)
        if (vsc != null) {
            vsc.setVisible()
            // the info textView is not on the overlay
            info = findViewById(R.id.player_overlay_textinfo)
            overlayInfo = findViewById(R.id.player_overlay_info)
        }
    }

    internal fun showInfo(textid: Int, duration: Int) {
        showInfo(getString(textid), duration)
    }

    /**
     * hide the info view with "delay" milliseconds delay
     * @param delay
     */
        private fun fadeOutInfo(view:View?) {
        if (view?.visibility == View.VISIBLE) {
            view.startAnimation(AnimationUtils.loadAnimation(
                    this@VideoPlayerActivity, android.R.anim.fade_out))
            view.setInvisible()
        }
    }

    /* PlaybackService.Callback */

    override fun update() {
        if (service == null || !::playlistAdapter.isInitialized) return
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
                MediaPlayer.Event.Paused -> updateOverlayPausePlay()
                MediaPlayer.Event.Opening -> forcedTime = -1
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
                            setESTrackLists()
                            lifecycleScope.launch(Dispatchers.IO) {
                                val media = medialibrary.findMedia(mw)
                                val audioTrack = media.getMetaLong(MediaWrapper.META_AUDIOTRACK).toInt()
                                if (audioTrack != 0 || currentAudioTrack != -2)
                                    service.setAudioTrack(if (media.id == 0L) currentAudioTrack else audioTrack)
                            }
                        } else if (event.esChangedType == IMedia.Track.Type.Text) {
                            setESTrackLists()
                            lifecycleScope.launch(Dispatchers.IO) {
                                val media = medialibrary.findMedia(mw)
                                val spuTrack = media.getMetaLong(MediaWrapper.META_SUBTITLE_TRACK).toInt()
                                if (addNextTrack) {
                                    val tracks = service.spuTracks
                                    if (!(tracks as Array<MediaPlayer.TrackDescription>).isNullOrEmpty()) service.setSpuTrack(tracks[tracks.size - 1].id)
                                    addNextTrack = false
                                } else if (spuTrack != 0 || currentSpuTrack != -2) {
                                    service.setSpuTrack(if (media.id == 0L) currentSpuTrack else spuTrack)
                                }
                            }
                        }
                    }
                    if (menuIdx == -1 && event.esChangedType == IMedia.Track.Type.Video) {
                        handler.removeMessages(CHECK_VIDEO_TRACKS)
                        handler.sendEmptyMessageDelayed(CHECK_VIDEO_TRACKS, 1000)
                    }
                    invalidateESTracks(event.esChangedType)
                }
                MediaPlayer.Event.ESDeleted -> {
                    if (menuIdx == -1 && event.esChangedType == IMedia.Track.Type.Video) {
                        handler.removeMessages(CHECK_VIDEO_TRACKS)
                        handler.sendEmptyMessageDelayed(CHECK_VIDEO_TRACKS, 1000)
                    }
                    invalidateESTracks(event.esChangedType)
                }
                MediaPlayer.Event.ESSelected -> if (event.esChangedType == IMedia.Track.Type.Video) {
                    val vt = service.currentVideoTrack
                    if (vt != null)
                        fov = if (vt.projection == IMedia.VideoTrack.Projection.Rectangular) 0f else DEFAULT_FOV
                }
                MediaPlayer.Event.SeekableChanged -> updateSeekable(event.seekable)
                MediaPlayer.Event.PausableChanged -> updatePausable(event.pausable)
                MediaPlayer.Event.Buffering -> {
                    if (isPlaying) {
                        if (event.buffering == 100f)
                            stopLoading()
                        else if (!handler.hasMessages(LOADING_ANIMATION) && !isLoading
                                && (touchDelegate == null || !touchDelegate.isSeeking()) && !isDragging)
                            handler.sendEmptyMessageDelayed(LOADING_ANIMATION, LOADING_ANIMATION_DELAY.toLong())
                    }
                }
            }
        }
    }

    private fun onPlaying() {
        val mw = service?.currentMediaWrapper ?: return
        isPlaying = true
        hasPlaylist = service?.hasPlaylist() == true
        setPlaybackParameters()
        stopLoading()
        updateOverlayPausePlay()
        updateNavStatus()
        if (!mw.hasFlag(MediaWrapper.MEDIA_PAUSED))
            handler.sendEmptyMessageDelayed(FADE_OUT, OVERLAY_TIMEOUT.toLong())
        else {
            mw.removeFlags(MediaWrapper.MEDIA_PAUSED)
            wasPaused = false
        }
        setESTracks()
        if (::hudBinding.isInitialized && hudRightBinding.playerOverlayTitle.length() == 0)
            hudRightBinding.playerOverlayTitle.text = mw.title
        // Get possible subtitles
        observeDownloadedSubtitles()
        optionsDelegate?.setup()
        settings.edit().remove(VIDEO_PAUSED).apply()
        if (isInPictureInPictureMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val track = service?.playlistManager?.player?.mediaplayer?.currentVideoTrack ?: return
            val ar = Rational(track.width.coerceAtMost((track.height * 2.39f).toInt()), track.height)
            if (ar.isFinite && !ar.isZero) {
                setPictureInPictureParams(PictureInPictureParams.Builder().setAspectRatio(ar).build())
            }
        }
    }

    private fun encounteredError() {
        if (isFinishing || service?.hasNext() == true) return
        /* Encountered Error, exit player with a message */
        alertDialog = AlertDialog.Builder(this@VideoPlayerActivity)
                .setOnCancelListener { exit(RESULT_PLAYBACK_ERROR) }
                .setPositiveButton(R.string.ok) { _, _ -> exit(RESULT_PLAYBACK_ERROR) }
                .setTitle(R.string.encountered_error_title)
                .setMessage(R.string.encountered_error_message)
                .create().apply { show() }
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
        // Show the MainActivity if it is not in background.
        if (showUI) {
            val i = Intent().apply {
                setClassName(applicationContext, if (isTv) TV_AUDIOPLAYER_ACTIVITY else MOBILE_MAIN_ACTIVITY)
            }
            startActivity(i)
        }
        exitOK()
    }

    override fun isInPictureInPictureMode(): Boolean {
        return AndroidUtil.isNougatOrLater && super.isInPictureInPictureMode()
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
        return service?.updateViewpoint(yaw, pitch, 0f, fov, false) ?: false
    }

    internal fun initAudioVolume() = service?.let { service ->
        if (service.volume <= 100) {
            volume = audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            originalVol = audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        } else {
            volume = service.volume.toFloat() * audioMax / 100
        }
    }

    fun toggleOverlay() {
        if (!isShowing) showOverlay()
        else hideOverlay(true)
    }

    //Toast that appears only once
    fun displayWarningToast() {
        warningToast?.cancel()
        warningToast = Toast.makeText(application, R.string.audio_boost_warning, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.LEFT or Gravity.BOTTOM, 16.dp,0)
            show()
        }
    }

    internal fun setAudioVolume(vol: Int) {
        var vol = vol
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
                vol = Math.round(vol * 100 / audioMax.toFloat())
            } else {
                vol = Math.round(vol * 100 / audioMax.toFloat())
                service.setVolume(Math.round(vol.toFloat()))
            }
            showVolumeBar(vol)
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
        showInfo(if (isMute) R.string.sound_off else R.string.sound_on, 1000)
    }

    internal fun changeBrightness(delta: Float) {
        // Estimate and adjust Brightness
        val lp = window.attributes
        var brightness = (lp.screenBrightness + delta).coerceIn(0.01f, 1f)
        setWindowBrightness(brightness)
        brightness = (brightness * 100).roundToInt().toFloat()
        showBrightnessBar(brightness.toInt())
    }

    private fun setWindowBrightness(brightness: Float) {
        val lp = window.attributes
        lp.screenBrightness = brightness
        // Set Brightness
        window.attributes = lp
    }

    open fun onAudioSubClick(anchor: View?) {
        overlayDelegate.showTracks()
        hideOverlay(false)
    }

    override fun onPopupMenu(view: View, position: Int, item: MediaWrapper?) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.audio_player, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
            if (item.itemId == R.id.audio_player_mini_remove) service?.run {
                remove(position)
                return@OnMenuItemClickListener true
            }
            false
        })
        popupMenu.show()
    }

    override fun onSelectionSet(position: Int) = playlist.scrollToPosition(position)

    override fun playItem(position: Int, item: MediaWrapper) {
        service?.playIndex(position)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.orientation_toggle -> toggleOrientation()
            R.id.playlist_toggle -> togglePlaylist()
            R.id.player_overlay_forward -> touchDelegate.seekDelta(10000)
            R.id.player_overlay_rewind -> touchDelegate.seekDelta(-10000)
            R.id.ab_repeat_add_marker -> service?.playlistManager?.setABRepeatValue(hudBinding.playerOverlaySeekbar.progress.toLong())
            R.id.ab_repeat_reset -> service?.playlistManager?.resetABRepeatValues()
            R.id.ab_repeat_stop -> service?.playlistManager?.clearABRepeat()
            R.id.player_overlay_navmenu -> showNavMenu()
            R.id.player_overlay_length, R.id.player_overlay_time -> toggleTimeDisplay()
            R.id.video_renderer -> if (supportFragmentManager.findFragmentByTag("renderers") == null)
                RenderersDialog().show(supportFragmentManager, "renderers")
            R.id.video_secondary_display -> {
                clone = displayManager.isSecondary
                recreate()
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        return false
    }

    fun toggleTimeDisplay() {
        sDisplayRemainingTime = !sDisplayRemainingTime
        showOverlay()
        settings.putSingle(KEY_REMAINING_TIME_DISPLAY, sDisplayRemainingTime)
    }

    fun toggleLock() {
        if (isLocked)
            unlockScreen()
        else
            lockScreen()
        updateRendererVisibility()
    }

    fun toggleLoop(v: View) = service?.run {
        if (repeatType == PlaybackStateCompat.REPEAT_MODE_ONE) {
            showInfo(getString(R.string.repeat), 1000)
            repeatType = PlaybackStateCompat.REPEAT_MODE_NONE
        } else {
            repeatType = PlaybackStateCompat.REPEAT_MODE_ONE
            showInfo(getString(R.string.repeat_single), 1000)
        }
        true
    } ?: false

    override fun onStorageAccessGranted() {
        handler.sendEmptyMessage(START_PLAYBACK)
    }

    fun hideOptions() {
        optionsDelegate?.hide()
    }

    private interface TrackSelectedListener {
        fun onTrackSelected(trackID: Int)
    }

    private fun selectTrack(tracks: Array<MediaPlayer.TrackDescription>?, currentTrack: Int, titleId: Int,
                            listener: TrackSelectedListener?) {
        if (listener == null)
            throw IllegalArgumentException("listener must not be null")
        if (tracks == null)
            return
        val nameList = arrayOfNulls<String>(tracks.size)
        val idList = IntArray(tracks.size)
        var listPosition = 0
        for ((i, track) in tracks.withIndex()) {
            idList[i] = track.id
            nameList[i] = track.name
            // map the track position to the list position
            if (track.id == currentTrack) listPosition = i
        }

        if (!isFinishing) alertDialog = AlertDialog.Builder(this@VideoPlayerActivity)
                .setTitle(titleId)
                .setSingleChoiceItems(nameList, listPosition) { dialog, listPosition ->
                    var trackID = -1
                    // Reverse map search...
                    for (track in tracks) {
                        if (idList[listPosition] == track.id) {
                            trackID = track.id
                            break
                        }
                    }
                    listener.onTrackSelected(trackID)
                    dialog.dismiss()
                }
                .setOnDismissListener { this.dimStatusBar(true) }
                .create().apply {
                    setCanceledOnTouchOutside(true)
                    setOwnerActivity(this@VideoPlayerActivity)
                    show()
                }
    }

    fun selectVideoTrack() {
        setESTrackLists()
        service?.let {
            selectTrack(videoTracksList, it.videoTrack, R.string.track_video,
                    object : TrackSelectedListener {
                        override fun onTrackSelected(trackID: Int) {
                            if (trackID < -1) return
                            service?.let { service ->
                                service.setVideoTrack(trackID)
                                seek(service.time)
                            }
                        }
                    })
        }
    }

    fun selectAudioTrack() {
        setESTrackLists()
        service?.let {
            selectTrack(audioTracksList, it.audioTrack, R.string.track_audio,
                    object : TrackSelectedListener {
                        override fun onTrackSelected(trackID: Int) {
                            if (trackID < -1) return
                            service?.let { service ->
                                service.setAudioTrack(trackID)
                                runIO(Runnable {
                                    val mw = medialibrary.findMedia(service.currentMediaWrapper)
                                    if (mw != null && mw.id != 0L) mw.setLongMeta(MediaWrapper.META_AUDIOTRACK, trackID.toLong())
                                })
                            }
                        }
                    })
        }
    }

    fun selectSubtitles() {
        setESTrackLists()
        service?.let {
            selectTrack(subtitleTracksList, it.spuTrack, R.string.track_text,
                    object : TrackSelectedListener {
                        override fun onTrackSelected(trackID: Int) {
                            if (trackID < -1 || service == null) return
                            runIO(Runnable { setSpuTrack(trackID) })
                        }
                    })
        }
    }

    fun pickSubtitles() {
        val uri = videoUri?: return
        isShowingDialog = true
        val filePickerIntent = Intent(this, FilePickerActivity::class.java)
        filePickerIntent.data = Uri.parse(FileUtils.getParent(uri.toString()))
        startActivityForResult(filePickerIntent, 0)
    }

    fun downloadSubtitles() = service?.currentMediaWrapper?.let {
        MediaUtils.getSubs(this@VideoPlayerActivity, it)
    }

    @WorkerThread
    private fun setSpuTrack(trackID: Int) {
        runOnMainThread(Runnable { service?.setSpuTrack(trackID) })
        val mw = medialibrary.findMedia(service?.currentMediaWrapper) ?: return
        if (mw.id != 0L) mw.setLongMeta(MediaWrapper.META_SUBTITLE_TRACK, trackID.toLong())
    }

    private fun showNavMenu() {
        if (menuIdx >= 0) service?.titleIdx = menuIdx
    }

    private fun updateSeekable(seekable: Boolean) {
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
        if (!isLocked)
            hudBinding.playerOverlaySeekbar.isEnabled = seekable
    }

    private fun updatePausable(pausable: Boolean) {
        if (!::hudBinding.isInitialized) return
        hudBinding.playerOverlayPlay.isEnabled = pausable
        if (!pausable)
            hudBinding.playerOverlayPlay.setImageResource(R.drawable.ic_play_player_disabled)
    }

    fun doPlayPause() {
        if (service?.isPausable != true) return
        if (service?.isPlaying == true) {
            showOverlayTimeout(OVERLAY_INFINITE)
            pause()
        } else {
            handler.sendEmptyMessageDelayed(FADE_OUT, 300L)
            play()
        }
    }

    fun seek(position: Long) {
        service?.let { seek(position, it.length) }
    }

    internal fun seek(position: Long, length: Long) {
        service?.let { service ->
            forcedTime = position
            lastTime = service.time
            service.seek(position, length.toDouble())
            service.playlistManager.player.updateProgress(position)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSeekButton() {
        if (!::hudBinding.isInitialized) return
        hudBinding.playerOverlayRewind.setOnClickListener(this)
        hudBinding.playerOverlayForward.setOnClickListener(this)
        hudBinding.playerOverlayRewind.setOnTouchListener(OnRepeatListener(this))
        hudBinding.playerOverlayForward.setOnTouchListener(OnRepeatListener(this))
    }

    fun resizeVideo() = service?.run {
        val next = (mediaplayer.videoScale.ordinal + 1) % MediaPlayer.SURFACE_SCALES_COUNT
        val scale = MediaPlayer.ScaleType.values()[next]
        setVideoScale(scale)
        handler.sendEmptyMessage(SHOW_INFO)
    }

    internal fun setVideoScale(scale: MediaPlayer.ScaleType) = service?.run {
        mediaplayer.videoScale = scale
        when (scale) {
            MediaPlayer.ScaleType.SURFACE_BEST_FIT -> showInfo(R.string.surface_best_fit, 1000)
            MediaPlayer.ScaleType.SURFACE_FIT_SCREEN -> showInfo(R.string.surface_fit_screen, 1000)
            MediaPlayer.ScaleType.SURFACE_FILL -> showInfo(R.string.surface_fill, 1000)
            MediaPlayer.ScaleType.SURFACE_16_9 -> showInfo("16:9", 1000)
            MediaPlayer.ScaleType.SURFACE_4_3 -> showInfo("4:3", 1000)
            MediaPlayer.ScaleType.SURFACE_ORIGINAL -> showInfo(R.string.surface_original, 1000)
        }
        settings.putSingle(VIDEO_RATIO, scale.ordinal)
    }

    /**
     * show overlay
     * @param forceCheck: adjust the timeout in function of playing state
     */
    private fun showOverlay(forceCheck: Boolean = false) {
        if (forceCheck) overlayTimeout = 0
        showOverlayTimeout(0)
    }

    /**
     * show overlay
     */
    fun showOverlayTimeout(timeout: Int) {
        service?.let { service ->
            if (isInPictureInPictureMode) return
            initOverlay()
            if (!::hudBinding.isInitialized) return
            overlayTimeout = when {
                timeout != 0 -> timeout
                service.isPlaying -> OVERLAY_TIMEOUT
                else -> OVERLAY_INFINITE
            }
            if (isNavMenu) {
                isShowing = true
                return
            }
            if (!isShowing) {
                isShowing = true
                if (!isLocked) {
                    showControls(true)
                }
                dimStatusBar(false)

                enterAnimate(arrayOf(hudBinding.progressOverlay, hud_background), 100.dp.toFloat())
                enterAnimate(arrayOf(hudRightBinding.hudRightOverlay, hud_right_background), -100.dp.toFloat())

                if (!displayManager.isPrimary)
                    overlayBackground.setVisible()
                updateOverlayPausePlay(true)
            }
            handler.removeMessages(FADE_OUT)
            if (overlayTimeout != OVERLAY_INFINITE)
                handler.sendMessageDelayed(handler.obtainMessage(FADE_OUT), overlayTimeout.toLong())
        }
    }

    private fun showControls(show: Boolean) {
        if (show && isInPictureInPictureMode) return
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
            hudBinding.orientationToggle.visibility = if (isTv || AndroidDevices.isChromeBook) View.INVISIBLE else if (show) View.VISIBLE else View.INVISIBLE
        }
        if (::hudRightBinding.isInitialized) {
            val secondary = displayManager.isSecondary
            if (secondary) hudRightBinding.videoSecondaryDisplay.setImageResource(R.drawable.ic_player_screenshare_stop)
            hudRightBinding.videoSecondaryDisplay.visibility = if (!show) View.GONE else if (UiTools.hasSecondaryDisplay(applicationContext)) View.VISIBLE else View.GONE
            hudRightBinding.videoSecondaryDisplay.contentDescription = resources.getString(if (secondary) R.string.video_remote_disable else R.string.video_remote_enable)

            hudRightBinding.playlistToggle.visibility = if (show && service?.hasPlaylist() == true) View.VISIBLE else View.GONE
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun initOverlay() {
        service?.let { service ->
            val vscRight = findViewById<ViewStubCompat>(R.id.player_hud_right_stub)
            vscRight?.let {
                it.setVisible()
                hudRightBinding = DataBindingUtil.bind(findViewById(R.id.hud_right_overlay)) ?: return
                if (!isBenchmark && enableCloneMode && !settings.contains("enable_clone_mode")) {
                    UiTools.snackerConfirm(hudRightBinding.videoSecondaryDisplay, getString(R.string.video_save_clone_mode), Runnable { settings.putSingle("enable_clone_mode", true) })
                }
            }

            val vsc = findViewById<ViewStubCompat>(R.id.player_hud_stub)
            if (vsc != null) {
                seekButtons = settings.getBoolean(ENABLE_SEEK_BUTTONS, false)
                vsc.setVisible()
                hudBinding = DataBindingUtil.bind(findViewById(R.id.progress_overlay)) ?: return
                hudBinding.player = this
                hudBinding.progress = service.playlistManager.player.progress
                abRepeatAddMarker = hudBinding.abRepeatContainer.findViewById(R.id.ab_repeat_add_marker)
                service.playlistManager.abRepeat.observe(this, Observer { abvalues ->
                    hudBinding.abRepeatA = if (abvalues.start == -1L) -1F else abvalues.start / service.playlistManager.player.getLength().toFloat()
                    hudBinding.abRepeatB = if (abvalues.stop == -1L) -1F else abvalues.stop / service.playlistManager.player.getLength().toFloat()
                    hudBinding.abRepeatMarkerA.visibility = if (abvalues.start == -1L) View.GONE else View.VISIBLE
                    hudBinding.abRepeatMarkerB.visibility = if (abvalues.stop == -1L) View.GONE else View.VISIBLE
                    service.manageAbRepeatStep(hudBinding.abRepeatReset, hudBinding.abRepeatStop, hudBinding.abRepeatContainer, abRepeatAddMarker)
                    showOverlayTimeout(if (abvalues.start == -1L || abvalues.stop == -1L) OVERLAY_INFINITE else OVERLAY_TIMEOUT)
                })
                service.playlistManager.abRepeatOn.observe(this, Observer {
                    hudBinding.abRepeatMarkerGuidelineContainer.visibility = if (it) View.VISIBLE else View.GONE
                    if (it) showOverlay(true)
                    if (it) {
                        hudBinding.playerOverlayLength.nextFocusUpId = R.id.ab_repeat_add_marker
                        hudBinding.playerOverlayTime.nextFocusUpId = R.id.ab_repeat_add_marker
                    }
                    if (it) showOverlayTimeout(OVERLAY_INFINITE)

                    service.manageAbRepeatStep(hudBinding.abRepeatReset, hudBinding.abRepeatStop, hudBinding.abRepeatContainer, abRepeatAddMarker)
                })
                service.playlistManager.delayValue.observe(this, Observer {
                    delayDelegate.delayChanged(it, service)
                })
                service.playlistManager.videoStatsOn.observe(this, Observer {
                    if (it) showOverlay(true)
                    statsDelegate.container = hudBinding.statsContainer
                    statsDelegate.initPlotView(hudBinding)
                    if (it) statsDelegate.start() else statsDelegate.stop()
                })
                hudBinding.statsClose.setOnClickListener { service.playlistManager.videoStatsOn.postValue(false) }

                hudBinding.lifecycleOwner = this
                updateOrientationIcon()
                overlayBackground = findViewById(R.id.player_overlay_background)
                navMenu = findViewById(R.id.player_overlay_navmenu)
                if (!AndroidDevices.isChromeBook && !isTv
                        && Settings.getInstance(this).getBoolean("enable_casting", true)) {
                    PlaybackService.renderer.observe(this, Observer { rendererItem -> hudRightBinding.videoRenderer.setImageDrawable(AppCompatResources.getDrawable(this, if (rendererItem == null) R.drawable.ic_player_renderer else R.drawable.ic_player_renderer_on)) })
                    RendererDelegate.renderers.observe(this, Observer<List<RendererItem>> { rendererItems -> updateRendererVisibility() })
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
                updateNavStatus()
                setListeners(true)
                initPlaylistUi()
            } else if (::hudBinding.isInitialized) {
                hudBinding.progress = service.playlistManager.player.progress
                hudBinding.lifecycleOwner = this
            }
        }
    }

    private fun updateRendererVisibility() {
        if (::hudRightBinding.isInitialized) hudRightBinding.videoRenderer.visibility = if (isLocked || RendererDelegate.renderers.value.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private val titleConstraintSetLandscape = ConstraintSet()
    private val titleConstraintSetPortrait = ConstraintSet()
    private fun manageTitleConstraints() {
       titleConstraintSetLandscape.clone(hudRightBinding.hudRightOverlay)
       titleConstraintSetPortrait.clone(hudRightBinding.hudRightOverlay)
        titleConstraintSetPortrait.setMargin(hudRightBinding.playerOverlayTitle.id, ConstraintSet.START, 16.dp)
        titleConstraintSetPortrait.setMargin(hudRightBinding.playerOverlayTitle.id, ConstraintSet.END, 16.dp)
        titleConstraintSetPortrait.connect(hudRightBinding.playerOverlayTitle.id, ConstraintSet.TOP, hudRightBinding.playerOverlayNavmenu.id, ConstraintSet.BOTTOM, 0.dp)
    }

    private fun updateTitleConstraints() {
        if (::hudRightBinding.isInitialized) when (resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> titleConstraintSetPortrait
            else -> titleConstraintSetLandscape
        }.applyTo(hudRightBinding.hudRightOverlay)
    }


    private fun updateHudMargins() {
        //here, we override the default Android overscan
        val overscanHorizontal = if (isTv) 32.dp else 0
        val overscanVertical = if (isTv) resources.getDimension(R.dimen.tv_overscan_vertical).toInt() else 0
        if (::hudBinding.isInitialized) {
            val largeMargin = resources.getDimension(R.dimen.large_margins_center)
            val smallMargin = resources.getDimension(R.dimen.small_margins_sides)
            applyMargin(hudBinding.playlistPrevious, largeMargin.toInt(), true)
            applyMargin(hudBinding.playerOverlayRewind, largeMargin.toInt(), true)
            applyMargin(hudBinding.playlistNext, largeMargin.toInt(), false)
            applyMargin(hudBinding.playerOverlayForward, largeMargin.toInt(), false)

            applyMargin(hudBinding.playerOverlayTracks, if (!isTv) smallMargin.toInt() else overscanHorizontal, false)
            applyMargin(hudBinding.orientationToggle, smallMargin.toInt(), false)
            applyMargin(hudBinding.playerResize, smallMargin.toInt(), true)
            applyMargin(hudBinding.playerOverlayAdvFunction, if (!isTv) smallMargin.toInt() else overscanHorizontal, true)

            hudBinding.playerOverlaySeekbar.setPadding(overscanHorizontal, 0, overscanHorizontal, 0)

            if (isTv) {
                applyMargin(hudBinding.playerOverlayTimeContainer, overscanHorizontal, false)
                applyMargin(hudBinding.playerOverlayLengthContainer, overscanHorizontal, true)
            }

            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                hudBinding.playerSpaceLeft.setGone()
                hudBinding.playerSpaceRight.setGone()
            } else {
                hudBinding.playerSpaceLeft.setVisible()
                hudBinding.playerSpaceRight.setVisible()
            }
        }
        if (::hudRightBinding.isInitialized) {
            applyVerticalMargin(hudRightBinding.playerOverlayTitle, overscanVertical, false)
        }
    }

    private fun applyMargin(view: View, margin: Int, isEnd: Boolean) = (view.layoutParams as ConstraintLayout.LayoutParams).apply {
        if (isEnd) marginEnd = margin else marginStart = margin
        view.layoutParams = this
    }

    private fun applyVerticalMargin(view: View, margin: Int, isBottom: Boolean) = (view.layoutParams as ConstraintLayout.LayoutParams).apply {
        if (isBottom) bottomMargin = margin else topMargin = margin
        view.layoutParams = this
    }

    /**
     * hider overlay
     */
    fun hideOverlay(fromUser: Boolean) {
        if (isShowing) {
            handler.removeMessages(FADE_OUT)
            Log.v(TAG, "remove View!")
            overlayTips.setInvisible()
            if (!displayManager.isPrimary) {
                overlayBackground?.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out))
                overlayBackground.setInvisible()
            }

            exitAnimate(arrayOf(hudBinding.progressOverlay, hud_background),100.dp.toFloat())
            exitAnimate(arrayOf(hudRightBinding.hudRightOverlay, hud_right_background),-100.dp.toFloat())

            showControls(false)
            isShowing = false
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

    /**
     * Dim the status bar and/or navigation icons when needed on Android 3.x.
     * Hide it on Android 4.0 and later
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun dimStatusBar(dim: Boolean) {
        if (isNavMenu) return

        var visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        var navbar = 0
        if (dim || isLocked) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            navbar = navbar or (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LOW_PROFILE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            if (AndroidUtil.isKitKatOrLater) visibility = visibility or View.SYSTEM_UI_FLAG_IMMERSIVE
            visibility = visibility or View.SYSTEM_UI_FLAG_FULLSCREEN
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            visibility = visibility or View.SYSTEM_UI_FLAG_VISIBLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        player_ui_container?.setPadding(0, 0, 0, 0)
        player_ui_container?.fitsSystemWindows = !isLocked

        if (AndroidDevices.hasNavBar)
            visibility = visibility or navbar
        window.decorView.systemUiVisibility = visibility
    }

    private fun showTitle() {
        if (isNavMenu) return

        var visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        var navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        navbar = navbar or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        if (AndroidDevices.hasNavBar) visibility = visibility or navbar
        window.decorView.systemUiVisibility = visibility
    }

    private fun updateOverlayPausePlay(skipAnim: Boolean = false) {
        if (!::hudBinding.isInitialized) return
        service?.let { service ->
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

    private fun invalidateESTracks(type: Int) {
        when (type) {
            IMedia.Track.Type.Audio -> audioTracksList = null
            IMedia.Track.Type.Text -> subtitleTracksList = null
        }
    }

    private fun setESTracks() {
        if (lastAudioTrack >= -1) {
            service?.setAudioTrack(lastAudioTrack)
            lastAudioTrack = -2
        }
        if (lastSpuTrack >= -1) {
            service?.setSpuTrack(lastSpuTrack)
            lastSpuTrack = -2
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setESTrackLists() {
        service?.let { service ->
            if (audioTracksList == null && service.audioTracksCount > 0)
                audioTracksList = service.audioTracks as Array<MediaPlayer.TrackDescription>?
            if (subtitleTracksList == null && service.spuTracksCount > 0)
                subtitleTracksList = service.spuTracks as Array<MediaPlayer.TrackDescription>?
            if (videoTracksList == null && service.videoTracksCount > 0)
                videoTracksList = service.videoTracks as Array<MediaPlayer.TrackDescription>?
        }
    }

    /**
     *
     */
    private fun play() {
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

    fun next() {
        service?.next()
    }

    fun previous() {
        service?.previous(false)
    }

    /*
     * Additionnal method to prevent alert dialog to pop up
     */
    private fun loadMedia(fromStart: Boolean) {
        askResume = false
        intent.putExtra(PLAY_EXTRA_FROM_START, fromStart)
        loadMedia()
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
    protected open fun loadMedia() {
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
            val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (km.inKeyguardRestrictedInputMode())
                wasPaused = true
            if (wasPaused && BuildConfig.DEBUG)
                Log.d(TAG, "Video was previously paused, resuming in paused mode")
            if (intent.data != null) videoUri = intent.data
            if (extras != null) {
                if (intent.hasExtra(PLAY_EXTRA_ITEM_LOCATION)) {
                    videoUri = extras.getParcelable(PLAY_EXTRA_ITEM_LOCATION)
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

                val path = extras.getString(PLAY_EXTRA_SUBTITLES_LOCATION)
                if (!path.isNullOrEmpty()) service.addSubtitleTrack(path, true)
                if (intent.hasExtra(PLAY_EXTRA_ITEM_TITLE))
                    itemTitle = extras.getString(PLAY_EXTRA_ITEM_TITLE)
            }
            if (startTime == 0L && savedTime > 0L) startTime = savedTime
            val restorePlayback = hasMedia && currentMedia?.uri == videoUri

            var openedMedia: MediaWrapper? = null
            val resumePlaylist = service.isValidIndex(positionInPlaylist)
            val continueplayback = isPlaying && (restorePlayback || positionInPlaylist == service.currentMediaPosition)
            if (resumePlaylist) {
                // Provided externally from AudioService
                if (BuildConfig.DEBUG) Log.v(TAG, "Continuing playback from PlaybackService at index $positionInPlaylist")
                openedMedia = service.media[positionInPlaylist]
                itemTitle = openedMedia.title
                updateSeekable(service.isSeekable)
                updatePausable(service.isPausable)
            }
            if (videoUri != null) {
                var uri = videoUri ?:return
                var media: MediaWrapper? = null
                if (!continueplayback) {
                    if (!resumePlaylist) {
                        // restore last position
                        media = medialibrary.getMedia(uri)
                        if (media == null && TextUtils.equals(uri.scheme, "file") &&
                                uri.path?.startsWith("/sdcard") == true) {
                            uri = FileUtils.convertLocalUri(uri)
                            videoUri = uri
                            media = medialibrary.getMedia(uri)
                        }
                        if (media != null && media.id != 0L && media.time == 0L)
                            media.time = media.getMetaLong(MediaWrapper.META_PROGRESS)
                    } else media = openedMedia
                    if (media != null) {
                        // in media library
                        if (askResume && !fromStart && positionInPlaylist <= 0 && media.time > 0) {
                            showConfirmResumeDialog()
                            return
                        }

                        lastAudioTrack = media.audioTrack
                        lastSpuTrack = media.spuTrack
                    } else if (!fromStart) {
                        // not in media library
                        if (askResume && startTime > 0L) {
                            showConfirmResumeDialog()
                            return
                        } else {
                            val rTime = settings.getLong(VIDEO_RESUME_TIME, -1)
                            if (rTime > 0) {
                                if (askResume) {
                                    showConfirmResumeDialog()
                                    return
                                } else {
                                    settings.putSingle(VIDEO_RESUME_TIME, -1L)
                                    startTime = rTime
                                }
                            }
                        }
                    }
                }

                // Start playback & seek
                /* prepare playback */
                val medialoaded = media != null
                if (!medialoaded) media = if (hasMedia) currentMedia else MLServiceLocator.getAbstractMediaWrapper(uri)
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
                service.loadLastPlaylist(PLAYLIST_TYPE_VIDEO)
            }
            if (itemTitle != null) title = itemTitle
            if (::hudBinding.isInitialized) {
                hudRightBinding.playerOverlayTitle.text = title
            }

            if (wasPaused) {
                // XXX: Workaround to update the seekbar position
                forcedTime = startTime
                forcedTime = -1
                showOverlay(true)
            }
            enableSubs()
        }
    }

    private fun enableSubs() {
        videoUri?.let {
            val lastPath = it.lastPathSegment ?: return
            enableSubs = (!TextUtils.isEmpty(lastPath) && !lastPath.endsWith(".ts") && !lastPath.endsWith(".m2ts")
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
    private fun getScreenOrientation(mode: PlayerOrientationMode): Int {
        return if (!mode.locked) {
            if (AndroidUtil.isJellyBeanMR2OrLater)
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            mode.orientation
        }
    }

    private fun getOrientationForLock(): Int {
        val wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
                Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                Surface.ROTATION_180 ->
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                Surface.ROTATION_270 ->
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                else -> 0
            }
        } else {
            when (rot) {
                Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                Surface.ROTATION_180 ->
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                Surface.ROTATION_270 ->
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else -> 0
            }
        }
    }

    private fun showConfirmResumeDialog() {
        if (isFinishing) return
        service?.pause()
        /* Encountered Error, exit player with a message */
        alertDialog = AlertDialog.Builder(this@VideoPlayerActivity)
                .setMessage(R.string.confirm_resume)
                .setPositiveButton(R.string.resume_from_position) { _, _ -> loadMedia(false) }
                .setNegativeButton(R.string.play_from_start) { _, _ -> loadMedia(true) }
                .create().apply {
                    setCancelable(false)
                    setOnKeyListener(DialogInterface.OnKeyListener { dialog, keyCode, _ ->
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            dialog.dismiss()
                            finish()
                            return@OnKeyListener true
                        }
                        false
                    })
                    show()
                }
    }

    fun showAdvancedOptions() {
        if (optionsDelegate == null) service?.let { optionsDelegate = PlayerOptionsDelegate(this, it) }
        optionsDelegate?.show()
        hideOverlay(false)
    }

    private fun toggleOrientation() {
        orientationMode.locked = !orientationMode.locked
        orientationMode.orientation = getOrientationForLock()

        requestedOrientation = getScreenOrientation(orientationMode)
        updateOrientationIcon()
    }

    private fun updateOrientationIcon() {
        if (::hudBinding.isInitialized) {
            val drawable = if (!orientationMode.locked) {
                R.drawable.ic_player_rotate
            } else if (orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                R.drawable.ic_player_lock_landscape
            } else {
                R.drawable.ic_player_lock_portrait
            }
            hudBinding.orientationToggle.setImageDrawable(ContextCompat.getDrawable(this, drawable))
        }
    }

    internal fun togglePlaylist() {
        if (isPlaylistVisible) {
            playlistContainer.setGone()
            playlist.setOnClickListener(null)
            return
        }
        hideOverlay(true)
        playlistContainer.setVisible()
        playlist.adapter = playlistAdapter
        update()
    }

    private fun toggleBtDelay(connected: Boolean) {
        service?.setAudioDelay(if (connected) settings.getLong(KEY_BLUETOOTH_DELAY, 0) else 0L)
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

    fun onClickOverlayTips(@Suppress("UNUSED_PARAMETER") v: View) {
        overlayTips.setGone()
    }

    fun onClickDismissTips(@Suppress("UNUSED_PARAMETER") v: View) {
        overlayTips.setGone()
        settings.putSingle(PREF_TIPS_SHOWN, true)
    }

    private fun updateNavStatus() {
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
                    (it.titles[it.title])?.isInteractive ?: false
                } ?: false
                isNavMenu = menuIdx == currentIdx || interactive
            }

            if (isNavMenu) {
                /*
                         * Keep the overlay hidden in order to have touch events directly
                         * transmitted to navigation handling.
                         */
                hideOverlay(false)
            } else if (menuIdx != -1) setESTracks()

            navMenu.setVisibility(if (menuIdx >= 0 && navMenu != null) View.VISIBLE else View.INVISIBLE)
            supportInvalidateOptionsMenu()
        }
    }

    open fun onServiceChanged(service: PlaybackService?) {
        if (service != null) {
            this.service = service
            //We may not have the permission to access files
            if (Permissions.checkReadStoragePermission(this, true) && !switchingView)
                handler.sendEmptyMessage(START_PLAYBACK)
            switchingView = false
            handler.post {
                // delay mediaplayer loading, prevent ANR
                if (service.volume > 100 && !isAudioBoostEnabled) service.setVolume(100)
            }
            service.addCallback(this)
        } else if (this.service != null) {
            this.service?.removeCallback(this)
            this.service = null
            handler.sendEmptyMessage(AUDIO_SERVICE_CONNECTION_FAILED)
            removeDownloadedSubtitlesObserver()
            previousMediaPath = null
        }
    }

    companion object {

        private const val TAG = "VLC/VideoPlayerActivity"

        private val ACTION_RESULT = "player.result".buildPkgString()
        private const val EXTRA_POSITION = "extra_position"
        private const val EXTRA_DURATION = "extra_duration"
        private const val EXTRA_URI = "extra_uri"
        private const val RESULT_CONNECTION_FAILED = Activity.RESULT_FIRST_USER + 1
        private const val RESULT_PLAYBACK_ERROR = Activity.RESULT_FIRST_USER + 2
        private const val RESULT_VIDEO_TRACK_LOST = Activity.RESULT_FIRST_USER + 3
        internal const val DEFAULT_FOV = 80f
        private const val KEY_TIME = "saved_time"
        private const val KEY_LIST = "saved_list"
        private const val KEY_URI = "saved_uri"
        private const val OVERLAY_TIMEOUT = 4000
        const val OVERLAY_INFINITE = -1
        private const val FADE_OUT = 1
        private const val FADE_OUT_INFO = 2
        private const val START_PLAYBACK = 3
        private const val AUDIO_SERVICE_CONNECTION_FAILED = 4
        private const val RESET_BACK_LOCK = 5
        private const val CHECK_VIDEO_TRACKS = 6
        private const val LOADING_ANIMATION = 7
        internal const val SHOW_INFO = 8
        internal const val HIDE_INFO = 9
        internal const val HIDE_SEEK = 10
        internal const val HIDE_SETTINGS = 11
        private const val FADE_OUT_BRIGHTNESS_INFO = 12
        private const val FADE_OUT_VOLUME_INFO = 13
        private const val KEY_REMAINING_TIME_DISPLAY = "remaining_time_display"
        const val KEY_BLUETOOTH_DELAY = "key_bluetooth_delay"

        private const val LOADING_ANIMATION_DELAY = 1000
        @Volatile
        internal var sDisplayRemainingTime: Boolean = false
        private const val PREF_TIPS_SHOWN = "video_player_tips_shown"

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
            context.startActivity(intent)
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

data class PlayerOrientationMode (
    var locked:Boolean = false,
    var orientation:Int = 0
)

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
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
    view.max = length.toInt()
}
/**
 * hide the info view
 */
/**
 * show overlay with the previous timeout value
 */
