package org.videolan.vlc.viewmodels

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.support.v4.app.Fragment
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.util.EmptyPBSCallback
import org.videolan.vlc.util.LiveDataset
import org.videolan.vlc.util.PlaylistFilterDelegate

class PlaylistModel(private val service: PlaybackService) : ViewModel(), PlaybackService.Callback by EmptyPBSCallback {

    val dataset = LiveDataset<MediaWrapper>()
    val progress = MutableLiveData<PlaybackProgress>()

    private val filter by lazy(LazyThreadSafetyMode.NONE) { PlaylistFilterDelegate(dataset) }

    fun setup() {
        service.addCallback(this)
        update()
    }

    override fun update() {
        dataset.value = service.medias.toMutableList()
    }

    override fun updateProgress() {
        progress.value = PlaybackProgress(service.time, service.length)
    }

    fun filter(query: CharSequence?) = launch(UI, CoroutineStart.UNDISPATCHED) { filter.filter(query) }

    public override fun onCleared() {
        service.removeCallback(this)
    }

    fun getItemPosition(position: Int, media: MediaWrapper): Int {
        val list = dataset.value
        if (list[position] == media) return position
        else for ((index, item) in list.withIndex()) if (item == media) return index
        return -1
    }

    companion object {
        fun get(fragment: Fragment, service: PlaybackService) = ViewModelProviders.of(fragment, PlaylistModel.Factory(service)).get(PlaylistModel::class.java)
    }

    class Factory(val service: PlaybackService): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PlaylistModel(service) as T
        }
    }
}

data class PlaybackProgress(val time: Long, val length: Long, val timeText : String = Tools.millisToString(time), val lengthText : String  = Tools.millisToString(length))