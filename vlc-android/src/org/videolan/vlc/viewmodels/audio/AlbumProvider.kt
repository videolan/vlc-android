package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.async
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Album
import org.videolan.vlc.viewmodels.MedialibraryModel


class AlbumProvider: MedialibraryModel<Album>(), Medialibrary.AlbumsAddedCb, Medialibrary.AlbumsModifiedCb {
    override fun onAlbumsAdded() {
        refresh()
    }

    override fun onAlbumsModified() {
        refresh()
    }

    override suspend fun updateList() {
        dataset.value = async { medialibrary.albums.toMutableList() }.await()
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
}