package org.videolan.vlc.viewmodels.audio

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary.ArtistsAddedCb
import org.videolan.medialibrary.media.MediaLibraryItem

class ArtistProvider(private var showAll: Boolean = false): AudioModel(), ArtistsAddedCb {

    override fun onArtistsAdded() {
        refresh()
    }

    fun showAll(show: Boolean) {
        showAll = show
    }

    //VLCApplication.getSettings().getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false)
    @Suppress("UNCHECKED_CAST")
    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) {
            medialibrary.getArtists(showAll, sort, desc).toMutableList() as MutableList<MediaLibraryItem>
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

    class Factory(private val showAll: Boolean): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ArtistProvider(showAll) as T
        }
    }
}