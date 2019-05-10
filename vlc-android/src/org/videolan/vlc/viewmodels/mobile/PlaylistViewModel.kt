package org.videolan.vlc.viewmodels.mobile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Album
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Playlist
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.providers.medialibrary.TracksProvider
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.viewmodels.MedialibraryViewModel


@ExperimentalCoroutinesApi
class PlaylistViewModel(context: Context, val playlist: MediaLibraryItem) : MedialibraryViewModel(context),
        Medialibrary.MediaCb by EmptyMLCallbacks,
        Medialibrary.AlbumsCb by EmptyMLCallbacks,
        Medialibrary.PlaylistsCb by EmptyMLCallbacks {
    val tracksProvider = TracksProvider(playlist, context, this)
    override val providers : Array<MedialibraryProvider<out MediaLibraryItem>> = arrayOf(tracksProvider)

    init {
        when (playlist) {
            is Playlist -> medialibrary.addPlaylistCb(this@PlaylistViewModel)
            is Album -> medialibrary.addAlbumsCb(this@PlaylistViewModel)
            else -> medialibrary.addMediaCb(this@PlaylistViewModel)
        }
        if (medialibrary.isStarted) refresh()
    }

    override fun onMediaAdded() { refresh() }

    override fun onMediaDeleted() { refresh() }

    override fun onPlaylistsDeleted() { refresh() }

    override fun onPlaylistsModified() { refresh() }

    override fun onAlbumsDeleted() { refresh() }

    override fun onAlbumsModified() { refresh() }

    override fun onCleared() {
        when (playlist) {
            is Playlist -> medialibrary.removePlaylistCb(this)
            is Album -> medialibrary.removeAlbumsCb(this)
            else -> medialibrary.removeMediaCb(this)
        }
        super.onCleared()
    }

    class Factory(val context: Context, val playlist: MediaLibraryItem): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PlaylistViewModel(context.applicationContext, playlist) as T
        }
    }
}