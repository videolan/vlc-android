package org.videolan.vlc.viewmodels.mobile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Album
import org.videolan.medialibrary.media.Artist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.providers.medialibrary.AlbumsProvider
import org.videolan.vlc.providers.medialibrary.TracksProvider
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.viewmodels.MedialibraryViewModel

@ExperimentalCoroutinesApi
class AlbumSongsViewModel(context: Context, val parent: MediaLibraryItem) : MedialibraryViewModel(context),
        Medialibrary.MediaCb,
        Medialibrary.ArtistsCb by EmptyMLCallbacks,
        Medialibrary.AlbumsCb by EmptyMLCallbacks {

    val albumsProvider = AlbumsProvider(parent, context, this)
    val tracksProvider = TracksProvider(parent, context, this)
    override val providers = arrayOf(albumsProvider, tracksProvider)

    init {
        when (parent) {
            is Artist -> medialibrary.addArtistsCb(this@AlbumSongsViewModel)
            is Album -> medialibrary.addAlbumsCb(this@AlbumSongsViewModel)
            else -> medialibrary.addMediaCb(this@AlbumSongsViewModel)
        }
        if (medialibrary.isStarted) refresh()
    }

    override fun onMediaAdded() { refresh() }

    override fun onMediaModified() { refresh() }

    override fun onMediaDeleted() { refresh() }

    override fun onArtistsModified() { refresh() }

    override fun onAlbumsModified() { refresh() }

    override fun onCleared() {
        when (parent) {
            is Artist -> medialibrary.removeArtistsCb(this)
            is Album -> medialibrary.removeAlbumsCb(this)
            else -> medialibrary.removeMediaCb(this)
        }
        super.onCleared()
    }

    class Factory(val context: Context, val parent: MediaLibraryItem): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AlbumSongsViewModel(context.applicationContext, parent) as T
        }
    }
}