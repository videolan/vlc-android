package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.MediaLibraryItem


class Genresprovider: AudioModel() {

    @Suppress("UNCHECKED_CAST")
    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) {
            medialibrary.getGenres(sort, desc).toMutableList() as MutableList<MediaLibraryItem>
        }
    }
}