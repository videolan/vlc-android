package org.videolan.vlc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.media.MediaSessionBrowser
import org.videolan.vlc.util.*
import java.util.*
import kotlin.math.min

@Suppress("unused")
private const val TAG = "VLC/MediaSessionCallback"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
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
        playbackService.launch {
            val context = playbackService.applicationContext
            when {
                mediaId == MediaSessionBrowser.ID_SHUFFLE_ALL -> {
                    val tracks = context.getFromMl { audio }
                    if (tracks.isNotEmpty() && isActive) {
                        playbackService.load(tracks, Random().nextInt(min(tracks.size, MEDIALIBRARY_PAGE_SIZE)))
                        if (!playbackService.isShuffling) playbackService.shuffle()
                    }
                }
                mediaId.startsWith(MediaSessionBrowser.ALBUM_PREFIX) -> {
                    val tracks = context.getFromMl { getAlbum(mediaId.extractId())?.tracks }
                    if (isActive) tracks?.let { playbackService.load(it, 0) }
                }
                mediaId.startsWith(MediaSessionBrowser.PLAYLIST_PREFIX) -> {
                    val tracks = context.getFromMl { getPlaylist(mediaId.extractId())?.tracks }
                    if (isActive) tracks?.let { playbackService.load(it, 0) }
                }
                mediaId.startsWith(ExtensionsManager.EXTENSION_PREFIX) -> {
                    val id = mediaId.replace(ExtensionsManager.EXTENSION_PREFIX + "_" + mediaId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1] + "_", "")
                    onPlayFromUri(Uri.parse(id), null)
                }
                else -> try {
                    context.getFromMl { getMedia(mediaId.toLong()) }?.let { if (isActive) playbackService.load(it) }
                } catch (e: NumberFormatException) {
                    if (isActive) playbackService.loadLocation(mediaId)
                }
            }
        }
    }

    private fun String.extractId() = split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].toLong()

    override fun onPlayFromUri(uri: Uri?, extras: Bundle?) = playbackService.loadUri(uri)

    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        playbackService.mediaSession.setPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_CONNECTING, playbackService.time, 1.0f).build())
        playbackService.launch(Dispatchers.IO) {
            if (!isActive) return@launch
            playbackService.getFromMl { isStarted }
            val vsp = VoiceSearchParams(query ?: "", extras)
            var items: Array<out MediaLibraryItem>? = null
            var tracks: Array<AbstractMediaWrapper>? = null
            when {
                vsp.isAny -> {
                    items = playbackService.medialibrary.audio.also { if (!playbackService.isShuffling) playbackService.shuffle() }
                }
                vsp.isArtistFocus -> items = playbackService.medialibrary.searchArtist(vsp.artist)
                vsp.isAlbumFocus -> items = playbackService.medialibrary.searchAlbum(vsp.album)
                vsp.isGenreFocus -> items = playbackService.medialibrary.searchGenre(vsp.genre)
                vsp.isPlaylistFocus -> items = playbackService.medialibrary.searchPlaylist(vsp.playlist)
                vsp.isSongFocus -> tracks = playbackService.medialibrary.searchMedia(vsp.song)
            }
            if (!isActive) return@launch
            if (tracks.isNullOrEmpty() && items.isNullOrEmpty() && query?.length ?: 0 > 2) playbackService.medialibrary.search(query)?.run {
                when {
                    !albums.isNullOrEmpty() -> tracks = albums!![0].tracks
                    !artists.isNullOrEmpty() -> tracks = artists!![0].tracks
                    !playlists.isNullOrEmpty() -> tracks = playlists!![0].tracks
                    !genres.isNullOrEmpty() -> tracks = genres!![0].tracks
                }
            }
            if (!isActive) return@launch
            if (tracks.isNullOrEmpty() && !items.isNullOrEmpty()) tracks = items[0].tracks
            if (!tracks.isNullOrEmpty()) playbackService.load(tracks, 0)
        }
    }

    override fun onPause() = playbackService.pause()

    override fun onStop() = playbackService.stop()

    override fun onSkipToNext() = playbackService.next()

    override fun onSkipToPrevious() = playbackService.previous(false)

    override fun onSeekTo(pos: Long) = playbackService.seek(if (pos < 0) playbackService.time + pos else pos, fromUser = true)

    override fun onFastForward() = playbackService.seek(Math.min(playbackService.length, playbackService.time + 5000))

    override fun onRewind() = playbackService.seek(Math.max(0, playbackService.time - 5000))

    override fun onSkipToQueueItem(id: Long) {
        playbackService.playIndex(id.toInt())
    }
}