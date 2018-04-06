package org.videolan.vlc.viewmodels

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaWrapper

class HistoryProvider: BaseModel<MediaWrapper>() {

    override fun canSortByName() = false

    override fun fetch() {
        refresh()
    }

    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) { Medialibrary.getInstance().lastMediaPlayed().toMutableList() }
    }

    fun moveUp(media: MediaWrapper) {
        dataset.value = dataset.value.apply {
            remove(media)
            add(0, media)
        }
    }

    fun clear() {
        dataset.value = mutableListOf()
    }
}