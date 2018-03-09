package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.vlc.util.ModelsHelper


class PlaylistsProvider: AudioModel() {

    override fun canSortByDuration() = true

    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) { ModelsHelper.generateSections(sort, medialibrary.getPlaylists(sort, desc)) }
    }
}