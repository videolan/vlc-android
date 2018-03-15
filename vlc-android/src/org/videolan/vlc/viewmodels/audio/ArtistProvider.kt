package org.videolan.vlc.viewmodels.audio

import android.util.Log
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary.ArtistsAddedCb
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.Constants
import org.videolan.vlc.util.ModelsHelper

class ArtistProvider: AudioModel(), ArtistsAddedCb {

    override fun onArtistsAdded() {
        refresh()
    }

    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) { ModelsHelper.generateSections(sort, medialibrary.getArtists(VLCApplication.getSettings().getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false), sort, desc)) }
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.setArtistsAddedCb(this)
    }

    override fun onCleared() {
        super.onCleared()
        medialibrary.setArtistsAddedCb(null)
    }
}