package org.videolan.vlc.viewmodels

import kotlinx.coroutines.experimental.async
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaWrapper

class HistoryProvider: BaseModel<MediaWrapper>() {

    override fun fetch() {
        refresh()
    }

    override suspend fun updateList() {
        dataset.value = async { Medialibrary.getInstance().lastMediaPlayed().toMutableList() }.await()
    }

    fun moveUp(media: MediaWrapper) {
        dataset.value = dataset.value?.apply {
            remove(media)
            add(0, media)
        }
    }

    fun clear() {
        dataset.value = mutableListOf()
    }
}