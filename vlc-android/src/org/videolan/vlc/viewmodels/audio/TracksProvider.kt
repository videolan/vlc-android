package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.async
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.viewmodels.MedialibraryModel


class TracksProvider: MedialibraryModel<MediaWrapper>() {

    override fun onMediaAdded(mediaList: Array<out MediaWrapper>?) {
        refresh()
    }

    override fun onMediaUpdated(mediaList: Array<out MediaWrapper>?) {
        refresh()
    }
    override suspend fun updateList() {
        dataset.value = async { medialibrary.audio.toMutableList() }.await()
    }
}