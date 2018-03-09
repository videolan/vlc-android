package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.vlc.util.ModelsHelper


class Genresprovider: AudioModel() {

    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) { ModelsHelper.generateSections(sort, medialibrary.getGenres(sort, desc)) }
    }
}