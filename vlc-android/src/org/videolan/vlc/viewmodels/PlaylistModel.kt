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

import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.EmptyPBSCallback
import org.videolan.vlc.util.LiveDataset
import org.videolan.vlc.util.PlaylistFilterDelegate
import org.videolan.vlc.util.REPEAT_NONE

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class PlaylistModel : ScopedModel(), PlaybackService.Callback by EmptyPBSCallback, Observer<PlaybackService> {

    var service: PlaybackService? = null
    val dataset = LiveDataset<AbstractMediaWrapper>()
    private var originalDataset : MutableList<AbstractMediaWrapper>? = null
    val selection : Int
        get() = if (filtering) -1 else service?.playlistManager?.currentIndex ?: -1
    private var filtering = false
    val progress = MediatorLiveData<PlaybackProgress>()
    val playerState = MutableLiveData<PlayerState>()
    val connected : Boolean
        get() = service !== null

    private val filter by lazy(LazyThreadSafetyMode.NONE) { PlaylistFilterDelegate(dataset) }

    private val filterActor by lazy(mode = LazyThreadSafetyMode.NONE) {
        actor<CharSequence?> {
            for (query in channel) filter.filter(query)
        }
    }

    init {
        PlaybackService.service.observeForever(this)
    }

    private fun setup(service: PlaybackService) {
        service.addCallback(this)
        update()
    }

    override fun update() {
        service?.run {
            dataset.value = media.toMutableList()
            playerState.value = PlayerState(isPlaying, title, artist)
        }
    }

    val hasMedia
        get() = service?.hasMedia() ?: false

    fun insertMedia(position: Int, media: AbstractMediaWrapper) = service?.insertItem(position, media)

    fun remove(position: Int) = service?.remove(position)

    fun move(from: Int, to: Int) = service?.moveItem(from, to)

    @MainThread
    fun filter(query: CharSequence?) {
        val filtering = query != null
        if (this.filtering != filtering) {
            this.filtering = filtering
            originalDataset = if (filtering) dataset.value.toMutableList() else null
        }
        filterActor.offer(query)
    }

    val title
        get() = service?.title

    val artist
        get() = service?.artist

    public override fun onCleared() {
        service?.apply {
            removeCallback(this@PlaylistModel)
            progress.removeSource(playlistManager.player.progress)
        }
        PlaybackService.service.removeObserver(this)
        super.onCleared()
    }

    fun getPlaylistPosition(position: Int, media: AbstractMediaWrapper): Int {
        val list = originalDataset ?: dataset.value
        if (position in 0 until list.size && list[position] == media) return position
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

    val length : Long
            get() = service?.length ?: 0L

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

    val currentMediaWrapper : AbstractMediaWrapper?
        get() = service?.currentMediaWrapper

    val currentMediaPosition : Int
        get() = service?.currentMediaPosition ?: -1

    val medias
        get() = service?.media

    fun shuffle() = service?.shuffle()

    fun load(medialist: List<AbstractMediaWrapper>, position: Int) = service?.load(medialist, position)

    suspend fun switchToVideo() : Boolean {
        service?.apply {
            if (PlaylistManager.hasMedia() && !isVideoPlaying && !hasRenderer()) {
                currentMediaWrapper?.run {
                    if (!hasFlag(AbstractMediaWrapper.MEDIA_FORCE_AUDIO) && canSwitchToVideo()) {
                        switchToVideo()
                        return true
                    }
                }
            }
        }
        return false
    }

    suspend fun canSwitchToVideo() = withContext(Dispatchers.IO) { service?.playlistManager?.player?.canSwitchToVideo() ?: false }

    fun toggleABRepeat() = service?.playlistManager?.toggleABRepeat()

    val videoTrackCount
        get() = service?.videoTracksCount ?: 0


    override fun onChanged(service: PlaybackService?) {
        if (service != null) {
            this.service = service
            progress.apply {
                addSource(service.playlistManager.player.progress) {
                    value = PlaybackProgress(it?.time ?: 0L, it?.length ?: 0L)
                }
            }
            setup(service)
        } else {
            this.service?.apply {
                removeCallback(this@PlaylistModel)
                progress.removeSource(playlistManager.player.progress)
            }
            this.service = null
        }
    }

    companion object {
        fun get(fragment: Fragment) = ViewModelProviders.of(fragment.requireActivity()).get(PlaylistModel::class.java)
    }
}

data class PlaybackProgress(
        val time: Long,
        val length: Long,
        val timeText : String = Tools.millisToString(time),
        val lengthText : String  = Tools.millisToString(length))

class PlayerState(val playing: Boolean, val title: String?, val artist: String?)