package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.VLCIO


class PlaylistsProvider: AudioModel() {

    override fun canSortByDuration() = true

    @Suppress("UNCHECKED_CAST")
    override suspend fun updateList() {
        dataset.value = withContext(VLCIO) { medialibrary.getPlaylists(sort, desc).toMutableList() as MutableList<MediaLibraryItem> }
    }
}