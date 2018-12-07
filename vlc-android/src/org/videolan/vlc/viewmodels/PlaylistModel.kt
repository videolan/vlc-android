/*****************************************************************************
 * PlaylistModel.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
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
 *****************************************************************************/

package org.videolan.vlc.viewmodels

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.ABRepeat
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.EmptyPBSCallback
import org.videolan.vlc.util.LiveDataset
import org.videolan.vlc.util.PlaylistFilterDelegate
import org.videolan.vlc.util.REPEAT_NONE

class PlaylistModel : ScopedModel(), PlaybackService.Callback by EmptyPBSCallback, PlaybackService.Client.Callback {

    var service: PlaybackService? = null
    val dataset = LiveDataset<MediaWrapper>()
    private var originalDataset : MutableList<MediaWrapper>? = null
    val selection : Int
        get() = if (filtering) -1 else service?.playlistManager?.currentIndex ?: -1
    private var filtering = false
    val progress = MediatorLiveData<PlaybackProgress>()
    val playerState = MutableLiveData<PlayerState>()

    private val filter by lazy(LazyThreadSafetyMode.NONE) { PlaylistFilterDelegate(dataset) }

    private fun setup(service: PlaybackService) {
        service.addCallback(this)
        update()
    }

    override fun update() {
        service?.run {
            dataset.value = medias.toMutableList()
            playerState.value = PlayerState(isPlaying, title, artist)
        }
    }

    val hasMedia
        get() = service?.hasMedia() ?: false

    fun insertMedia(position: Int, media: MediaWrapper) = service?.insertItem(position, media)

    fun remove(position: Int) = service?.remove(position)

    fun move(from: Int, to: Int) = service?.moveItem(from, to)

    fun filter(query: CharSequence?) {
        val filtering = query != null
        if (this.filtering != filtering) {
            this.filtering = filtering
            originalDataset = if (filtering) dataset.value.toMutableList() else null
        }
        launch { filter.filter(query) }
    }

    val title
        get() = service?.title

    val artist
        get() = service?.artist

    public override fun onCleared() {
        service?.removeCallback(this)
        super.onCleared()
    }

    fun getPlaylistPosition(position: Int, media: MediaWrapper): Int {
        val list = originalDataset ?: dataset.value
        if (list[position] == media) return position
        else {
            for ((index, item) in list.withIndex()) if (item == media) {
                return index
            }
        }
        return -1
    }

    fun stopAfter(position: Int) {
        service?.playlistManager?.stopAfter = position
    }

    fun play(position: Int) = service?.playIndex(position)

    fun togglePlayPause() = service?.run{
        if (isPlaying) pause() else play()
    }

    fun stop() = service?.stop()

    fun next() = service?.run {
        if (hasNext()) {
            next()
            true
        } else false
    } ?: false

    fun previous(force : Boolean = false) = service?.run {
        if (hasPrevious() || isSeekable) {
            previous(force)
            true
        } else false
    } ?: false

    var time
        get() = service?.time ?: 0L
        set(value) {
            service?.time = value
        }

    val length = service?.length ?: 0L

    val playing : Boolean
        get() = service?.isPlaying ?: false

    val shuffling : Boolean
        get() = service?.isShuffling ?: false

    val canShuffle : Boolean
        get() = service?.canShuffle() ?: false

    var repeatType : Int
        get() = service?.repeatType ?: REPEAT_NONE
        set(value) {
            service?.repeatType = value
        }

    val currentMediaWrapper : MediaWrapper?
        get() = service?.currentMediaWrapper

    val currentMediaPosition : Int
        get() = service?.currentMediaPosition ?: -1

    val abRepeat = MediatorLiveData<ABRepeat>()

    val medias
        get() = service?.medias

    fun shuffle() = service?.shuffle()

    fun load(medialist: List<MediaWrapper>, position: Int) = service?.load(medialist, position)

    fun switchToVideo() : Boolean {
        service?.apply {
            if (PlaylistManager.hasMedia() && !isVideoPlaying) {
                currentMediaWrapper?.run {
                    if (!hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) && canSwitchToVideo()) {
                        switchToVideo()
                        return true
                    }
                }
            }
        }
        return false
    }

    fun canSwitchToVideo() = service?.playlistManager?.player?.canSwitchToVideo() ?: false

    fun toggleABRepeat() = service?.playlistManager?.toggleABRepeat()

    val videoTrackCount
        get() = service?.videoTracksCount ?: 0

    override fun onConnected(service: PlaybackService) {
        this.service = service
        abRepeat.addSource(service.playlistManager.abRepeat) { value -> value}
        progress.apply {
            addSource(service.playlistManager.player.progress) {
                value = PlaybackProgress(it?.time ?: 0L, it?.length ?: 0L)
            }
        }
        setup(service)
    }

    override fun onDisconnected() {
        service?.apply {
            removeCallback(this@PlaylistModel)
            abRepeat.removeSource(playlistManager.abRepeat)
            progress.removeSource(playlistManager.player.progress)
        }
        service = null
    }

    companion object {
        fun get(fragment: androidx.fragment.app.Fragment) = ViewModelProviders.of(fragment).get(PlaylistModel::class.java)
    }
}

data class PlaybackProgress(
        val time: Long,
        val length: Long,
        val timeText : String = Tools.millisToString(time),
        val lengthText : String  = Tools.millisToString(length))

class PlayerState(val playing: Boolean, val title: String?, val artist: String?)