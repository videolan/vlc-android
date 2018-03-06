package org.videolan.vlc.viewmodels.audio

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.async
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.viewmodels.MedialibraryModel

class TracksProvider(val parent: MediaLibraryItem? = null): MedialibraryModel<MediaWrapper>() {

    override fun onMediaAdded(mediaList: Array<out MediaWrapper>?) {
        refresh()
    }

    override fun onMediaUpdated(mediaList: Array<out MediaWrapper>?) {
        refresh()
    }
    override suspend fun updateList() {
        dataset.value = async {
            parent?.tracks?.toMutableList() ?: medialibrary.audio.toMutableList()
        }.await()
    }

    class Factory(val parent: MediaLibraryItem?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TracksProvider(parent) as T
        }
    }
}