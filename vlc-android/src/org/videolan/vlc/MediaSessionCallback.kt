package org.videolan.vlc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import kotlinx.coroutines.experimental.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.media.BrowserProvider
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Constants
import org.videolan.vlc.util.VoiceSearchParams

internal class MediaSessionCallback(private val playbackService: PlaybackService) : MediaSessionCompat.Callback() {
    private var mHeadsetDownTime = 0L
    private var mHeadsetUpTime = 0L

    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
        if (!playbackService.settings.getBoolean("enable_headset_actions", true) || VLCApplication.showTvUi()) return false
        val event = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        if (event != null && !playbackService.isVideoPlaying) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                    || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                val time = SystemClock.uptimeMillis()
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount <= 0) mHeadsetDownTime = time
                        if (!playbackService.hasMedia()) {
                            MediaUtils.loadlastPlaylistNoUi(playbackService, Constants.PLAYLIST_TYPE_AUDIO)
                            return true
                        }
                    }
                    KeyEvent.ACTION_UP -> if (AndroidDevices.hasTsp) { //no backward/forward on TV
                        when {
                            time - mHeadsetDownTime >= PlaybackService.DELAY_LONG_CLICK -> { // long click
                                mHeadsetUpTime = time
                                playbackService.previous(false)
                                return true
                            }
                            time - mHeadsetUpTime <= PlaybackService.DELAY_DOUBLE_CLICK -> { // double click
                                mHeadsetUpTime = time
                                playbackService.next()
                                return true
                            }
                            else -> {
                                mHeadsetUpTime = time
                                return false
                            }
                        }
                    }
                }
                return false
            } else if (!AndroidUtil.isLolliPopOrLater) {
                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        onSkipToNext()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        onSkipToPrevious()
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onPlay() {
        if (playbackService.hasMedia()) playbackService.play()
        else MediaUtils.loadlastPlaylistNoUi(playbackService, Constants.PLAYLIST_TYPE_AUDIO)
    }

    override fun onCustomAction(action: String?, extras: Bundle?) {
        when (action) {
            "shuffle" -> playbackService.shuffle()
            "repeat" -> playbackService.repeatType = when (playbackService.repeatType) {
                Constants.REPEAT_NONE -> Constants.REPEAT_ALL
                Constants.REPEAT_ALL -> Constants.REPEAT_ONE
                Constants.REPEAT_ONE -> Constants.REPEAT_NONE
                else -> Constants.REPEAT_NONE
            }
        }
    }

    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
        when {
            mediaId.startsWith(BrowserProvider.ALBUM_PREFIX) -> playbackService.load(playbackService.medialibrary.getAlbum(java.lang.Long.parseLong(mediaId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]))!!.tracks, 0)
            mediaId.startsWith(BrowserProvider.PLAYLIST_PREFIX) -> playbackService.load(playbackService.medialibrary.getPlaylist(java.lang.Long.parseLong(mediaId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]))!!.tracks, 0)
            mediaId.startsWith(ExtensionsManager.EXTENSION_PREFIX) -> onPlayFromUri(Uri.parse(mediaId.replace(ExtensionsManager.EXTENSION_PREFIX + "_" + mediaId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1] + "_", "")), null)
            else -> try {
                playbackService.medialibrary.getMedia(mediaId.toLong())?.let { playbackService.load(it) }
            } catch (e: NumberFormatException) {
                playbackService.loadLocation(mediaId)
            }
        }

    }

    override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
        playbackService.loadUri(uri)
    }

    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        if (!playbackService.medialibrary.isInitiated || playbackService.libraryReceiver != null) {
            playbackService.registerMedialibrary(Runnable { onPlayFromSearch(query, extras) })
            return
        }
        playbackService.mediaSession.setPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_CONNECTING, playbackService.time, 1.0f).build())
        launch {
            val vsp = VoiceSearchParams(query, extras)
            var items: Array<out MediaLibraryItem>? = null
            var tracks: Array<MediaWrapper>? = null
            when {
                vsp.isAny -> {
                    items = playbackService.medialibrary.audio
                    if (!playbackService.isShuffling) playbackService.shuffle()
                }
                vsp.isArtistFocus -> items = playbackService.medialibrary.searchArtist(vsp.artist)
                vsp.isAlbumFocus -> items = playbackService.medialibrary.searchAlbum(vsp.album)
                vsp.isGenreFocus -> items = playbackService.medialibrary.searchGenre(vsp.genre)
                vsp.isSongFocus -> tracks = playbackService.medialibrary.searchMedia(vsp.song)!!.tracks
            }
            if (Tools.isArrayEmpty(tracks)) {
                val result = playbackService.medialibrary.search(query)
                if (result != null) {
                    when {
                        !Tools.isArrayEmpty(result.albums) -> tracks = result.albums[0].tracks
                        !Tools.isArrayEmpty(result.artists) -> tracks = result.artists[0].tracks
                        !Tools.isArrayEmpty(result.genres) -> tracks = result.genres[0].tracks
                    }
                }
            }
            if (tracks == null && !Tools.isArrayEmpty(items)) tracks = items!![0].tracks
            if (!Tools.isArrayEmpty(tracks)) playbackService.load(tracks, 0)
        }
    }

    override fun onPause() {
        playbackService.pause()
    }

    override fun onStop() {
        playbackService.stop()
    }

    override fun onSkipToNext() {
        playbackService.next()
    }

    override fun onSkipToPrevious() {
        playbackService.previous(false)
    }

    override fun onSeekTo(pos: Long) {
        playbackService.seek(pos)
    }

    override fun onFastForward() {
        playbackService.seek(Math.min(playbackService.length, playbackService.time + 5000))
    }

    override fun onRewind() {
        playbackService.seek(Math.max(0, playbackService.time - 5000))
    }

    override fun onSkipToQueueItem(id: Long) {
        playbackService.playIndex(id.toInt())
    }
}