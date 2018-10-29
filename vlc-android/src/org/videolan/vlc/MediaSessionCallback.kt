package org.videolan.vlc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.media.BrowserProvider
import org.videolan.vlc.util.*

private const val TAG = "VLC/MediaSessionCallback"

internal class MediaSessionCallback(private val playbackService: PlaybackService) : MediaSessionCompat.Callback() {

    override fun onPlay() {
        if (playbackService.hasMedia()) playbackService.play()
        else if (!AndroidDevices.isAndroidTv) PlaybackService.loadLastAudio(playbackService)
    }

    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
        val keyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent? ?: return false
        if (!playbackService.hasMedia()
                && (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            return if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                PlaybackService.loadLastAudio(playbackService)
                true
            } else false
        }
        return super.onMediaButtonEvent(mediaButtonEvent)
    }

    override fun onCustomAction(action: String?, extras: Bundle?) {
        when (action) {
            "shuffle" -> playbackService.shuffle()
            "repeat" -> playbackService.repeatType = when (playbackService.repeatType) {
                REPEAT_NONE -> REPEAT_ALL
                REPEAT_ALL -> REPEAT_ONE
                REPEAT_ONE -> REPEAT_NONE
                else -> REPEAT_NONE
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

    override fun onPlayFromUri(uri: Uri?, extras: Bundle?) = playbackService.loadUri(uri)

    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        if (!playbackService.medialibrary.isInitiated || playbackService.libraryReceiver != null) {
            playbackService.registerMedialibrary(Runnable { onPlayFromSearch(query, extras) })
            return
        }
        playbackService.mediaSession.setPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_CONNECTING, playbackService.time, 1.0f).build())
        GlobalScope.launch(Dispatchers.IO) {
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
                vsp.isSongFocus -> tracks = playbackService.medialibrary.searchMedia(vsp.song)!!
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

    override fun onPause() = playbackService.pause()

    override fun onStop() = playbackService.stop()

    override fun onSkipToNext() = playbackService.next()

    override fun onSkipToPrevious() = playbackService.previous(false)

    override fun onSeekTo(pos: Long) = playbackService.seek(if (pos < 0) playbackService.time + pos else pos)

    override fun onFastForward() = playbackService.seek(Math.min(playbackService.length, playbackService.time + 5000))

    override fun onRewind() = playbackService.seek(Math.max(0, playbackService.time - 5000))

    override fun onSkipToQueueItem(id: Long) {
        playbackService.playIndex(id.toInt())
    }
}