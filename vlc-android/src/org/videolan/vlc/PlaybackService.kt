/*****************************************************************************
 * PlaybackService.kt
 * Copyright © 2011-2018 VLC authors and VideoLAN
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

package org.videolan.vlc

import android.annotation.TargetApi
import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.*
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.media.MediaBrowserServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.videolan.libvlc.IVLCVout
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.vlc.gui.video.PopupManager
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.BrowserProvider
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.*
import org.videolan.vlc.widget.VLCAppWidgetProvider
import org.videolan.vlc.widget.VLCAppWidgetProviderBlack
import org.videolan.vlc.widget.VLCAppWidgetProviderWhite
import java.util.*

private const val TAG = "VLC/PlaybackService"

class PlaybackService : MediaBrowserServiceCompat(), CoroutineScope, LifecycleOwner {
    override val coroutineContext = Dispatchers.Main.immediate
    private val dispatcher = ServiceLifecycleDispatcher(this)

    lateinit var playlistManager: PlaylistManager
        private set
    val mediaplayer: MediaPlayer
        get() = playlistManager.player.mediaplayer
    private lateinit var keyguardManager: KeyguardManager
    internal lateinit var settings: SharedPreferences
    private val mBinder = LocalBinder()
    internal lateinit var medialibrary: Medialibrary

    private val callbacks = mutableListOf<Callback>()
    private var detectHeadset = true
    private lateinit var wakeLock: PowerManager.WakeLock
    private val audioFocusHelper by lazy { VLCAudioFocusHelper(this) }

    // Playback management
    internal lateinit var mediaSession: MediaSessionCompat
    @Volatile private var notificationShowing = false

    private var widget = 0
    /**
     * Last widget position update timestamp
     */
    private var widgetPositionTimestamp = System.currentTimeMillis()
    private var popupManager: PopupManager? = null

    internal var libraryReceiver: PBSMedialibraryReceiver? = null

    private val receiver = object : BroadcastReceiver() {
        private var wasPlaying = false
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val state = intent.getIntExtra("state", 0)

            // skip all headsets events if there is a call
            val telManager = this@PlaybackService.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telManager?.callState != TelephonyManager.CALL_STATE_IDLE) return

            /*
             * Launch the activity if needed
             */
            if (action.startsWith(ACTION_REMOTE_GENERIC) && !isPlaying && !playlistManager.hasCurrentMedia()) {
                packageManager.getLaunchIntentForPackage(packageName)?.let { context.startActivity(it) }
            }

            /*
             * Remote / headset control events
             */
            when (action) {
                ACTION_REMOTE_PLAYPAUSE -> {
                    if (!playlistManager.hasCurrentMedia()) loadLastAudioPlaylist()
                    else if (isPlaying) pause()
                    else play()
                }
                ACTION_REMOTE_PLAY -> if (!isPlaying && playlistManager.hasCurrentMedia()) play()
                ACTION_REMOTE_PAUSE -> if (playlistManager.hasCurrentMedia()) pause()
                ACTION_REMOTE_BACKWARD -> previous(false)
                ACTION_REMOTE_STOP,
                VLCApplication.SLEEP_INTENT -> stop()
                ACTION_REMOTE_FORWARD -> next()
                ACTION_REMOTE_LAST_PLAYLIST -> loadLastAudioPlaylist()
                ACTION_REMOTE_LAST_VIDEO_PLAYLIST -> playlistManager.loadLastPlaylist(PLAYLIST_TYPE_VIDEO)
                ACTION_REMOTE_SWITCH_VIDEO -> {
                    removePopup()
                    if (hasMedia()) {
                        currentMediaWrapper!!.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                        playlistManager.switchToVideo()
                    }
                }
                VLCAppWidgetProvider.ACTION_WIDGET_INIT -> updateWidget()
                VLCAppWidgetProvider.ACTION_WIDGET_ENABLED , VLCAppWidgetProvider.ACTION_WIDGET_DISABLED -> updateHasWidget()
                ACTION_CAR_MODE_EXIT -> BrowserProvider.unbindExtensionConnection()
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> if (detectHeadset) {
                    if (BuildConfig.DEBUG) Log.i(TAG, "Becoming noisy")
                    wasPlaying = isPlaying
                    if (wasPlaying && playlistManager.hasCurrentMedia())
                        pause()
                }
                Intent.ACTION_HEADSET_PLUG -> if (detectHeadset && state != 0) {
                    if (BuildConfig.DEBUG) Log.i(TAG, "Headset Inserted.")
                    if (wasPlaying && playlistManager.hasCurrentMedia() && settings.getBoolean("enable_play_on_headset_insertion", false))
                        play()
                }
            }/*
             * headset plug events
             */
        }
    }

    private val mediaPlayerListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                if (BuildConfig.DEBUG) Log.i(TAG, "MediaPlayer.Event.Playing")
                executeUpdate()
                publishState()
                audioFocusHelper.changeAudioFocus(true)
                if (!wakeLock.isHeld) wakeLock.acquire()
                if (!keyguardManager.isKeyguardLocked
                        && !playlistManager.videoBackground
                        && !hasRenderer()
                        && playlistManager.switchToVideo()) {
                    hideNotification(true)
                } else {
                    showNotification()
                }
            }
            MediaPlayer.Event.Paused -> {
                if (BuildConfig.DEBUG) Log.i(TAG, "MediaPlayer.Event.Paused")
                executeUpdate()
                publishState()
                showNotification()
                if (wakeLock.isHeld) wakeLock.release()
            }
            MediaPlayer.Event.EncounteredError -> executeUpdate()
            MediaPlayer.Event.PositionChanged -> if (widget != 0) updateWidgetPosition(event.positionChanged)
            MediaPlayer.Event.ESAdded -> if (event.esChangedType == Media.Track.Type.Video && (playlistManager.videoBackground || !playlistManager.switchToVideo())) {
                /* CbAction notification content intent: resume video or resume audio activity */
                updateMetadata()
            }
            MediaPlayer.Event.MediaChanged -> if (BuildConfig.DEBUG) Log.d(TAG, "onEvent: MediaChanged")
        }
        cbActor.offer(CbMediaPlayerEvent(event))
    }

    private val handler = PlaybackServiceHandler(this)

    val sessionPendingIntent: PendingIntent
        get() {
            return when {
                playlistManager.player.isVideoPlaying() -> {//PIP
                    val notificationIntent = Intent(this, VideoPlayerActivity::class.java)
                    PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                }
                playlistManager.videoBackground || canSwitchToVideo() && !currentMediaHasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) -> {//resume video playback
                    /* Resume VideoPlayerActivity from ACTION_REMOTE_SWITCH_VIDEO intent */
                    val notificationIntent = Intent(ACTION_REMOTE_SWITCH_VIDEO)
                    PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                }
                else -> { /* Show audio player */
                    val notificationIntent = Intent(this, StartActivity::class.java)
                    PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                }
            }
        }

    @Volatile
    private var isForeground = false

    private var currentWidgetCover: String? = null

    val isPlaying: Boolean
        @MainThread
        get() = playlistManager.player.isPlaying()

    val isSeekable: Boolean
        @MainThread
        get() = playlistManager.player.seekable

    val isPausable: Boolean
        @MainThread
        get() = playlistManager.player.pausable

    val isShuffling: Boolean
        @MainThread
        get() = playlistManager.shuffling

    var repeatType: Int
        @MainThread
        get() = playlistManager.repeating
        @MainThread
        set(repeatType) {
            playlistManager.setRepeatType(repeatType)
            publishState()
        }

    val isVideoPlaying: Boolean
        @MainThread
        get() = playlistManager.player.isVideoPlaying()

    val album: String?
        @MainThread
        get() {
            val media = playlistManager.getCurrentMedia()
            return if (media != null) MediaUtils.getMediaAlbum(this@PlaybackService, media) else null
        }

    val artist: String?
        @MainThread
        get() {
            val media = playlistManager.getCurrentMedia()
            return if (media != null) media.nowPlaying ?: MediaUtils.getMediaArtist(this@PlaybackService, media)
            else null
        }

    val artistPrev: String?
        @MainThread
        get() {
            val prev = playlistManager.getPrevMedia()
            return if (prev != null) MediaUtils.getMediaArtist(this@PlaybackService, prev) else null
        }

    val artistNext: String?
        @MainThread
        get() {
            val next = playlistManager.getNextMedia()
            return if (next != null) MediaUtils.getMediaArtist(this@PlaybackService, next) else null
        }

    val title: String?
        @MainThread
        get() {
            val media = playlistManager.getCurrentMedia()
            return if (media != null) if (media.nowPlaying != null) media.nowPlaying else media.title else null
        }

    val titlePrev: String?
        @MainThread
        get() {
            val prev = playlistManager.getPrevMedia()
            return prev?.title
        }

    val titleNext: String?
        @MainThread
        get() {
            val next = playlistManager.getNextMedia()
            return next?.title
        }

    val coverArt: String?
        @MainThread
        get() {
            val media = playlistManager.getCurrentMedia()
            return media?.artworkMrl
        }

    val prevCoverArt: String?
        @MainThread
        get() {
            val prev = playlistManager.getPrevMedia()
            return prev?.artworkMrl
        }

    val nextCoverArt: String?
        @MainThread
        get() {
            val next = playlistManager.getNextMedia()
            return next?.artworkMrl
        }

    var time: Long
        @MainThread
        get() = playlistManager.player.getCurrentTime()
        @MainThread
        set(time) = playlistManager.player.setTime(time)

    val length: Long
        @MainThread
        get() = playlistManager.player.getLength()

    val lastStats: Media.Stats?
        get() = playlistManager.player.previousMediaStats

    val isPlayingPopup: Boolean
        @MainThread
        get() = popupManager != null

    val mediaListSize: Int
        get() = playlistManager.getMediaListSize()

    val medias: List<MediaWrapper>
        @MainThread
        get() = playlistManager.getMediaList()

    val mediaLocations: List<String>
        @MainThread
        get() {
            return mutableListOf<String>().apply { for (mw in playlistManager.getMediaList()) add(mw.location) }
        }

    val currentMediaLocation: String?
        @MainThread
        get() = playlistManager.getCurrentMedia()?.location

    val currentMediaPosition: Int
        @MainThread
        get() = playlistManager.currentIndex

    val currentMediaWrapper: MediaWrapper?
        @MainThread
        get() = this@PlaybackService.playlistManager.getCurrentMedia()

    val rate: Float
        @MainThread
        get() = playlistManager.player.getRate()

    val titles: Array<out MediaPlayer.Title>?
        @MainThread
        get() = playlistManager.player.getTitles()

    var chapterIdx: Int
        @MainThread
        get() = playlistManager.player.getChapterIdx()
        @MainThread
        set(chapter) = playlistManager.player.setChapterIdx(chapter)

    var titleIdx: Int
        @MainThread
        get() = playlistManager.player.getTitleIdx()
        @MainThread
        set(title) = playlistManager.player.setTitleIdx(title)

    val volume: Int
        @MainThread
        get() = playlistManager.player.getVolume()

    val audioTracksCount: Int
        @MainThread
        get() = playlistManager.player.getAudioTracksCount()

    val audioTracks: Array<out MediaPlayer.TrackDescription>?
        @MainThread
        get() = playlistManager.player.getAudioTracks()

    val audioTrack: Int
        @MainThread
        get() = playlistManager.player.getAudioTrack()

    val videoTracksCount: Int
        @MainThread
        get() = if (hasMedia()) playlistManager.player.getVideoTracksCount() else 0

    val videoTracks: Array<out MediaPlayer.TrackDescription>?
        @MainThread
        get() = playlistManager.player.getVideoTracks()

    val currentVideoTrack: Media.VideoTrack?
        @MainThread
        get() = playlistManager.player.getCurrentVideoTrack()

    val videoTrack: Int
        @MainThread
        get() = playlistManager.player.getVideoTrack()

    val spuTracks: Array<out MediaPlayer.TrackDescription>?
        @MainThread
        get() = playlistManager.player.getSpuTracks()

    val spuTrack: Int
        @MainThread
        get() = playlistManager.player.getSpuTrack()

    val spuTracksCount: Int
        @MainThread
        get() = playlistManager.player.getSpuTracksCount()

    val audioDelay: Long
        @MainThread
        get() = playlistManager.player.getAudioDelay()

    val spuDelay: Long
        @MainThread
        get() = playlistManager.player.getSpuDelay()

    interface Callback {
        fun update()
        fun onMediaEvent(event: Media.Event)
        fun onMediaPlayerEvent(event: MediaPlayer.Event)
    }

    private inner class LocalBinder : Binder() {
        internal val service: PlaybackService
            get() = this@PlaybackService
    }

    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()
        settings = Settings.getInstance(this)
        playlistManager = PlaylistManager(this)
        Util.checkCpuCompatibility(this)

        medialibrary = VLCApplication.getMLInstance()
        if (!medialibrary.isInitiated) registerMedialibrary(null)

        detectHeadset = settings.getBoolean("enable_headset_detection", true)

        // Make sure the audio player will acquire a wake-lock while playing. If we don't do
        // that, the CPU might go to sleep while the song is playing, causing playback to stop.
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)

        updateHasWidget()
        initMediaSession()

        val filter = IntentFilter().apply {
            priority = Integer.MAX_VALUE
            addAction(ACTION_REMOTE_BACKWARD)
            addAction(ACTION_REMOTE_PLAYPAUSE)
            addAction(ACTION_REMOTE_PLAY)
            addAction(ACTION_REMOTE_PAUSE)
            addAction(ACTION_REMOTE_STOP)
            addAction(ACTION_REMOTE_FORWARD)
            addAction(ACTION_REMOTE_LAST_PLAYLIST)
            addAction(ACTION_REMOTE_LAST_VIDEO_PLAYLIST)
            addAction(ACTION_REMOTE_SWITCH_VIDEO)
            addAction(VLCAppWidgetProvider.ACTION_WIDGET_INIT)
            addAction(VLCAppWidgetProvider.ACTION_WIDGET_ENABLED)
            addAction(VLCAppWidgetProvider.ACTION_WIDGET_DISABLED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(VLCApplication.SLEEP_INTENT)
            addAction(ACTION_CAR_MODE_EXIT)
        }
        registerReceiver(receiver, filter)

        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    override fun onStart(intent: Intent?, startId: Int) {
        dispatcher.onServicePreSuperOnStart()
        super.onStart(intent, startId)
    }

    private fun updateHasWidget() {
        val manager = AppWidgetManager.getInstance(this)
        widget = when {
            manager.getAppWidgetIds(ComponentName(this, VLCAppWidgetProviderWhite::class.java)).isNotEmpty() -> 1
            manager.getAppWidgetIds(ComponentName(this, VLCAppWidgetProviderBlack::class.java)).isNotEmpty() -> 2
            else -> 0
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (AndroidUtil.isOOrLater && !isForeground && !VLCApplication.isForeground()) forceForeground()
        when (intent?.action) {
            Intent.ACTION_MEDIA_BUTTON -> {
                if (AndroidDevices.hasTsp || AndroidDevices.hasPlayServices) androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)
            }
            ACTION_REMOTE_PLAYPAUSE,
            ACTION_REMOTE_PLAY,
            ACTION_REMOTE_LAST_PLAYLIST -> {
                if (playlistManager.hasCurrentMedia()) play()
                else loadLastAudioPlaylist()
            }
            ACTION_PLAY_FROM_SEARCH -> {
                if (!this::mediaSession.isInitialized) initMediaSession()
                val extras = intent!!.getBundleExtra(EXTRA_SEARCH_BUNDLE)
                mediaSession.controller.transportControls
                        .playFromSearch(extras.getString(SearchManager.QUERY), extras)
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        if (settings.getBoolean("audio_task_removed", false)) stopSelf()
    }

    override fun onDestroy() {
        dispatcher.onServicePreSuperOnDestroy()
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (this::mediaSession.isInitialized) mediaSession.release()
        //Call it once mediaSession is null, to not publish playback state
        stop(true)

        unregisterReceiver(receiver)
        playlistManager.onServiceDestroyed()
    }

    override fun onBind(intent: Intent): IBinder? {
        dispatcher.onServicePreSuperOnBind()
        return if (androidx.media.MediaBrowserServiceCompat.SERVICE_INTERFACE == intent.action) super.onBind(intent) else mBinder
    }

    val vout : IVLCVout?
        get() {
            return playlistManager.player.getVout()
        }

    private fun forceForeground() {
        val ctx = this@PlaybackService
        val notification = NotificationHelper.createPlaybackNotification(ctx,false,
                ctx.resources.getString(R.string.loading), "", "",null,
                false, mediaSession.sessionToken, sessionPendingIntent)
        startForeground(3, notification)
        isForeground = true
    }

    private fun sendStartSessionIdIntent() {
        val sessionId = VLCOptions.getAudiotrackSessionId()
        if (sessionId == 0) return

        val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
        if (isVideoPlaying) intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
        else intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        sendBroadcast(intent)
    }

    private fun sendStopSessionIdIntent() {
        val sessionId = VLCOptions.getAudiotrackSessionId()
        if (sessionId == 0) return

        val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
        sendBroadcast(intent)
    }

    fun setBenchmark() {
        playlistManager.isBenchmark = true
    }

    fun setHardware() {
        playlistManager.isHardware = true
    }

    fun onMediaPlayerEvent(event: MediaPlayer.Event) = mediaPlayerListener.onEvent(event)

    fun onPlaybackStopped(systemExit: Boolean) {
        if (!systemExit) hideNotification(isForeground)
        removePopup()
        if (wakeLock.isHeld) wakeLock.release()
        audioFocusHelper.changeAudioFocus(false)
        medialibrary.resumeBackgroundOperations()
        // We must publish state before resetting mCurrentIndex
        publishState()
        executeUpdate()
    }

    private fun canSwitchToVideo() = playlistManager.player.canSwitchToVideo()

    fun onMediaEvent(event: Media.Event) = cbActor.offer(CbMediaEvent(event))

    fun executeUpdate() {
        cbActor.offer(CbUpdate)
        updateWidget()
        updateMetadata()
        broadcastMetadata()
    }

    private class PlaybackServiceHandler(owner: PlaybackService) : WeakHandler<PlaybackService>(owner) {
        private var lastPublicationDate = 0L

        override fun handleMessage(msg: Message) {
            val service = owner ?: return
            when (msg.what) {
                SHOW_TOAST -> {
                    val bundle = msg.data
                    val text = bundle.getString("text")
                    val duration = bundle.getInt("duration")
                    Toast.makeText(VLCApplication.getAppContext(), text, duration).show()
                }
                END_MEDIASESSION -> if (service::mediaSession.isInitialized) service.mediaSession.isActive = false
            }
        }
    }

    fun showNotification() : Boolean {
        notificationShowing = true
        return cbActor.offer(ShowNotification)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun showNotificationInternal() {
        if (!AndroidDevices.isAndroidTv && AndroidDevices.showTvUi(this)) return
        if (isPlayingPopup || !hasRenderer() && isVideoPlaying) {
            hideNotificationInternal(true)
            return
        }
        val mw = playlistManager.getCurrentMedia()
        if (mw != null) {
            val coverOnLockscreen = settings.getBoolean("lockscreen_cover", true)
            val playing = isPlaying
            val sessionToken = mediaSession.sessionToken
            val ctx = this
            val metaData = mediaSession.controller.metadata
            launch(Dispatchers.Default) {
                delay(100)
                if (isPlayingPopup || !notificationShowing) return@launch
                try {
                    val title = if (metaData == null) mw.title else metaData.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                    val artist = if (metaData == null) mw.artist else metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)
                    val album = if (metaData == null) mw.album else metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)
                    var cover = if (coverOnLockscreen && metaData != null)
                        metaData.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                    else
                        AudioUtil.readCoverBitmap(Uri.decode(mw.artworkMrl), 256)
                    if (cover == null || cover.isRecycled)
                        cover = BitmapFactory.decodeResource(ctx.resources, R.drawable.ic_no_media)

                    val notification = NotificationHelper.createPlaybackNotification(ctx,
                            mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO), title, artist, album,
                            cover, playing, sessionToken, sessionPendingIntent)
                    if (isPlayingPopup) return@launch
                    if (!AndroidUtil.isLolliPopOrLater || playing || audioFocusHelper.lossTransient) {
                        if (!isForeground) {
                            this@PlaybackService.startForeground(3, notification)
                            isForeground = true
                        } else
                            NotificationManagerCompat.from(ctx).notify(3, notification)
                    } else {
                        if (isForeground) {
                            ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_DETACH)
                            isForeground = false
                        }
                        NotificationManagerCompat.from(ctx).notify(3, notification)
                    }
                } catch (e: IllegalArgumentException) {
                    // On somme crappy firmwares, shit can happen
                    Log.e(TAG, "Failed to display notification", e)
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Failed to display notification", e)
                }
            }
        }
    }

    private fun currentMediaHasFlag(flag: Int): Boolean {
        val mw = playlistManager.getCurrentMedia()
        return mw != null && mw.hasFlag(flag)
    }

    private fun hideNotification(remove: Boolean) : Boolean {
        notificationShowing = false
        return cbActor.offer(HideNotification(remove))
    }

    private fun hideNotificationInternal(remove: Boolean) {
        if (!isPlayingPopup && isForeground) {
            ServiceCompat.stopForeground(this@PlaybackService, if (remove) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH)
            isForeground = false
        }
        NotificationManagerCompat.from(this@PlaybackService).cancel(3)
    }

    fun onNewPlayback() = mediaSession.setSessionActivity(sessionPendingIntent)

    fun onPlaylistLoaded() {
        notifyTrackChanged()
        updateMediaQueue()
    }

    @MainThread
    fun pause() = playlistManager.pause()

    @MainThread
    fun play() = playlistManager.play()

    @MainThread
    @JvmOverloads
    fun stop(systemExit: Boolean = false) {
        playlistManager.stop(systemExit)
    }

    private fun initMediaSession() {
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)

        mediaButtonIntent.setClass(this, androidx.media.session.MediaButtonReceiver::class.java)
        val mbrIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0)
        val mbrName = ComponentName(this, androidx.media.session.MediaButtonReceiver::class.java)

        mediaSession = MediaSessionCompat(this, "VLC", mbrName, mbrIntent)
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setCallback(MediaSessionCallback(this))
        try {
            mediaSession.isActive = true
        } catch (e: NullPointerException) {
            // Some versions of KitKat do not support AudioManager.registerMediaButtonIntent
            // with a PendingIntent. They will throw a NullPointerException, in which case
            // they should be able to activate a MediaSessionCompat with only transport
            // controls.
            mediaSession.isActive = false
            mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            mediaSession.isActive = true
        }

        sessionToken = mediaSession.sessionToken
    }

    private fun updateMetadata() {
        cbActor.offer(UpdateMeta)
    }

    private suspend fun updateMetadataInternal() {
        val media = playlistManager.getCurrentMedia() ?: return
        if (!this::mediaSession.isInitialized) initMediaSession()
        val ctx = this
        val length = length
        val bob = withContext(Dispatchers.Default) {
            val title = media.nowPlaying ?: media.title
            val coverOnLockscreen = settings.getBoolean("lockscreen_cover", true)
            val bob = MediaMetadataCompat.Builder().apply {
                putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, BrowserProvider.generateMediaId(media))
                putString(MediaMetadataCompat.METADATA_KEY_GENRE, MediaUtils.getMediaGenre(ctx, media))
                putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, media.trackNumber.toLong())
                putString(MediaMetadataCompat.METADATA_KEY_ARTIST, MediaUtils.getMediaArtist(ctx, media))
                putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, MediaUtils.getMediaReferenceArtist(ctx, media))
                putString(MediaMetadataCompat.METADATA_KEY_ALBUM, MediaUtils.getMediaAlbum(ctx, media))
                putLong(MediaMetadataCompat.METADATA_KEY_DURATION, length)
            }
            if (coverOnLockscreen) {
                val cover = AudioUtil.readCoverBitmap(Uri.decode(media.artworkMrl), 512)
                if (cover?.config != null)
                //In case of format not supported
                    bob.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover.copy(cover.config, false))
            }
            bob.putLong("shuffle", 1L)
            bob.putLong("repeat", repeatType.toLong())
            return@withContext bob
        }
        if (this@PlaybackService::mediaSession.isInitialized) mediaSession.setMetadata(bob.build())
    }

    private fun publishState() {
        if (!this::mediaSession.isInitialized) return
        if (AndroidDevices.isAndroidTv) handler.removeMessages(END_MEDIASESSION)
        val pscb = PlaybackStateCompat.Builder()
        var actions = PLAYBACK_BASE_ACTIONS
        val hasMedia = playlistManager.hasCurrentMedia()
        var time = time
        var state = playlistManager.player.playbackState
        when (state) {
            PlaybackStateCompat.STATE_PLAYING -> actions = actions or (PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
            PlaybackStateCompat.STATE_PAUSED -> actions = actions or (PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP)
            else -> {
                actions = actions or PlaybackStateCompat.ACTION_PLAY
                val media = if (AndroidDevices.isAndroidTv && !AndroidUtil.isOOrLater && hasMedia) playlistManager.getCurrentMedia() else null
                if (media != null) { // Hack to show a now paying card on Android TV
                    val length = media.length
                    time = media.time
                    val progress = if (length <= 0L) 0f else time / length.toFloat()
                    if (progress < 0.95f) {
                        state = PlaybackStateCompat.STATE_PAUSED
                        handler.sendEmptyMessageDelayed(END_MEDIASESSION, 900_000L)
                    }
                }
            }
        }
        pscb.setState(state, time, playlistManager.player.getRate())
        val repeatType = playlistManager.repeating
        if (repeatType != REPEAT_NONE || hasNext())
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        if (repeatType != REPEAT_NONE || hasPrevious() || isSeekable)
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        if (isSeekable)
            actions = actions or PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_SEEK_TO
        actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
        pscb.setActions(actions)
        val repeatResId = if (repeatType == REPEAT_ALL) R.drawable.ic_auto_repeat_pressed else if (repeatType == REPEAT_ONE) R.drawable.ic_auto_repeat_one_pressed else R.drawable.ic_auto_repeat_normal
        if (playlistManager.hasPlaylist())
            pscb.addCustomAction("shuffle", getString(R.string.shuffle_title), if (isShuffling) R.drawable.ic_auto_shuffle_pressed else R.drawable.ic_auto_shuffle_normal)
        pscb.addCustomAction("repeat", getString(R.string.repeat_title), repeatResId)

        val mediaIsActive = state != PlaybackStateCompat.STATE_STOPPED
        val update = mediaSession.isActive != mediaIsActive
        mediaSession.setPlaybackState(pscb.build())
        mediaSession.isActive = mediaIsActive
        mediaSession.setQueueTitle(getString(R.string.music_now_playing))
        if (update) {
            if (mediaIsActive) sendStartSessionIdIntent()
            else sendStopSessionIdIntent()
        }
    }

    private fun notifyTrackChanged() {
        updateMetadata()
        updateWidget()
        broadcastMetadata()
    }

    private fun onMediaListChanged() {
        executeUpdate()
        updateMediaQueue()
    }

    @MainThread
    fun next() = playlistManager.next()

    @MainThread
    fun previous(force: Boolean) = playlistManager.previous(force)

    @MainThread
    fun shuffle() {
        playlistManager.shuffle()
        publishState()
    }

    private fun updateWidget() {
        if (widget != 0 && !isVideoPlaying) {
            updateWidgetState()
            updateWidgetCover()
        }
    }

    private fun sendWidgetBroadcast(intent: Intent) {
        intent.component = ComponentName(this@PlaybackService, if (widget == 1) VLCAppWidgetProviderWhite::class.java else VLCAppWidgetProviderBlack::class.java)
        sendBroadcast(intent)
    }

    private fun updateWidgetState() {
        val media = playlistManager.getCurrentMedia()
        val widgetIntent = Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE)
        if (playlistManager.hasCurrentMedia()) {
            widgetIntent.putExtra("title", media!!.title)
            widgetIntent.putExtra("artist", if (media.isArtistUnknown!! && media.nowPlaying != null)
                media.nowPlaying
            else
                MediaUtils.getMediaArtist(this@PlaybackService, media))
        } else {
            widgetIntent.putExtra("title", getString(R.string.widget_default_text))
            widgetIntent.putExtra("artist", "")
        }
        widgetIntent.putExtra("isplaying", isPlaying)
        sendWidgetBroadcast(widgetIntent)
    }

    private fun updateWidgetCover() {
        val mw = playlistManager.getCurrentMedia()
        val newWidgetCover = mw?.artworkMrl
        if (!TextUtils.equals(currentWidgetCover, newWidgetCover)) {
            currentWidgetCover = newWidgetCover
            sendWidgetBroadcast(Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_COVER)
                    .putExtra("artworkMrl", newWidgetCover))
        }
    }

    private fun updateWidgetPosition(pos: Float) {
        val mw = playlistManager.getCurrentMedia()
        if (mw == null || widget == 0 || isVideoPlaying) return
        // no more than one widget mUpdateMeta for each 1/50 of the song
        val timestamp = System.currentTimeMillis()
        if (!playlistManager.hasCurrentMedia() || timestamp - widgetPositionTimestamp < mw.length / 50)
            return
        widgetPositionTimestamp = timestamp
        sendWidgetBroadcast(Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_POSITION)
                .putExtra("position", pos))
    }

    private fun broadcastMetadata() {
        val media = playlistManager.getCurrentMedia()
        if (media == null || isVideoPlaying) return
        sendBroadcast(Intent("com.android.music.metachanged")
                .putExtra("track", media.title)
                .putExtra("artist", media.artist)
                .putExtra("album", media.album)
                .putExtra("duration", media.length)
                .putExtra("playing", isPlaying)
                .putExtra("package", "org.videolan.vlc"))
    }

    private fun loadLastAudioPlaylist() {
        if (AndroidDevices.isAndroidTv) return
        runOnceReady(Runnable { if (!playlistManager.loadLastPlaylist()) stopSelf() })
    }

    fun loadLastPlaylist(type: Int) {
        runOnceReady(Runnable { playlistManager.loadLastPlaylist(type) })
    }

    fun showToast(text: String, duration: Int) {
        val msg = Message().apply {
            what = SHOW_TOAST
            data = Bundle(2).apply {
                putString("text", text)
                putInt("duration", duration)
            }
        }
        handler.sendMessage(msg)
    }

    @MainThread
    fun canShuffle() = playlistManager.canShuffle()

    @MainThread
    fun hasMedia() = PlaylistManager.hasMedia()

    @MainThread
    fun hasPlaylist() = playlistManager.hasPlaylist()

    @MainThread
    fun addCallback(cb: Callback) = cbActor.offer(CbAdd(cb))

    @MainThread
    fun removeCallback(cb: Callback) = cbActor.offer(CbRemove(cb))

    fun restartMediaPlayer() = playlistManager.player.restart()

    fun saveMediaMeta() = playlistManager.saveMediaMeta()

    fun isValidIndex(positionInPlaylist: Int) = playlistManager.isValidPosition(positionInPlaylist)

    /**
     * Loads a selection of files (a non-user-supplied collection of media)
     * into the primary or "currently playing" playlist.
     *
     * @param mediaPathList A list of locations to load
     * @param position The position to start playing at
     */
    @MainThread
    private fun loadLocations(mediaPathList: List<String>, position: Int) = playlistManager.loadLocations(mediaPathList, position)

    @MainThread
    fun loadUri(uri: Uri?) = loadLocation(uri!!.toString())

    @MainThread
    fun loadLocation(mediaPath: String) = loadLocations(listOf(mediaPath), 0)

    @MainThread
    fun load(mediaList: Array<MediaWrapper>?, position: Int) {
        mediaList?.let { load(it.toList(), position) }
    }

    @MainThread
    fun load(mediaList: List<MediaWrapper>, position: Int) = playlistManager.load(mediaList, position)

    private fun updateMediaQueue() {
        val queue = LinkedList<MediaSessionCompat.QueueItem>()
        var position = -1L
        for (media in playlistManager.getMediaList()) {
            var title: String? = media.nowPlaying
            if (title == null) title = media.title
            val builder = MediaDescriptionCompat.Builder()
            builder.setTitle(title)
                    .setDescription(Util.getMediaDescription(MediaUtils.getMediaArtist(this, media), MediaUtils.getMediaAlbum(this, media)))
                    .setIconBitmap(BitmapUtil.getPictureFromCache(media))
                    .setMediaUri(media.uri)
                    .setMediaId(BrowserProvider.generateMediaId(media))
            queue.add(MediaSessionCompat.QueueItem(builder.build(), ++position))
        }
        mediaSession.setQueue(queue)
    }

    @MainThread
    fun load(media: MediaWrapper) = load(listOf(media), 0)

    /**
     * Play a media from the media list (playlist)
     *
     * @param index The index of the media
     * @param flags LibVLC.MEDIA_* flags
     */
    @JvmOverloads
    fun playIndex(index: Int, flags: Int = 0) = launch { playlistManager.playIndex(index, flags) }

    @MainThread
    fun flush() {
        /* HACK: flush when activating a video track. This will force an
         * I-Frame to be displayed right away. */
        if (isSeekable) {
            val time = time
            if (time > 0)
                seek(time)
        }
    }

    /**
     * Use this function to show an URI in the audio interface WITHOUT
     * interrupting the stream.
     *
     * Mainly used by VideoPlayerActivity in response to loss of video track.
     */

    @MainThread
    fun showWithoutParse(index: Int) {
        playlistManager.setVideoTrackEnabled(false)
        val media = playlistManager.getMedia(index)
        if (media == null || !isPlaying) return
        // Show an URI without interrupting/losing the current stream
        if (BuildConfig.DEBUG) Log.v(TAG, "Showing index " + index + " with playing URI " + media.uri)
        playlistManager.currentIndex = index
        notifyTrackChanged()
        PlaylistManager.showAudioPlayer.value = !isVideoPlaying
        showNotification()
    }

    fun setVideoTrackEnabled(enabled: Boolean) = playlistManager.setVideoTrackEnabled(enabled)

    fun switchToVideo() = playlistManager.switchToVideo()

    @MainThread
    fun switchToPopup(index: Int) {
        playlistManager.saveMediaMeta()
        showWithoutParse(index)
        showPopup()
    }

    @MainThread
    fun removePopup() {
        popupManager?.removePopup()
        popupManager = null
    }

    @MainThread
    private fun showPopup() {
        if (popupManager == null) popupManager = PopupManager(this)
        popupManager!!.showPopup()
        hideNotification(true)
    }

    /**
     * Append to the current existing playlist
     */

    @MainThread
    fun append(mediaList: Array<MediaWrapper>) = append(mediaList.toList())

    @MainThread
    fun append(mediaList: List<MediaWrapper>) = launch {
        playlistManager.append(mediaList)
        onMediaListChanged()
    }

    @MainThread
    fun append(media: MediaWrapper) = append(listOf(media))

    /**
     * Insert into the current existing playlist
     */

    @MainThread
    fun insertNext(mediaList: Array<MediaWrapper>) = insertNext(mediaList.toList())

    @MainThread
    private fun insertNext(mediaList: List<MediaWrapper>) {
        playlistManager.insertNext(mediaList)
        onMediaListChanged()
    }

    @MainThread
    fun insertNext(media: MediaWrapper) = insertNext(listOf(media))

    /**
     * Move an item inside the playlist.
     */
    @MainThread
    fun moveItem(positionStart: Int, positionEnd: Int) = playlistManager.moveItem(positionStart, positionEnd)

    @MainThread
    fun insertItem(position: Int, mw: MediaWrapper) = playlistManager.insertItem(position, mw)


    @MainThread
    fun remove(position: Int)  = playlistManager.remove(position)

    @MainThread
    fun removeLocation(location: String) = playlistManager.removeLocation(location)

    @MainThread
    operator fun hasNext() = playlistManager.hasNext()

    @MainThread
    fun hasPrevious() = playlistManager.hasPrevious()

    @MainThread
    fun detectHeadset(enable: Boolean) {
        detectHeadset = enable
    }

    @MainThread
    fun setRate(rate: Float, save: Boolean) = playlistManager.player.setRate(rate, save)

    @MainThread
    fun navigate(where: Int) = playlistManager.player.navigate(where)

    @MainThread
    fun getChapters(title: Int) = playlistManager.player.getChapters(title)

    @MainThread
    fun setVolume(volume: Int) = playlistManager.player.setVolume(volume)

    @MainThread
    @JvmOverloads
    fun seek(position: Long, length: Double = this.length.toDouble()) {
        if (length > 0.0) setPosition((position / length).toFloat())
        else time = position
    }

    @MainThread
    fun updateViewpoint(yaw: Float, pitch: Float, roll: Float, fov: Float, absolute: Boolean): Boolean {
        return playlistManager.player.updateViewpoint(yaw, pitch, roll, fov, absolute)
    }

    @MainThread
    fun saveStartTime(time: Long) {
        playlistManager.savedTime = time
    }

    @MainThread
    private fun setPosition(pos: Float) = playlistManager.player.setPosition(pos)

    @MainThread
    fun setAudioTrack(index: Int) = playlistManager.player.setAudioTrack(index)

    @MainThread
    fun setAudioDigitalOutputEnabled(enabled: Boolean) = playlistManager.player.setAudioDigitalOutputEnabled(enabled)

    @MainThread
    fun setVideoTrack(index: Int) = playlistManager.player.setVideoTrack(index)

    @MainThread
    fun addSubtitleTrack(path: String, select: Boolean) = playlistManager.player.addSubtitleTrack(path, select)

    @MainThread
    fun addSubtitleTrack(uri: Uri, select: Boolean) = playlistManager.player.addSubtitleTrack(uri, select)

    @MainThread
    fun setSpuTrack(index: Int) = playlistManager.setSpuTrack(index)

    @MainThread
    fun setAudioDelay(delay: Long) = playlistManager.setAudioDelay(delay)

    @MainThread
    fun setSpuDelay(delay: Long) = playlistManager.setSpuDelay(delay)

    @MainThread
    fun hasRenderer() = playlistManager.player.hasRenderer

    @MainThread
    fun setRenderer(item: RendererItem?) {
        val wasOnRenderer = hasRenderer()
        if (wasOnRenderer && !hasRenderer() && canSwitchToVideo())
            VideoPlayerActivity.startOpened(applicationContext,
                    playlistManager.getCurrentMedia()!!.uri, playlistManager.currentIndex)
        playlistManager.setRenderer(item)
        if (!wasOnRenderer && item != null) audioFocusHelper.changeAudioFocus(false)
        else if (wasOnRenderer && item == null && isPlaying) audioFocusHelper.changeAudioFocus(true)
    }

    @MainThread
    fun setEqualizer(equalizer: MediaPlayer.Equalizer?) = playlistManager.player.setEqualizer(equalizer)

    @MainThread
    fun setVideoScale(scale: Float) = playlistManager.player.setVideoScale(scale)

    @MainThread
    fun setVideoAspectRatio(aspect: String?) = playlistManager.player.setVideoAspectRatio(aspect)

    override fun getLifecycle() = dispatcher.lifecycle!!

    class Client(private val mContext: Context?, private val mCallback: Callback?) {

        private var mBound = false

        private val mServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, iBinder: IBinder) {
                if (!mBound) return

                val service = PlaybackService.getService(iBinder)
                if (service != null) mCallback?.onConnected(service)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                mBound = false
                mCallback?.onDisconnected()
            }
        }

        @MainThread
        interface Callback {
            fun onConnected(service: PlaybackService)
            fun onDisconnected()
        }

        init {
            if (mContext == null || mCallback == null) throw IllegalArgumentException("Context and callback can't be null")
        }

        @MainThread
        fun connect() {
            if (mBound) throw IllegalStateException("already connected")
            val serviceIntent = getServiceIntent(mContext!!)
            if (mContext is Activity) mContext.startService(serviceIntent)
            else ContextCompat.startForegroundService(mContext, serviceIntent)
            mBound = mContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
        }

        @MainThread
        fun disconnect() {
            if (mBound) {
                mBound = false
                mContext?.unbindService(mServiceConnection)
            }
        }

        companion object {
            const val TAG = "PlaybackService.Client"

            fun getServiceIntent(context: Context): Intent {
                return Intent(context, PlaybackService::class.java)
            }

            private fun startService(context: Context) {
                ContextCompat.startForegroundService(context, getServiceIntent(context))
            }

            private fun stopService(context: Context) {
                context.stopService(getServiceIntent(context))
            }

            fun restartService(context: Context) {
                stopService(context)
                startService(context)
            }
        }
    }

    /*
     * Browsing
     */

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): androidx.media.MediaBrowserServiceCompat.BrowserRoot? {
        return if (Permissions.canReadStorage(this@PlaybackService)) androidx.media.MediaBrowserServiceCompat.BrowserRoot(BrowserProvider.ID_ROOT, null) else null
    }

    override fun onLoadChildren(parentId: String, result: androidx.media.MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        result.detach()
        if (!medialibrary.isInitiated || libraryReceiver != null)
            registerMedialibrary(Runnable { sendResults(result, parentId) })
        else sendResults(result, parentId)
    }

    private fun sendResults(result: androidx.media.MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>, parentId: String) {
        launch(Dispatchers.IO) {
            try {
                result.sendResult(BrowserProvider.browse(applicationContext, parentId))
            } catch (ignored: RuntimeException) {} //bitmap parcelization can fail
        }
    }


    private val cbActor by lazy {
        actor<CbAction>(capacity = Channel.UNLIMITED) {
            for (update in channel) when (update) {
                CbUpdate -> for (callback in callbacks) callback.update()
                is CbMediaEvent -> for (callback in callbacks) callback.onMediaEvent(update.event)
                is CbMediaPlayerEvent -> for (callback in callbacks) callback.onMediaPlayerEvent(update.event)
                is CbRemove -> callbacks.remove(update.cb)
                is CbAdd -> callbacks.add(update.cb)
                ShowNotification -> showNotificationInternal()
                is HideNotification -> hideNotificationInternal(update.remove)
                UpdateMeta -> updateMetadataInternal()
            }
        }
    }

    companion object {


        private const val SHOW_TOAST = 1
        private const val END_MEDIASESSION = 2

        fun getService(iBinder: IBinder): PlaybackService? {
            val binder = iBinder as LocalBinder
            return binder.service
        }

        fun loadLastAudio(context: Context) {
            val i = PlaybackService.Client.getServiceIntent(context).apply { action = ACTION_REMOTE_LAST_PLAYLIST }
            ContextCompat.startForegroundService(context, i)
        }

        private const val PLAYBACK_BASE_ACTIONS = (PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PLAY_FROM_URI
                or PlaybackStateCompat.ACTION_PLAY_PAUSE)
    }
}

// Actor actions sealed classes
private sealed class CbAction
private object CbUpdate : CbAction()
private class CbMediaEvent(val event : Media.Event) : CbAction()
private class CbMediaPlayerEvent(val event : MediaPlayer.Event) : CbAction()
private class CbAdd(val cb : PlaybackService.Callback) : CbAction()
private class CbRemove(val cb : PlaybackService.Callback) : CbAction()
private object ShowNotification : CbAction()
private class HideNotification(val remove: Boolean) : CbAction()
private object UpdateMeta : CbAction()