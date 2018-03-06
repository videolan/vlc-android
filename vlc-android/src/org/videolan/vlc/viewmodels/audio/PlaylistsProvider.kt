package org.videolan.vlc.viewmodels.audio

import kotlinx.coroutines.experimental.async
import org.videolan.medialibrary.media.Playlist
import org.videolan.vlc.viewmodels.MedialibraryModel


class PlaylistsProvider: MedialibraryModel<Playlist>() {

    override suspend fun updateList() {
        dataset.value = async { medialibrary.playlists.toMutableList() }.await()
    }
}