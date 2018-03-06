package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.async
import org.videolan.medialibrary.media.Genre
import org.videolan.vlc.viewmodels.MedialibraryModel


class Genresprovider: MedialibraryModel<Genre>() {

    override suspend fun updateList() {
        dataset.value = async { medialibrary.genres.toMutableList() }.await()
    }
}