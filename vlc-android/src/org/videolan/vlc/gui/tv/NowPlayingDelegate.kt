package org.videolan.vlc.gui.tv

import androidx.lifecycle.Observer
import org.videolan.libvlc.MediaPlayer
import org.videolan.medialibrary.media.DummyItem
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.PlaybackServiceActivity
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.CATEGORY_NOW_PLAYING
import org.videolan.vlc.util.EmptyPBSCallback


class NowPlayingDelegate(private val fragment: MainTvFragment): PlaybackService.Client.Callback, PlaybackService.Callback by EmptyPBSCallback {
    private var service: PlaybackService? = null
    private val helper = PlaybackServiceActivity.Helper(fragment.requireActivity(), this)
    private val observer = Observer<Boolean> {
        when {
            it != true -> {
                helper.onStop()
                updateCurrent()
            }
            service === null -> helper.onStart()
            else -> updateCurrent()
        }
    }

    init {
        PlaylistManager.showAudioPlayer.observe(fragment, observer)
    }

    fun onClear() {
        PlaylistManager.showAudioPlayer.removeObserver(observer)
    }

    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.MediaChanged -> updateCurrent()
        }
    }

    private fun updateCurrent() {
        fragment.updateAudioCategories(service?.currentMediaWrapper?.let {
            DummyItem(CATEGORY_NOW_PLAYING, it.title, it.artist).apply { setArtWork(service?.coverArt) }
        })
        fragment.updateHistory()
    }

    override fun onConnected(service: PlaybackService) {
        this.service = service
        updateCurrent()
        service.addCallback(this)
    }

    override fun onDisconnected() {
        service?.removeCallback(this)
        service = null
    }
}
