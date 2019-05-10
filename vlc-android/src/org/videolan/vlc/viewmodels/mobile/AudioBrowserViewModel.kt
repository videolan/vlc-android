package org.videolan.vlc.viewmodels.mobile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.videolan.medialibrary.Medialibrary
import org.videolan.vlc.providers.medialibrary.AlbumsProvider
import org.videolan.vlc.providers.medialibrary.ArtistsProvider
import org.videolan.vlc.providers.medialibrary.GenresProvider
import org.videolan.vlc.providers.medialibrary.TracksProvider
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.util.KEY_ARTISTS_SHOW_ALL
import org.videolan.vlc.util.Settings


@ExperimentalCoroutinesApi
class AudioBrowserViewModel(context: Context) : BaseAudioViewModel(context),
        Medialibrary.MediaCb,
        Medialibrary.ArtistsCb by EmptyMLCallbacks,
        Medialibrary.AlbumsCb by EmptyMLCallbacks,
        Medialibrary.GenresCb by EmptyMLCallbacks {

    val artistsProvider = ArtistsProvider(context, this, true)
    val albumsProvider = AlbumsProvider(null, context, this)
    val tracksProvider = TracksProvider(null, context, this)
    val genresProvider = GenresProvider(context, this)
    override val providers = arrayOf(artistsProvider, albumsProvider, tracksProvider, genresProvider)

    var showResumeCard = Settings.getInstance(context).getBoolean("audio_resume_card", true)

    init {
        medialibrary.addArtistsCb(this)
        medialibrary.addAlbumsCb(this)
        medialibrary.addGenreCb(this)
        medialibrary.addMediaCb(this)
        if (medialibrary.isStarted) refresh()
    }

    override fun onCleared() {
        medialibrary.removeArtistsCb(this)
        medialibrary.removeAlbumsCb(this)
        medialibrary.removeGenreCb(this)
        medialibrary.removeMediaCb(this)
        super.onCleared()
    }

    override fun refresh() {
        artistsProvider.showAll = Settings.getInstance(context).getBoolean(KEY_ARTISTS_SHOW_ALL, false)
        super.refresh()
    }

    override fun onMediaAdded() { refresh() }
    override fun onMediaModified() { refresh() }
    override fun onMediaDeleted() { refresh() }

    override fun onArtistsAdded() { refresh() }
    override fun onArtistsDeleted() { refresh() }
    override fun onArtistsModified() { refresh() }

    override fun onAlbumsAdded() { refresh() }
    override fun onAlbumsDeleted() { refresh() }
    override fun onAlbumsModified() { refresh() }

    override fun onGenresAdded() { refresh() }
    override fun onGenresModified() { refresh() }
    override fun onGenresDeleted() { refresh() }

    class Factory(val context: Context): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AudioBrowserViewModel(context.applicationContext) as T
        }
    }
}