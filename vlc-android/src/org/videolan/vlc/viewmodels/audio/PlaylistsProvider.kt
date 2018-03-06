package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.async
import org.videolan.vlc.util.ModelsHelper


class PlaylistsProvider: AudioModel() {

    override fun canSortByDuration() = true

    override suspend fun updateList() {
        dataset.value = async { ModelsHelper.generateSections(sort, medialibrary.getPlaylists(sort, desc)) }.await()
    }
}