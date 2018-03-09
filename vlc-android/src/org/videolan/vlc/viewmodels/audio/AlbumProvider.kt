package org.videolan.vlc.viewmodels.audio

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Artist
import org.videolan.medialibrary.media.Genre
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.ModelsHelper


class AlbumProvider(val parent: MediaLibraryItem? = null): AudioModel(), Medialibrary.AlbumsAddedCb, Medialibrary.AlbumsModifiedCb {

    override fun canSortByDuration() = true
    override fun canSortByReleaseDate() = true

    init {
        if (parent is Artist) sort = Medialibrary.SORT_RELEASEDATE
    }

    override fun onAlbumsAdded() {
        refresh()
    }

    override fun onAlbumsModified() {
        refresh()
    }

    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) {
            ModelsHelper.generateSections(sort, when (parent) {
                is Artist -> parent.getAlbums(sort, desc)
                is Genre -> parent.getAlbums(sort, desc)
                else -> medialibrary.getAlbums(sort, desc)
            })
        }
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.setAlbumsAddedCb(this)
        medialibrary.setAlbumsModifiedCb(this)
    }

    override fun onCleared() {
        super.onCleared()
        medialibrary.setAlbumsAddedCb(null)
        medialibrary.setAlbumsModifiedCb(null)
    }

    class Factory(val parent: MediaLibraryItem?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AlbumProvider(parent) as T
        }
    }
}