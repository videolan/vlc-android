package org.videolan.vlc.viewmodels.audio

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary.ArtistsAddedCb
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.Constants
import org.videolan.vlc.util.ModelsHelper

class ArtistProvider(private val sections: Boolean = true): AudioModel(), ArtistsAddedCb {

    override fun onArtistsAdded() {
        refresh()
    }

    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) {
            val list = medialibrary.getArtists(VLCApplication.getSettings().getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false), sort, desc)
            (if (sections) ModelsHelper.generateSections(sort, list) else list.toList()).toMutableList()
        }
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.setArtistsAddedCb(this)
    }

    override fun onCleared() {
        super.onCleared()
        medialibrary.setArtistsAddedCb(null)
    }

    class Factory(private val sections: Boolean): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ArtistProvider(sections) as T
        }
    }
}