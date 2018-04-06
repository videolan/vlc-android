package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.VLCApplication


class Genresprovider: AudioModel() {

    init {
        sort = VLCApplication.getSettings().getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = VLCApplication.getSettings().getBoolean("${sortKey}_desc", false)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) {
            medialibrary.getGenres(sort, desc).toMutableList() as MutableList<MediaLibraryItem>
        }
    }
}