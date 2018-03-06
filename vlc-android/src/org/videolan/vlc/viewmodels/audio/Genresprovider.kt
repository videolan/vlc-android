package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.async
import org.videolan.vlc.util.ModelsHelper


class Genresprovider: AudioModel() {

    override suspend fun updateList() {
        dataset.value = async { ModelsHelper.generateSections(sort, medialibrary.getGenres(sort, desc)) }.await()
    }
}