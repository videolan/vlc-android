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

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
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
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.*
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup.LayoutParams
import android.view.animation.*
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.ViewStubCompat
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.circularreveal.CircularRevealCompat
import com.google.android.material.circularreveal.CircularRevealWidget
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.android.synthetic.main.player_overlay_seek.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.libvlc.util.DisplayManager
import org.videolan.libvlc.util.VLCVideoLayout
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.tools.*
import org.videolan.vlc.*
import org.videolan.vlc.database.models.ExternalSub
import org.videolan.vlc.databinding.PlayerHudBinding
import org.videolan.vlc.databinding.PlayerHudRightBinding
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.audio.PlaylistAdapter
import org.videolan.vlc.gui.browser.EXTRA_MRL
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.dialogs.RenderersDialog
import org.videolan.vlc.gui.helpers.*
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity
import org.videolan.vlc.interfaces.IPlaybackSettingsController
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.repository.ExternalSubRepository
import org.videolan.vlc.repository.SlaveRepository
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.PlaylistModel
import java.util.*

@Suppress("DEPRECATION")
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
open class VideoPlayerActivity : AppCompatActivity(), IPlaybackSettingsController, PlaybackService.Callback, PlaylistAdapter.IPlayer, OnClickListener, OnLongClickListener, StoragePermissionsDelegate.CustomActionController, Observer<PlaybackService>, TextWatcher {


    private var wasPlaying = true
    private val controlsConstraintSetPortrait = ConstraintSet()
    private val controlsConstraintSetLandscape = ConstraintSet()
    var service: PlaybackService? = null
    private lateinit var medialibrary: AbstractMedialibrary
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


    private lateinit var settings: SharedPreferences

    /** Overlay  */
    private var overlayBackground: View? = null

    private var isDragging: Boolean = false
    internal var isShowing: Boolean = false
        private set
    private var isShowingDialog: Boolean = false
    private var playbackSetting: IPlaybackSettingsController.DelayState = IPlaybackSettingsController.DelayState.OFF
    private var info: TextView? = null
    private var overlayInfo: View? = null
    private var verticalBar: View? = null
    private lateinit var verticalBarProgress: View
    private lateinit var verticalBarBoostProgress: View
    internal var isLoading: Boolean = false
        private set
    private var isPlaying = false
    private var loadingImageView: ImageView? = null
    private var navMenu: ImageView? = null
    private var rendererBtn: ImageView? = null
    private var playbackSettingPlus: ImageView? = null
    private var playbackSettingMinus: ImageView? = null
    protected var enableCloneMode: Boolean = false
    private var screenOrientation: Int = 0
    private var screenOrientationLock: Int = 0
    private var currentScreenOrientation: Int = 0

    private var spuDelay = 0L
    private var audioDelay = 0L
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
    private var touchDelegate: VideoTouchDelegate? = null
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

    private val addedExternalSubs = ArrayList<ExternalSub>()
    private var downloadedSubtitleLiveData: LiveData<List<ExternalSub>>? = null
    private var previousMediaPath: String? = null

    private val isInteractive: Boolean
        @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
        get() {
            val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            return if (AndroidUtil.isLolliPopOrLater) pm.isInteractive else pm.isScreenOn
        }

    private val playlistObserver = Observer<List<AbstractMediaWrapper>> { mediaWrappers -> if (mediaWrappers != null) playlistAdapter.update(mediaWrappers) }

    private var addNextTrack = false


    private lateinit var playToPause: AnimatedVectorDrawableCompat
    private lateinit var pauseToPlay: AnimatedVectorDrawableCompat

    internal val isPlaybackSettingActive: Boolean
        get() = playbackSetting != IPlaybackSettingsController.DelayState.OFF

    /**
     * Handle resize of the surface and the overlay
     */
    val handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            service?.run {
                when (msg.what) {
                    FADE_OUT -> hideOverlay(false)
                    FADE_OUT_INFO -> fadeOutInfo()
                    START_PLAYBACK -> startPlayback()
                    AUDIO_SERVICE_CONNECTION_FAILED -> exit(RESULT_CONNECTION_FAILED)
                    RESET_BACK_LOCK -> lockBackButton = true
                    CHECK_VIDEO_TRACKS -> if (videoTracksCount < 1 && audioTracksCount > 0) {
                        Log.i(TAG, "No video track, open in audio mode")
                        switchToAudioMode(true)
                    }
                    LOADING_ANIMATION -> startLoading()
                    HIDE_INFO -> hideOverlay(true)
                    SHOW_INFO -> showOverlay()
                    HIDE_SEEK -> hideSeekOverlay()
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

    val isOptionsListShowing: Boolean
        get() = optionsDelegate != null && optionsDelegate!!.isShowing()

    /* XXX: After a seek, playbackService.getTime can return the position before or after
             * the seek position. Therefore we return forcedTime in order to avoid the seekBar
             * to move between seek position and the actual position.
             * We have to wait for a valid position (that is after the seek position).
             * to re-init lastTime and forcedTime to -1 and return the actual position.
             */
    private val time: Long
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

    private var enableSubs = true

    private val downloadedSubtitleObserver = Observer<List<ExternalSub>> { externalSubs ->
        for (externalSub in externalSubs!!) {
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
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
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

    private val btSaveListener = OnClickListener {
        service?.run {
            settings.edit().putLong(KEY_BLUETOOTH_DELAY, service?.audioDelay ?: 0L).apply()
        }
    }

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (TextUtils.equals(intent.action, PLAY_FROM_SERVICE))
                onNewIntent(intent)
            else if (TextUtils.equals(intent.action, EXIT_PLAYER))
                exitOK()
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Util.checkCpuCompatibility(this)

        settings = Settings.getInstance(this)

        /* Services and miscellaneous */
        audiomanager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioMax = audiomanager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        isAudioBoostEnabled = settings.getBoolean("audio_boost", false)

        enableCloneMode = if (clone != null) clone!! else settings.getBoolean("enable_clone_mode", false)
        displayManager = DisplayManager(this, PlaybackService.renderer, false, enableCloneMode, isBenchmark)
        setContentView(if (displayManager.isPrimary) R.layout.player else R.layout.player_remote_control)


        rootView = findViewById(R.id.player_root)


        playlist = findViewById(R.id.video_playlist)
        playlistSearchText = findViewById(R.id.playlist_search_text)
        playlistContainer = findViewById(R.id.video_playlist_container)
        closeButton = findViewById(R.id.close_button)
        playlistSearchText.editText?.addTextChangedListener(this)



        screenOrientation = Integer.valueOf(
                settings.getString(SCREEN_ORIENTATION, "99" /*SCREEN ORIENTATION SENSOR*/)!!)

        videoLayout = findViewById(R.id.video_layout)

        /* Loading view */
        loadingImageView = findViewById(R.id.player_overlay_loading)
        dimStatusBar(true)
        handler.sendEmptyMessageDelayed(LOADING_ANIMATION, LOADING_ANIMATION_DELAY.toLong())

        switchingView = false

        askResume = settings.getBoolean("dialog_confirm_resume", false)
        sDisplayRemainingTime = settings.getBoolean(KEY_REMAINING_TIME_DISPLAY, false)
        // Clear the resume time, since it is only used for resumes in external
        // videos.
        val editor = settings.edit()
        editor.putLong(VIDEO_RESUME_TIME, -1)
        // Paused flag - per session too, like the subs list.
        editor.apply()

        this.volumeControlStream = AudioManager.STREAM_MUSIC

        // 100 is the value for screen_orientation_start_lock
        try {
            requestedOrientation = getScreenOrientation(screenOrientation)
        } catch (ignored: IllegalStateException) {
            Log.w(TAG, "onCreate: failed to set orientation")
        }

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

            //Set margins for TV overscan
            if (isTv) {
                val hm = resources.getDimensionPixelSize(R.dimen.tv_overscan_horizontal)
                val vm = resources.getDimensionPixelSize(R.dimen.tv_overscan_vertical)

                val uiContainer = findViewById<RelativeLayout>(R.id.player_ui_container)
                val lp = uiContainer.layoutParams as RelativeLayout.LayoutParams
                lp.setMargins(hm, 0, hm, vm)
                uiContainer.layoutParams = lp

            }
        }


        medialibrary = AbstractMedialibrary.getInstance()
        val touch = if (!isTv) {
            val audioTouch = (!AndroidUtil.isLolliPopOrLater || !audiomanager.isVolumeFixed) && settings.getBoolean(ENABLE_VOLUME_GESTURE, true)
            val brightnessTouch = !AndroidDevices.isChromeBook && settings.getBoolean(ENABLE_BRIGHTNESS_GESTURE, true)
            ((if (audioTouch) TOUCH_FLAG_AUDIO_VOLUME else 0)
                    + (if (brightnessTouch) TOUCH_FLAG_BRIGHTNESS else 0)
                    + if (settings.getBoolean(ENABLE_DOUBLE_TAP_SEEK, true)) TOUCH_FLAG_SEEK else 0)
        } else 0
        currentScreenOrientation = resources.configuration.orientation
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        val yRange = Math.min(dm.widthPixels, dm.heightPixels)
        val xRange = Math.max(dm.widthPixels, dm.heightPixels)
        val sc = ScreenConfig(dm, xRange, yRange, currentScreenOrientation)
        touchDelegate = VideoTouchDelegate(this, touch, sc, isTv)
        UiTools.setRotationAnimation(this)
        if (savedInstanceState != null) {
            savedTime = savedInstanceState.getLong(KEY_TIME)
            videoUri = savedInstanceState.getParcelable<Parcelable>(KEY_URI) as Uri?
        }

        playToPause = AnimatedVectorDrawableCompat.create(this, R.drawable.anim_play_pause)!!
        pauseToPlay = AnimatedVectorDrawableCompat.create(this, R.drawable.anim_pause_play)!!

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

        if (isLocked && screenOrientation == 99) requestedOrientation = screenOrientationLock
    }

    private fun setListeners(enabled: Boolean) {
        if (navMenu != null) navMenu!!.setOnClickListener(if (enabled) this else null)
        if (::hudBinding.isInitialized) {
            hudBinding.playerOverlaySeekbar.setOnSeekBarChangeListener(if (enabled) seekListener else null)
            hudBinding.orientationToggle.setOnClickListener(if (enabled) this else null)
            hudBinding.orientationToggle.setOnLongClickListener(if (enabled) this else null)
        }
        UiTools.setViewOnClickListener(rendererBtn, if (enabled) this else null)
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        if (playbackStarted) service?.run {
            if (::hudBinding.isInitialized) {
                hudBinding.playerOverlayTitle.text = currentMediaWrapper?.title ?: return@run
            }
            var uri: Uri? = if (intent.hasExtra(PLAY_EXTRA_ITEM_LOCATION))
                intent.extras!!.getParcelable<Parcelable>(PLAY_EXTRA_ITEM_LOCATION) as Uri?
            else
                intent.data
            if (uri == null || uri == videoUri)
                return
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

        if (!isInPictureInPictureMode) {
            if (finishing || (AndroidUtil.isNougatOrLater && !AndroidUtil.isOOrLater //Video on background on Nougat Android TVs

                            && AndroidDevices.isAndroidTv && !requestVisibleBehind(true)))
                stopPlayback()
            else if (displayManager.isPrimary && !isShowingDialog && "2" == settings.getString(KEY_VIDEO_APP_SWITCH, "0")
                    && isInteractive && service?.hasRenderer() == false) {
                switchToPopup()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (videoUri != null && "content" != videoUri!!.scheme) {
            outState.putLong(KEY_TIME, savedTime)
            if (playlistModel == null) outState.putParcelable(KEY_URI, videoUri)
        }
        videoUri = null
    }

    @TargetApi(Build.VERSION_CODES.O)
    fun switchToPopup() {
        val mw = service?.currentMediaWrapper
        if (mw == null || !AndroidDevices.pipAllowed
                || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            return

        val forceLegacy = Settings.getInstance(this).getBoolean(POPUP_FORCE_LEGACY, false)
        if (AndroidDevices.hasPiP && !forceLegacy) {
            if (AndroidUtil.isOOrLater)
                try {
                    videoLayout?.findViewById<View>(R.id.surface_video)?.run {
                        val height = if (height != 0) height else mw.height
                        val width = Math.min(if (width != 0) width else mw.width, (height * 2.39f).toInt())
                        enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(width, height)).build())
                    }
                } catch (e: IllegalArgumentException) { // Fallback with default parameters

                    enterPictureInPictureMode()
                }
            else {

                enterPictureInPictureMode()
            }
        } else {
            if (Permissions.canDrawOverlays(this)) {
                switchingView = true
                switchToPopup = true
                if (service?.isPlaying != true) mw.addFlags(AbstractMediaWrapper.MEDIA_PAUSED)
                cleanUI()
                exitOK()
            } else
                Permissions.checkDrawOverlaysPermission(this)
        }
    }

    override fun onVisibleBehindCanceled() {
        super.onVisibleBehindCanceled()
        stopPlayback()
        exitOK()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        currentScreenOrientation = newConfig.orientation

        if (touchDelegate != null) {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(dm)
            val sc = ScreenConfig(dm,
                    Math.max(dm.widthPixels, dm.heightPixels),
                    Math.min(dm.widthPixels, dm.heightPixels),
                    currentScreenOrientation)
            touchDelegate!!.screenConfig = sc
        }
        resetHudLayout()
        showControls(isShowing)
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun resetHudLayout() {
        if (!::hudBinding.isInitialized) return
        val orientation = getScreenOrientation(100)
        val portrait = orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        if (portrait) controlsConstraintSetPortrait.applyTo(hudBinding.progressOverlay) else controlsConstraintSetLandscape.applyTo(hudBinding.progressOverlay)

        if (!isTv && !AndroidDevices.isChromeBook)
            hudBinding.orientationToggle.setVisible()
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    override fun onStart() {
        medialibrary.pauseBackgroundOperations()
        super.onStart()
        PlaybackService.start(this)
        PlaybackService.service.observe(this, this)
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
        PlaybackService.service.removeObservers(this)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver)

        unregisterReceiver(btReceiver)
        alertDialog?.dismiss()
        if (displayManager.isPrimary && !isFinishing && service?.isPlaying == true
                && "1" == settings.getString(KEY_VIDEO_APP_SWITCH, "0")) {
            switchToAudioMode(false)
        }

        cleanUI()
        stopPlayback()

        val editor = settings.edit()
        if (savedTime != -1L) editor.putLong(VIDEO_RESUME_TIME, savedTime)

        editor.apply()

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
    }

    private fun saveBrightness() {
        // Save brightness if user wants to
        if (settings.getBoolean(SAVE_BRIGHTNESS, false)) {
            val brightness = window.attributes.screenBrightness
            if (brightness != -1f) {
                val editor = settings.edit()
                editor.putFloat(BRIGHTNESS_VALUE, brightness)
                editor.apply()
            }
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
                    stop()
                } else
                    vlcVout.detachViews()
            }
            val mediaPlayer = mediaplayer
            if (!displayManager.isOnRenderer && videoLayout != null) {
                mediaPlayer.attachViews(videoLayout!!, displayManager, true, false)
                val size = if (isBenchmark) MediaPlayer.ScaleType.SURFACE_FILL else MediaPlayer.ScaleType.values()[settings.getInt(VIDEO_RATIO, MediaPlayer.ScaleType.SURFACE_BEST_FIT.ordinal)]
                mediaPlayer.videoScale = size
            }

            initUI()

            loadMedia()
        }
    }

    private fun initPlaylistUi() {
        if (service?.hasPlaylist() == true) {
            hasPlaylist = true
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

            val callback = SwipeDragItemTouchHelperCallback(playlistAdapter)
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

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
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
            if (wasPaused) settings.edit().putBoolean(VIDEO_PAUSED, true).apply()
            if (!isFinishing) {
                currentAudioTrack = audioTrack
                currentSpuTrack = spuTrack
                if (tv && !isInteractive) finish() // Leave player on TV, restauration can be difficult
            }

            if (isMute) mute(false)

            playbackStarted = false

            handler.removeCallbacksAndMessages(null)
            mediaplayer.detachViews()
            if (hasMedia() && switchingView) {
                if (BuildConfig.DEBUG) Log.d(TAG, "mLocation = \"$videoUri\"")
                if (switchToPopup)
                    switchToPopup(currentMediaPosition)
                else {
                    currentMediaWrapper!!.addFlags(AbstractMediaWrapper.MEDIA_FORCE_AUDIO)
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
            stop()
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private fun cleanUI() {

        rootView?.run { keepScreenOn = false }

        /* Stop listening for changes to media routes. */
        if (!isBenchmark) displayManager.removeMediaRouterCallback()

        if (!displayManager.isSecondary) service?.mediaplayer?.detachViews()

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data == null) return

        if (data.hasExtra(EXTRA_MRL)) {
            service?.addSubtitleTrack(Uri.parse(data.getStringExtra(EXTRA_MRL)), false)
            service?.currentMediaWrapper?.let {
                SlaveRepository.getInstance(this).saveSlave(it.location, Media.Slave.Type.Subtitle, 2, data.getStringExtra(EXTRA_MRL))
            }
            addNextTrack = true
        } else if (BuildConfig.DEBUG) Log.d(TAG, "Subtitle selection dialog was cancelled")
    }

    open fun exit(resultCode: Int) {
        if (isFinishing) return
        val resultIntent = Intent(ACTION_RESULT)
        if (videoUri != null) service?.run {
            if (AndroidUtil.isNougatOrLater)
                resultIntent.putExtra(EXTRA_URI, videoUri!!.toString())
            else
                resultIntent.data = videoUri
            resultIntent.putExtra(EXTRA_POSITION, time)
            resultIntent.putExtra(EXTRA_DURATION, length)
        }
        setResult(resultCode, resultIntent)
        finish()
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
        return !isLoading && touchDelegate != null && touchDelegate!!.dispatchGenericMotionEvent(event)
    }

    override fun onBackPressed() {
        if (optionsDelegate != null && optionsDelegate!!.isShowing()) {
            optionsDelegate!!.hide()
        } else if (lockBackButton) {
            lockBackButton = false
            handler.sendEmptyMessageDelayed(RESET_BACK_LOCK, 2000)
            Toast.makeText(applicationContext, getString(R.string.back_quit_lock), Toast.LENGTH_SHORT).show()
        } else if (isPlaylistVisible) {
            togglePlaylist()
        } else if (isPlaybackSettingActive) {
            endPlaybackSetting()
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
        if (isPlaybackSettingActive || isOptionsListShowing) return false
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
            KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekDelta(10000)
                return true
            }
            KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekDelta(-10000)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                seekDelta(60000)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                seekDelta(-60000)
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
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isNavMenu)
                    return navigateDvdMenu(keyCode)
                else if (!isShowing && !playlistContainer.isVisible()) {
                    if (event.isAltPressed && event.isCtrlPressed) {
                        seekDelta(-300000)
                    } else if (event.isCtrlPressed) {
                        seekDelta(-60000)
                    } else if (fov == 0f)
                        seekDelta(-10000)
                    else
                        service?.updateViewpoint(-5f, 0f, 0f, 0f, false)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isNavMenu)
                    return navigateDvdMenu(keyCode)
                else if (!isShowing && !playlistContainer.isVisible()) {
                    if (event.isAltPressed && event.isCtrlPressed) {
                        seekDelta(300000)
                    } else if (event.isCtrlPressed) {
                        seekDelta(60000)
                    } else if (fov == 0f)
                        seekDelta(10000)
                    else
                        service?.updateViewpoint(5f, 0f, 0f, 0f, false)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isNavMenu)
                    return navigateDvdMenu(keyCode)
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
                delayAudio(-50000L)
                return true
            }
            KeyEvent.KEYCODE_K -> {
                delayAudio(50000L)
                return true
            }
            KeyEvent.KEYCODE_G -> {
                delaySubs(-50000L)
                return true
            }
            KeyEvent.KEYCODE_H -> {
                delaySubs(50000L)
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
                }
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

    override fun showAudioDelaySetting() {
        playbackSetting = IPlaybackSettingsController.DelayState.AUDIO
        showDelayControls()
    }

    override fun showSubsDelaySetting() {
        playbackSetting = IPlaybackSettingsController.DelayState.SUBS
        showDelayControls()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showDelayControls() {
        if (touchDelegate != null) touchDelegate!!.clearTouchAction()
        if (!displayManager.isPrimary) showOverlayTimeout(OVERLAY_INFINITE)
        val vsc = findViewById<ViewStubCompat>(R.id.player_overlay_settings_stub)
        if (vsc != null) {
            vsc.inflate()
            playbackSettingPlus = findViewById(R.id.player_delay_plus)
            playbackSettingMinus = findViewById(R.id.player_delay_minus)

        }
        playbackSettingMinus!!.setOnClickListener(this)
        playbackSettingPlus!!.setOnClickListener(this)
        playbackSettingMinus!!.setOnTouchListener(OnRepeatListener(this))
        playbackSettingPlus!!.setOnTouchListener(OnRepeatListener(this))
        playbackSettingMinus.setVisible()
        playbackSettingPlus.setVisible()
        playbackSettingPlus!!.requestFocus()
        initPlaybackSettingInfo()
    }


    private fun initPlaybackSettingInfo() {
        initInfoOverlay()
        verticalBar.setGone()
        overlayInfo.setVisible()
        var text = ""
        when (playbackSetting) {
            IPlaybackSettingsController.DelayState.AUDIO -> {
                text += getString(R.string.audio_delay) + "\n"
                text += service!!.audioDelay / 1000L
                text += " ms"
            }
            IPlaybackSettingsController.DelayState.SUBS -> {
                text += getString(R.string.spu_delay) + "\n"
                text += service!!.spuDelay / 1000L
                text += " ms"
            }
            else -> text += "0"
        }
        info?.text = text
    }

    override fun endPlaybackSetting() {
        service?.let { service ->
            service.saveMediaMeta()
            if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO && (audiomanager.isBluetoothA2dpOn || audiomanager.isBluetoothScoOn)) {
                val msg = (getString(R.string.audio_delay) + "\n"
                        + service.audioDelay / 1000L
                        + " ms")
                val sb = Snackbar.make(info!!, msg, Snackbar.LENGTH_LONG)
                sb.setAction(R.string.save_bluetooth_delay, btSaveListener)
                sb.show()
            }
            playbackSetting = IPlaybackSettingsController.DelayState.OFF
            if (playbackSettingMinus != null) {
                playbackSettingMinus!!.setOnClickListener(null)
                playbackSettingMinus.setInvisible()
            }
            if (playbackSettingPlus != null) {
                playbackSettingPlus!!.setOnClickListener(null)
                playbackSettingPlus.setInvisible()
            }
            overlayInfo.setInvisible()
            info!!.text = ""
            if (::hudBinding.isInitialized) hudBinding.playerOverlayPlay.requestFocus()
        }
    }

    private fun delayAudio(delta: Long) {
        service?.let { service ->
            initInfoOverlay()
            val delay = service.audioDelay + delta
            service.setAudioDelay(delay)
            info?.text = getString(R.string.audio_delay) + "\n" + delay / 1000L + " ms"
            audioDelay = delay
            if (!isPlaybackSettingActive) {
                playbackSetting = IPlaybackSettingsController.DelayState.AUDIO
                initPlaybackSettingInfo()
            }
        }
    }

    private fun delaySubs(delta: Long) {
        service?.let { service ->
            initInfoOverlay()
            val delay = service.spuDelay + delta
            service.setSpuDelay(delay)
            info?.text = getString(R.string.spu_delay) + "\n" + delay / 1000L + " ms"
            spuDelay = delay
            if (!isPlaybackSettingActive) {
                playbackSetting = IPlaybackSettingsController.DelayState.SUBS
                initPlaybackSettingInfo()
            }
        }
    }

    /**
     * Lock screen rotation
     */
    private fun lockScreen() {
        if (screenOrientation != 100) {
            screenOrientationLock = requestedOrientation
            requestedOrientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                ActivityInfo.SCREEN_ORIENTATION_LOCKED
            else
                getScreenOrientation(100)
        }
        showInfo(R.string.locked, 1000)
        if (::hudBinding.isInitialized) {
            hudBinding.lockOverlayButton.setImageResource(R.drawable.ic_locked_player)
            hudBinding.playerOverlayTime.isEnabled = false
            hudBinding.playerOverlaySeekbar.isEnabled = false
            hudBinding.playerOverlayLength.isEnabled = false
            hudBinding.playlistNext.isEnabled = false
            hudBinding.playlistPrevious.isEnabled = false
        }
        hideOverlay(true)
        lockBackButton = true
        isLocked = true
    }

    /**
     * Remove screen lock
     */
    private fun unlockScreen() {
        if (screenOrientation != 100)
            requestedOrientation = screenOrientationLock
        showInfo(R.string.unlocked, 1000)
        if (::hudBinding.isInitialized) {
            hudBinding.lockOverlayButton.setImageResource(R.drawable.ic_lock_player)
            hudBinding.playerOverlayTime.isEnabled = true
            hudBinding.playerOverlaySeekbar.isEnabled = service?.isSeekable != false
            hudBinding.playerOverlayLength.isEnabled = true
            hudBinding.playlistNext.isEnabled = true
            hudBinding.playlistPrevious.isEnabled = true
        }
        isShowing = false
        isLocked = false
        showOverlay()
        lockBackButton = false
    }

    /**
     * Show text in the info view and vertical progress bar for "duration" milliseconds
     * @param text
     * @param duration
     * @param barNewValue new volume/brightness value (range: 0 - 15)
     */
    private fun showInfoWithVerticalBar(text: String, duration: Int, barNewValue: Int, max: Int) {
        showInfo(text, duration)
        if (!::verticalBarProgress.isInitialized) return
        var layoutParams: LinearLayout.LayoutParams
        if (barNewValue <= 100) {
            layoutParams = verticalBarProgress.layoutParams as LinearLayout.LayoutParams
            layoutParams.weight = barNewValue * 100 / max.toFloat()
            verticalBarProgress.layoutParams = layoutParams
            layoutParams = verticalBarBoostProgress.layoutParams as LinearLayout.LayoutParams
            layoutParams.weight = 0f
            verticalBarBoostProgress.layoutParams = layoutParams
        } else {
            layoutParams = verticalBarProgress.layoutParams as LinearLayout.LayoutParams
            layoutParams.weight = 100 * 100 / max.toFloat()
            verticalBarProgress.layoutParams = layoutParams
            layoutParams = verticalBarBoostProgress.layoutParams as LinearLayout.LayoutParams
            layoutParams.weight = (barNewValue - 100) * 100 / max.toFloat()
            verticalBarBoostProgress.layoutParams = layoutParams
        }
        verticalBar.setVisible()
    }

    /**
     * Show text in the info view for "duration" milliseconds
     * @param text
     * @param duration
     */
    internal fun showInfo(text: String, duration: Int) {
        if (isInPictureInPictureMode) return
        initInfoOverlay()
        verticalBar.setGone()
        overlayInfo.setVisible()
        info!!.text = text
        handler.removeMessages(FADE_OUT_INFO)
        handler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration.toLong())
    }

    private fun initInfoOverlay() {
        val vsc = findViewById<ViewStubCompat>(R.id.player_info_stub)
        if (vsc != null) {
            vsc.setVisible()
            // the info textView is not on the overlay
            info = findViewById(R.id.player_overlay_textinfo)
            overlayInfo = findViewById(R.id.player_overlay_info)
            verticalBar = findViewById(R.id.verticalbar)
            verticalBarProgress = findViewById(R.id.verticalbar_progress)
            verticalBarBoostProgress = findViewById(R.id.verticalbar_boost_progress)
        }
    }

    private fun initSeekOverlay() = findViewById<ViewStubCompat>(R.id.player_seek_stub)?.setVisible()

    internal fun showInfo(textid: Int, duration: Int) {
        showInfo(getString(textid), duration)
    }

    /**
     * hide the info view with "delay" milliseconds delay
     * @param delay
     */
    private fun hideInfo(delay: Int = 0) {
        handler.sendEmptyMessageDelayed(FADE_OUT_INFO, delay.toLong())
    }

    private fun fadeOutInfo() {
        if (overlayInfo != null && overlayInfo!!.visibility == View.VISIBLE) {
            overlayInfo!!.startAnimation(AnimationUtils.loadAnimation(
                    this@VideoPlayerActivity, android.R.anim.fade_out))
            overlayInfo.setInvisible()
        }
    }

    /* PlaybackService.Callback */

    override fun update() {
        if (service == null || !::playlistAdapter.isInitialized) return
        playlistModel?.update()
    }

    override fun onMediaEvent(event: Media.Event) {
        when (event.type) {
            Media.Event.ParsedChanged -> updateNavStatus()
            Media.Event.MetaChanged -> {
            }
        }
    }

    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        service?.let { service ->
            when (event.type) {
                MediaPlayer.Event.Playing -> onPlaying()
                MediaPlayer.Event.Paused -> updateOverlayPausePlay()
                MediaPlayer.Event.Vout -> {
                    updateNavStatus()
                    if (event.voutCount > 0)
                        service.mediaplayer.updateVideoSurfaces()
                    if (menuIdx == -1)
                        handleVout(event.voutCount)
                }
                MediaPlayer.Event.ESAdded -> {
                    if (menuIdx == -1) {
                        val media = medialibrary.findMedia(service.currentMediaWrapper) ?: return
                        if (event.esChangedType == Media.Track.Type.Audio) {
                            setESTrackLists()
                            runIO(Runnable {
                                val audioTrack = media.getMetaLong(AbstractMediaWrapper.META_AUDIOTRACK).toInt()
                                if (audioTrack != 0 || currentAudioTrack != -2)
                                    service.setAudioTrack(if (media.id == 0L) currentAudioTrack else audioTrack)
                            })
                        } else if (event.esChangedType == Media.Track.Type.Text) {
                            setESTrackLists()
                            runIO(Runnable {
                                val spuTrack = media.getMetaLong(AbstractMediaWrapper.META_SUBTITLE_TRACK).toInt()
                                if (addNextTrack) {
                                    val tracks = service.spuTracks
                                    if (!Util.isArrayEmpty(tracks as Array<MediaPlayer.TrackDescription>)) service.setSpuTrack(tracks[tracks.size - 1].id)
                                    addNextTrack = false
                                } else if (spuTrack != 0 || currentSpuTrack != -2) {
                                    service.setSpuTrack(if (media.id == 0L) currentSpuTrack else spuTrack)
                                }
                            })
                        }
                    }
                    if (menuIdx == -1 && event.esChangedType == Media.Track.Type.Video) {
                        handler.removeMessages(CHECK_VIDEO_TRACKS)
                        handler.sendEmptyMessageDelayed(CHECK_VIDEO_TRACKS, 1000)
                    }
                    invalidateESTracks(event.esChangedType)
                }
                MediaPlayer.Event.ESDeleted -> {
                    if (menuIdx == -1 && event.esChangedType == Media.Track.Type.Video) {
                        handler.removeMessages(CHECK_VIDEO_TRACKS)
                        handler.sendEmptyMessageDelayed(CHECK_VIDEO_TRACKS, 1000)
                    }
                    invalidateESTracks(event.esChangedType)
                }
                MediaPlayer.Event.ESSelected -> if (event.esChangedType == Media.Track.Type.Video) {
                    val vt = service.currentVideoTrack
                    if (vt != null)
                        fov = if (vt.projection == Media.VideoTrack.Projection.Rectangular) 0f else DEFAULT_FOV
                }
                MediaPlayer.Event.SeekableChanged -> updateSeekable(event.seekable)
                MediaPlayer.Event.PausableChanged -> updatePausable(event.pausable)
                MediaPlayer.Event.Buffering -> {
                    if (isPlaying) {
                        if (event.buffering == 100f)
                            stopLoading()
                        else if (!handler.hasMessages(LOADING_ANIMATION) && !isLoading
                                && (touchDelegate == null || !touchDelegate!!.isSeeking()) && !isDragging)
                            handler.sendEmptyMessageDelayed(LOADING_ANIMATION, LOADING_ANIMATION_DELAY.toLong())
                    }
                }
            }
        }
    }

    private fun onPlaying() {
        val mw = service?.currentMediaWrapper ?: return
        isPlaying = true
        setPlaybackParameters()
        stopLoading()
        updateOverlayPausePlay()
        updateNavStatus()
        if (!mw.hasFlag(AbstractMediaWrapper.MEDIA_PAUSED))
            handler.sendEmptyMessageDelayed(FADE_OUT, OVERLAY_TIMEOUT.toLong())
        else {
            mw.removeFlags(AbstractMediaWrapper.MEDIA_PAUSED)
            wasPaused = false
        }
        setESTracks()
        if (::hudBinding.isInitialized && hudBinding.playerOverlayTitle.length() == 0)
            hudBinding.playerOverlayTitle.text = mw.title
        // Get possible subtitles
        observeDownloadedSubtitles()
        optionsDelegate?.setup()
        settings.edit().remove(VIDEO_PAUSED).apply()
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
            val i = Intent(this, if (isTv) AudioPlayerActivity::class.java else MainActivity::class.java)
            startActivity(i)
        }
        exitOK()
    }

    override fun isInPictureInPictureMode(): Boolean {
        return AndroidUtil.isNougatOrLater && super.isInPictureInPictureMode()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        service?.mediaplayer?.updateVideoSurfaces()
    }

    internal fun sendMouseEvent(action: Int, x: Int, y: Int) {
        if (service == null) return
        val vlcVout = service!!.vout
        vlcVout!!.sendMouseEvent(action, 0, x, y)
    }

    /**
     * show/hide the overlay
     */

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return service != null && touchDelegate?.onTouchEvent(event) == true
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
        if (warningToast != null) warningToast!!.cancel()
        warningToast = Toast.makeText(application, R.string.audio_boost_warning, Toast.LENGTH_SHORT)
        warningToast!!.show()
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
            showInfoWithVerticalBar(getString(R.string.volume) + "\n" + Integer.toString(vol) + '%'.toString(), 1000, vol, if (isAudioBoostEnabled) 200 else 100)
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
        var brightness = Math.min(Math.max(lp.screenBrightness + delta, 0.01f), 1f)
        setWindowBrightness(brightness)
        brightness = Math.round(brightness * 100).toFloat()
        showInfoWithVerticalBar(getString(R.string.brightness) + "\n" + brightness.toInt() + '%'.toString(), 1000, brightness.toInt(), 100)
    }

    private fun setWindowBrightness(brightness: Float) {
        val lp = window.attributes
        lp.screenBrightness = brightness
        // Set Brightness
        window.attributes = lp
    }

    open fun onAudioSubClick(anchor: View?) {
        service?.let { service ->
            var flags = 0
            if (enableSubs) {
                flags = flags or CTX_DOWNLOAD_SUBTITLES_PLAYER
                if (displayManager.isPrimary) flags = flags or CTX_PICK_SUBS
            }
            if (service.videoTracksCount > 2) flags = flags or CTX_VIDEO_TRACK
            if (service.audioTracksCount > 0) flags = flags or CTX_AUDIO_TRACK
            if (service.spuTracksCount > 0) flags = flags or CTX_SUBS_TRACK

            if (optionsDelegate == null) optionsDelegate = PlayerOptionsDelegate(this, service)
            optionsDelegate!!.flags = flags
            optionsDelegate!!.show(PlayerOptionType.MEDIA_TRACKS)
            hideOverlay(false)
        }
    }


    override fun onPopupMenu(view: View, position: Int, item: AbstractMediaWrapper?) {
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

    override fun playItem(position: Int, item: AbstractMediaWrapper) {
        service?.playIndex(position)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.orientation_toggle -> toggleOrientation()
            R.id.playlist_toggle -> togglePlaylist()
            R.id.player_overlay_forward -> seekDelta(10000)
            R.id.player_overlay_rewind -> seekDelta(-10000)
            R.id.player_overlay_navmenu -> showNavMenu()
            R.id.player_overlay_length, R.id.player_overlay_time -> toggleTimeDisplay()
            R.id.player_delay_minus -> if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO)
                delayAudio(-50000)
            else if (playbackSetting == IPlaybackSettingsController.DelayState.SUBS)
                delaySubs(-50000)
            R.id.player_delay_plus -> if (playbackSetting == IPlaybackSettingsController.DelayState.AUDIO)
                delayAudio(50000)
            else if (playbackSetting == IPlaybackSettingsController.DelayState.SUBS)
                delaySubs(50000)
            R.id.video_renderer -> if (supportFragmentManager.findFragmentByTag("renderers") == null)
                RenderersDialog().show(supportFragmentManager, "renderers")
            R.id.video_secondary_display -> {
                clone = displayManager.isSecondary
                recreate()
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        when (v.id) {
            R.id.orientation_toggle -> return resetOrientation()
        }

        return false
    }

    fun toggleTimeDisplay() {
        sDisplayRemainingTime = !sDisplayRemainingTime
        showOverlay()
        settings.edit().putBoolean(KEY_REMAINING_TIME_DISPLAY, sDisplayRemainingTime).apply()
    }

    fun toggleLock() {
        if (isLocked)
            unlockScreen()
        else
            lockScreen()
    }

    fun toggleLoop(v: View) = service?.run {
        if (repeatType == REPEAT_ONE) {
            showInfo(getString(R.string.repeat), 1000)
            repeatType = REPEAT_NONE
        } else {
            repeatType = REPEAT_ONE
            showInfo(getString(R.string.repeat_single), 1000)
        }
        true
    } ?: false

    override fun onStorageAccessGranted() {
        handler.sendEmptyMessage(START_PLAYBACK)
    }

    fun hideOptions() {
        if (optionsDelegate != null) optionsDelegate!!.hide()
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
                    ownerActivity = this@VideoPlayerActivity
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
                                    if (mw != null && mw.id != 0L) mw.setLongMeta(AbstractMediaWrapper.META_AUDIOTRACK, trackID.toLong())
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
        if (videoUri == null) return
        isShowingDialog = true
        val filePickerIntent = Intent(this, FilePickerActivity::class.java)
        filePickerIntent.data = Uri.parse(FileUtils.getParent(videoUri!!.toString()))
        startActivityForResult(filePickerIntent, 0)
    }


    fun downloadSubtitles() = service?.currentMediaWrapper?.let {
        MediaUtils.getSubs(this@VideoPlayerActivity, it)
    }


    @WorkerThread
    private fun setSpuTrack(trackID: Int) {
        runOnMainThread(Runnable { service?.setSpuTrack(trackID) })
        val mw = medialibrary.findMedia(service?.currentMediaWrapper) ?: return
        if (mw.id != 0L) mw.setLongMeta(AbstractMediaWrapper.META_SUBTITLE_TRACK, trackID.toLong())
    }

    private fun showNavMenu() {
        if (menuIdx >= 0) service?.titleIdx = menuIdx
    }

    private fun updateSeekable(seekable: Boolean) {
        if (!::hudBinding.isInitialized) return
        hudBinding.playerOverlayRewind.isEnabled = seekable
        hudBinding.playerOverlayRewind.setImageResource(if (seekable)
            R.drawable.ic_rewind_player
        else
            R.drawable.ic_rewind_player_disabled)
        hudBinding.playerOverlayForward.isEnabled = seekable
        hudBinding.playerOverlayForward.setImageResource(if (seekable)
            R.drawable.ic_forward_player
        else
            R.drawable.ic_forward_player_disabled)
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

    protected fun seek(position: Long) {
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

    private var nbTimesTaped = 0
    private var lastSeekWasForward = true
    internal fun seekDelta(delta: Int) {
        service?.let { service ->
            // unseekable stream
            if (service.length <= 0 || !service.isSeekable) return


            var position = time + delta
            if (position < 0) position = 0
            seek(position)
            val sb = StringBuilder()
            val seekForward = delta >= 0

            if (nbTimesTaped != 0 && lastSeekWasForward != seekForward) {
                hideSeekOverlay()
                nbTimesTaped = 0
            }

            nbTimesTaped++

            lastSeekWasForward = seekForward
            sb.append(if (nbTimesTaped == -1) (delta / 1000f).toInt() else (nbTimesTaped * (delta / 1000f).toInt()))
                    .append("s (")
                    .append(Tools.millisToString(service.time))
                    .append(')')

            initSeekOverlay()

            val container = if (seekForward) rightContainer else leftContainer
            val textView = if (seekForward) seekRightText else seekLeftText
            val imageFirst = if (seekForward) seekForwardFirst else seekRewindFirst
            val imageSecond = if (seekForward) seekForwardSecond else seekRewindSecond

            container.post {
                val backgroundAnim = ObjectAnimator.ofFloat(seek_background, "alpha", 1f)
                backgroundAnim.duration = 200

                val firstImageAnim = ObjectAnimator.ofFloat(imageFirst, "alpha", 1f, 0f)
                firstImageAnim.duration = 500

                val secondImageAnim = ObjectAnimator.ofFloat(imageSecond, "alpha", 0F, 1f, 0f)
                secondImageAnim.duration = 750

                val cx = if (seekForward) container.width else 0
                val cy = container.height / 2
                val animatorSet = AnimatorSet()
                val circularReveal = CircularRevealCompat.createCircularReveal(container, cx.toFloat(), cy.toFloat(), 0F, container.width.toFloat())

                val backgroundColorAnimator = ObjectAnimator.ofObject(container,
                        CircularRevealWidget.CircularRevealScrimColorProperty.CIRCULAR_REVEAL_SCRIM_COLOR.name,
                        ArgbEvaluator(),
                        Color.TRANSPARENT, ContextCompat.getColor(this, R.color.ripple_white), Color.TRANSPARENT)

                animatorSet.playTogether(
                        circularReveal,
                        backgroundColorAnimator,
                        backgroundAnim,
                        firstImageAnim,
                        secondImageAnim
                )
                animatorSet.duration = 1000

                val mainAnimOut = ObjectAnimator.ofFloat(seek_background, "alpha", 0f)
                backgroundAnim.duration = 200

                val seekAnimatorSet = AnimatorSet()
                seekAnimatorSet.playSequentially(animatorSet, mainAnimOut)


                handler.removeMessages(HIDE_SEEK)
                handler.sendEmptyMessageDelayed(HIDE_SEEK, SEEK_TIMEOUT.toLong())

                container.visibility = View.VISIBLE
                seekAnimatorSet.start()
            }
            textView.text = sb.toString()
        }
    }

    private fun hideSeekOverlay() {
        rightContainer.visibility = View.INVISIBLE
        leftContainer.visibility = View.INVISIBLE
        seekRightText.text = ""
        seekLeftText.text = ""
        nbTimesTaped = 0
        seekForwardFirst.alpha = 0f
        seekForwardSecond.alpha = 0f
        seekRewindFirst.alpha = 0f
        seekRewindSecond.alpha = 0f
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
        settings.edit()
                .putInt(VIDEO_RATIO, scale.ordinal)
                .apply()
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
    private fun showOverlayTimeout(timeout: Int) {
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
                hudBinding.progressOverlay.setVisible()
                hudRightBinding.hudRightOverlay.setVisible()
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
            if (hasPlaylist) {
                hudBinding.playlistPrevious.visibility = if (show) View.VISIBLE else View.INVISIBLE
                hudBinding.playlistNext.visibility = if (show) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun initOverlay() {
        service?.let { service ->
            val vscRight = findViewById<ViewStubCompat>(R.id.player_hud_right_stub)
            vscRight?.let {
                it.inflate()
                hudRightBinding = DataBindingUtil.bind(findViewById(R.id.hud_right_overlay))
                        ?: return
                val secondary = displayManager.isSecondary
                if (secondary) hudRightBinding.videoSecondaryDisplay.setImageResource(R.drawable.ic_screenshare_stop_circle_player)
                hudRightBinding.videoSecondaryDisplay.visibility = if (UiTools.hasSecondaryDisplay(applicationContext)) View.VISIBLE else View.GONE
                hudRightBinding.videoSecondaryDisplay.contentDescription = resources.getString(if (secondary) R.string.video_remote_disable else R.string.video_remote_enable)
                if (!isBenchmark && enableCloneMode && !settings.contains("enable_clone_mode")) {
                    UiTools.snackerConfirm(hudRightBinding.videoSecondaryDisplay, getString(R.string.video_save_clone_mode), Runnable { settings.edit().putBoolean("enable_clone_mode", true).apply() })
                }
            }

            val vsc = findViewById<ViewStubCompat>(R.id.player_hud_stub)
            if (vsc != null) {
                seekButtons = settings.getBoolean(ENABLE_SEEK_BUTTONS, false)
                vsc.inflate()
                hudBinding = DataBindingUtil.bind(findViewById(R.id.progress_overlay)) ?: return
                hudBinding.player = this
                hudBinding.progress = service.playlistManager.player.progress
                hudBinding.lifecycleOwner = this
                val layoutParams = hudBinding.progressOverlay.layoutParams as RelativeLayout.LayoutParams
                if (AndroidDevices.isPhone || !AndroidDevices.hasNavBar)
                    layoutParams.width = LayoutParams.MATCH_PARENT
                else
                    layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE)
                hudBinding.progressOverlay.layoutParams = layoutParams
                overlayBackground = findViewById(R.id.player_overlay_background)
                navMenu = findViewById(R.id.player_overlay_navmenu)
                if (!AndroidDevices.isChromeBook && !isTv
                        && Settings.getInstance(this).getBoolean("enable_casting", true)) {
                    rendererBtn = findViewById(R.id.video_renderer)
                    PlaybackService.renderer.observe(this, Observer { rendererItem -> rendererBtn?.setImageDrawable(AppCompatResources.getDrawable(this, if (rendererItem == null) R.drawable.ic_renderer_circle_player else R.drawable.ic_renderer_on_circle_player)) })
                    RendererDelegate.renderers.observe(this, Observer<List<RendererItem>> { rendererItems -> rendererBtn.setVisibility(if (Util.isListEmpty(rendererItems)) View.GONE else View.VISIBLE) })
                }

                hudBinding.playerOverlayTitle.text = service.currentMediaWrapper?.title

                if (seekButtons) initSeekButton()
                controlsConstraintSetPortrait.clone(hudBinding.progressOverlay)
                controlsConstraintSetLandscape.clone(hudBinding.progressOverlay)
                controlsConstraintSetPortrait.clear(R.id.player_overlay_time_container, ConstraintSet.BOTTOM)
                controlsConstraintSetPortrait.clear(R.id.player_overlay_length_container, ConstraintSet.BOTTOM)

                controlsConstraintSetPortrait.removeFromHorizontalChain(R.id.player_overlay_time_container)
                controlsConstraintSetPortrait.removeFromHorizontalChain(R.id.player_overlay_length_container)
                controlsConstraintSetPortrait.connect(R.id.player_overlay_time_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                controlsConstraintSetPortrait.connect(R.id.player_overlay_length_container, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                controlsConstraintSetPortrait.setMargin(R.id.player_overlay_time_container, ConstraintSet.START, resources.getDimensionPixelSize(R.dimen.time_margin_sides))
                controlsConstraintSetPortrait.setMargin(R.id.player_overlay_length_container, ConstraintSet.END, resources.getDimensionPixelSize(R.dimen.time_margin_sides))

                val chainIds = intArrayOf(R.id.lock_overlay_button, R.id.playlist_previous, R.id.player_overlay_rewind, R.id.player_overlay_play, R.id.player_overlay_forward, R.id.playlist_next, R.id.player_overlay_tracks)

                chainIds.forEach {
                    controlsConstraintSetPortrait.clear(it, ConstraintSet.START)
                    controlsConstraintSetPortrait.clear(it, ConstraintSet.END)
                    controlsConstraintSetPortrait.removeFromHorizontalChain(it)
                    controlsConstraintSetPortrait.connect(it, ConstraintSet.TOP, R.id.player_overlay_time_container, ConstraintSet.BOTTOM)
                    controlsConstraintSetPortrait.connect(it, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                }

                controlsConstraintSetPortrait.createHorizontalChain(ConstraintSet.PARENT_ID, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, chainIds, null, ConstraintSet.CHAIN_SPREAD)

                resetHudLayout()
                updateOverlayPausePlay(true)
                updateSeekable(service.isSeekable)
                updatePausable(service.isPausable)
                updateNavStatus()
                setListeners(true)
                initPlaylistUi()
                if (!displayManager.isPrimary) hudBinding.lockOverlayButton.setGone()
            } else if (::hudBinding.isInitialized) {
                hudBinding.progress = service.playlistManager.player.progress
                hudBinding.lifecycleOwner = this
            }
        }
    }

    /**
     * hider overlay
     */
    internal fun hideOverlay(fromUser: Boolean) {
        if (isShowing) {
            handler.removeMessages(FADE_OUT)
            Log.i(TAG, "remove View!")
            overlayTips.setInvisible()
            if (!displayManager.isPrimary) {
                overlayBackground?.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out))
                overlayBackground.setInvisible()
            }
            if (::hudBinding.isInitialized) hudBinding.progressOverlay.setInvisible()
            if (::hudRightBinding.isInitialized) hudRightBinding.hudRightOverlay.setInvisible()
            showControls(false)
            isShowing = false
            dimStatusBar(true)
            playlistSearchText.editText!!.setText("")
        } else if (!fromUser) {
            /*
             * Try to hide the Nav Bar again.
             * It seems that you can't hide the Nav Bar if you previously
             * showed it in the last 1-2 seconds.
             */
            dimStatusBar(true)
        }
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
            visibility = visibility or View.SYSTEM_UI_FLAG_VISIBLE
        }

        if (AndroidDevices.hasNavBar)
            visibility = visibility or navbar
        window.decorView.systemUiVisibility = visibility
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
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
            Media.Track.Type.Audio -> audioTracksList = null
            Media.Track.Type.Text -> subtitleTracksList = null
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

    operator fun next() {
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
            var fromStart = false
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
                if (intent.hasExtra(PLAY_EXTRA_ITEM_LOCATION))
                    videoUri = extras.getParcelable(PLAY_EXTRA_ITEM_LOCATION)
                fromStart = extras.getBoolean(PLAY_EXTRA_FROM_START, false)
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
                if (!TextUtils.isEmpty(path)) service.addSubtitleTrack(path!!, true)
                if (intent.hasExtra(PLAY_EXTRA_ITEM_TITLE))
                    itemTitle = extras.getString(PLAY_EXTRA_ITEM_TITLE)
            }
            if (startTime == 0L && savedTime > 0L) startTime = savedTime
            val restorePlayback = hasMedia && currentMedia!!.uri == videoUri

            var openedMedia: AbstractMediaWrapper? = null
            val resumePlaylist = service.isValidIndex(positionInPlaylist)
            val continueplayback = isPlaying && (restorePlayback || positionInPlaylist == service.currentMediaPosition)
            if (resumePlaylist) {
                // Provided externally from AudioService
                if (BuildConfig.DEBUG) Log.d(TAG, "Continuing playback from PlaybackService at index $positionInPlaylist")
                openedMedia = service.media[positionInPlaylist]
//            if (openedMedia == null) {
//                encounteredError()
//                return
//            }
                itemTitle = openedMedia.title
                updateSeekable(service.isSeekable)
                updatePausable(service.isPausable)
            }
            if (videoUri != null) {
                var media: AbstractMediaWrapper? = null
                if (!continueplayback) {
                    if (!resumePlaylist) {
                        // restore last position
                        media = medialibrary.getMedia(videoUri!!)
                        if (media == null && TextUtils.equals(videoUri!!.scheme, "file") &&
                                videoUri!!.path != null && videoUri!!.path!!.startsWith("/sdcard")) {
                            videoUri = FileUtils.convertLocalUri(videoUri!!)
                            media = medialibrary.getMedia(videoUri!!)
                        }
                        if (media != null && media.id != 0L && media.time == 0L)
                            media.time = media.getMetaLong(AbstractMediaWrapper.META_PROGRESS)
                    } else
                        media = openedMedia
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
                                    settings.edit()
                                            .putLong(VIDEO_RESUME_TIME, -1)
                                            .apply()
                                    startTime = rTime
                                }
                            }
                        }
                    }
                }

                // Start playback & seek
                /* prepare playback */
                val medialoaded = media != null
                if (!medialoaded) media = if (hasMedia) currentMedia else MLServiceLocator.getAbstractMediaWrapper(videoUri!!)
                if (wasPaused)
                    media!!.addFlags(AbstractMediaWrapper.MEDIA_PAUSED)
                if (intent.hasExtra(PLAY_DISABLE_HARDWARE))
                    media!!.addFlags(AbstractMediaWrapper.MEDIA_NO_HWACCEL)
                media!!.removeFlags(AbstractMediaWrapper.MEDIA_FORCE_AUDIO)
                media.addFlags(AbstractMediaWrapper.MEDIA_VIDEO)
                if (fromStart) media.addFlags(AbstractMediaWrapper.MEDIA_FROM_START)

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
                    } else
                        service.playIndex(positionInPlaylist)
                } else if (medialoaded)
                    service.load(media)
                else
                    service.loadUri(videoUri)

                // Get the title
                if (itemTitle == null && !TextUtils.equals(videoUri!!.scheme, "content"))
                    title = videoUri!!.lastPathSegment
            } else if (service.hasMedia() && !displayManager.isPrimary) {
                onPlaying()
            } else {
                service.loadLastPlaylist(PLAYLIST_TYPE_VIDEO)
            }
            if (itemTitle != null) title = itemTitle
            if (::hudBinding.isInitialized) {
                hudBinding.playerOverlayTitle.text = title
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
        if (videoUri != null) {
            val lastPath = videoUri!!.lastPathSegment
            enableSubs = (!TextUtils.isEmpty(lastPath) && !lastPath!!.endsWith(".ts") && !lastPath.endsWith(".m2ts")
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
    private fun getScreenOrientation(mode: Int): Int {
        when (mode) {
            98 //toggle button
            -> return if (currentScreenOrientation == Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            99 //screen orientation user
            -> return if (AndroidUtil.isJellyBeanMR2OrLater)
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            101 //screen orientation landscape
            -> return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            102 //screen orientation portrait
            -> return ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        /*
         screenOrientation = 100, we lock screen at its current orientation
         */
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
        optionsDelegate?.show(PlayerOptionType.ADVANCED)
        hideOverlay(false)
    }

    private fun toggleOrientation() {
        //screen is not yet locked. We invert the rotation to force locking in the current orientation
        if (screenOrientation != 98) {
            currentScreenOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        screenOrientation = 98//Rotate button
        requestedOrientation = getScreenOrientation(screenOrientation)

        //As the current orientation may have been artificially changed above, we reset it to the real current orientation
        currentScreenOrientation = resources.configuration.orientation
        @StringRes val message = if (currentScreenOrientation == Configuration.ORIENTATION_LANDSCAPE)
            R.string.locked_in_landscape_mode
        else
            R.string.locked_in_portrait_mode
        rootView?.let { UiTools.snacker(it, message) }
    }

    private fun resetOrientation(): Boolean {
        if (screenOrientation == 98) {
            screenOrientation = Integer.valueOf(
                    settings.getString(SCREEN_ORIENTATION, "99" /*SCREEN ORIENTATION SENSOR*/)!!)
            rootView?.let { UiTools.snacker(it, R.string.reset_orientation) }
            requestedOrientation = getScreenOrientation(screenOrientation)
            return true
        }
        return false
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
        loadingImageView!!.startAnimation(anim)
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

    fun onClickOverlayTips(v: View) {
        overlayTips.setGone()
    }

    fun onClickDismissTips(v: View) {
        overlayTips.setGone()
        settings.edit().putBoolean(PREF_TIPS_SHOWN, true).apply()
    }

    private fun updateNavStatus() {
        if (service == null) return
        isNavMenu = false
        menuIdx = -1

        runIO(Runnable {
            val titles = service?.titles
            runOnMainThread(Runnable {
                if (isFinishing || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@Runnable
                if (titles != null) {
                    val currentIdx = service?.titleIdx ?: return@Runnable
                    for (i in titles.indices) {
                        val title = titles[i]
                        if (title.isMenu) {
                            menuIdx = i
                            break
                        }
                    }
                    isNavMenu = menuIdx == currentIdx
                }

                if (isNavMenu) {
                    /*
                             * Keep the overlay hidden in order to have touch events directly
                             * transmitted to navigation handling.
                             */
                    hideOverlay(false)
                } else if (menuIdx != -1) setESTracks()

                navMenu.setVisibility(if (menuIdx >= 0 && navMenu != null) View.VISIBLE else View.GONE)
                supportInvalidateOptionsMenu()
            })
        })
    }

    override fun onChanged(service: PlaybackService?) {
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
        private const val KEY_URI = "saved_uri"
        private const val OVERLAY_TIMEOUT = 4000
        private const val SEEK_TIMEOUT = 1500
        private const val OVERLAY_INFINITE = -1
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
        private const val KEY_REMAINING_TIME_DISPLAY = "remaining_time_display"
        private const val KEY_BLUETOOTH_DELAY = "key_bluetooth_delay"

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

        fun getIntent(action: String, mw: AbstractMediaWrapper, fromStart: Boolean, openedPosition: Int): Intent {
            return getIntent(action, VLCApplication.appContext, mw.uri, mw.title, fromStart, openedPosition)
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

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@BindingAdapter("length", "time")
fun setPlaybackTime(view: TextView, length: Long, time: Long) {
    view.text = if (VideoPlayerActivity.sDisplayRemainingTime && length > 0)
        "-" + '\u00A0'.toString() + Tools.millisToString(length - time)
    else
        Tools.millisToString(length)
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
