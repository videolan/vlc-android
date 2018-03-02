package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.async
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.Medialibrary.ArtistsAddedCb
import org.videolan.medialibrary.media.Artist
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.Constants
import org.videolan.vlc.viewmodels.MedialibraryModel

class ArtistProvider: MedialibraryModel<Artist>(), ArtistsAddedCb, Medialibrary.ArtistsModifiedCb {
    override fun onArtistsModified() {
        refresh()
    }

    override fun onArtistsAdded() {
        refresh()
    }

    override suspend fun updateList() {
        dataset.value = async { medialibrary.getArtists(VLCApplication.getSettings().getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false)).toMutableList() }.await()
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