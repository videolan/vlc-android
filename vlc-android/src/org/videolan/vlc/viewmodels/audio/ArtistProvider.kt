package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.Medialibrary.ArtistsAddedCb
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.Constants
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.util.ModelsHelper

class ArtistProvider: AudioModel(), ArtistsAddedCb, Medialibrary.ArtistsModifiedCb by EmptyMLCallbacks {
    override fun onArtistsAdded() {
        refresh()
    }

    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) { ModelsHelper.generateSections(sort, medialibrary.getArtists(VLCApplication.getSettings().getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false), sort, desc)) }
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.setArtistsAddedCb(this)
        medialibrary.setArtistsModifiedCb(this)
    }

    override fun onCleared() {
        super.onCleared()
        medialibrary.setArtistsAddedCb(null)
        medialibrary.setArtistsModifiedCb(null)
    }
}