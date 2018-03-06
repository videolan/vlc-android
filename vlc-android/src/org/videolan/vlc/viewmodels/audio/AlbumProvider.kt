package org.videolan.vlc.viewmodels.audio

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.async
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Album
import org.videolan.medialibrary.media.Artist
import org.videolan.medialibrary.media.Genre
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.viewmodels.MedialibraryModel


class AlbumProvider(val parent: MediaLibraryItem? = null): MedialibraryModel<Album>(), Medialibrary.AlbumsAddedCb, Medialibrary.AlbumsModifiedCb {

    override fun onAlbumsAdded() {
        refresh()
    }

    override fun onAlbumsModified() {
        refresh()
    }

    override suspend fun updateList() {
        dataset.value = async {
            when (parent) {
                is Artist -> parent.albums.toMutableList()
                is Genre -> parent.albums.toMutableList()
                else -> medialibrary.albums.toMutableList()
            }
        }.await()
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