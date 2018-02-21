package org.videolan.vlc.viewmodels

import android.arch.lifecycle.ViewModel
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.interfaces.MediaAddedCb
import org.videolan.medialibrary.interfaces.MediaUpdatedCb
import org.videolan.medialibrary.media.MediaWrapper


abstract class MedialibraryModel : ViewModel(), Medialibrary.OnMedialibraryReadyListener, MediaUpdatedCb, MediaAddedCb {

//    val refreshing = MutableLiveData<Boolean>()
    val medialibrary = Medialibrary.getInstance()!!

    abstract fun refresh()

    protected fun fetch() {
        medialibrary.addOnMedialibraryReadyListener(this)
        if (medialibrary.isStarted) onMedialibraryReady()
    }

    override fun onMedialibraryReady() {
        launch(UI) { refresh() }
    }

    override fun onMedialibraryIdle() {
        launch(UI) { refresh() }
    }

    override fun onMediaUpdated(mediaList: Array<out MediaWrapper>?) {}

    override fun onMediaAdded(mediaList: Array<out MediaWrapper>?) {}

    override fun onCleared() {
        super.onCleared()
        medialibrary.removeOnMedialibraryReadyListener(this)
    }
}