package org.videolan.vlc.viewmodels.paged

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.*
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.util.Settings

class PagedTracksModel(context: Context, val parent: MediaLibraryItem? = null): MLPagedModel<MediaWrapper>(context),
        Medialibrary.MediaCb,
        Medialibrary.ArtistsCb by EmptyMLCallbacks,
        Medialibrary.AlbumsCb by EmptyMLCallbacks,
        Medialibrary.GenresCb by EmptyMLCallbacks,
        Medialibrary.PlaylistsCb by EmptyMLCallbacks {

    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"
    override fun canSortByDuration() = true
    override fun canSortByAlbum() = parent !== null
    override fun canSortByLastModified() = true

    init {
        sort = Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", false)
        if (sort == Medialibrary.SORT_ALPHA) sort = when (parent) {
            is Artist -> Medialibrary.SORT_ALBUM
            is Album -> Medialibrary.SORT_DEFAULT
            else -> Medialibrary.SORT_ALPHA
        }
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        launch(Dispatchers.Main) {
            when (parent) {
                is Artist -> medialibrary.addArtistsCb(this@PagedTracksModel)
                is Album -> medialibrary.addAlbumsCb(this@PagedTracksModel)
                is Genre -> medialibrary.addGenreCb(this@PagedTracksModel)
                is Playlist -> medialibrary.addPlaylistCb(this@PagedTracksModel)
                else -> medialibrary.addMediaCb(this@PagedTracksModel)
            }
        }
    }

    override fun onCleared() {
        when (parent) {
            is Artist -> medialibrary.removeArtistsCb(this)
            is Album -> medialibrary.removeAlbumsCb(this)
            is Genre -> medialibrary.removeGenreCb(this)
            is Playlist -> medialibrary.removePlaylistCb(this)
            else -> medialibrary.removeMediaCb(this)
        }
        super.onCleared()
    }

    override fun getAll(): Array<MediaWrapper> = parent?.tracks ?: medialibrary.getAudio(sort, desc)

    override fun getPage(loadSize: Int, startposition: Int) : Array<MediaWrapper> = if (filterQuery == null) when(parent) {
        is Artist -> parent.getPagedTracks(sort, desc, loadSize, startposition)
        is Album -> parent.getPagedTracks(sort, desc, loadSize, startposition)
        is Genre -> parent.getPagedTracks(sort, desc, loadSize, startposition)
        is Playlist -> parent.getPagedTracks(loadSize, startposition)
        else -> medialibrary.getPagedAudio(sort, desc, loadSize, startposition)
    } else when(parent) {
        is Artist -> parent.searchTracks(filterQuery, sort, desc, loadSize, startposition)
        is Album -> parent.searchTracks(filterQuery, sort, desc, loadSize, startposition)
        is Genre -> parent.searchTracks(filterQuery, sort, desc, loadSize, startposition)
        is Playlist -> parent.searchTracks(filterQuery, sort, desc, loadSize, startposition)
        else -> medialibrary.searchAudio(filterQuery, sort, desc, loadSize, startposition)
    }

    override fun getTotalCount() = if (filterQuery == null) when (parent) {
        is Album -> parent.realTracksCount
        is Playlist -> parent.realTracksCount
        is Artist,
        is Genre -> parent.tracksCount
        else -> medialibrary.audioCount
    } else when(parent) {
        is Artist -> parent.searchTracksCount(filterQuery)
        is Album -> parent.searchTracksCount(filterQuery)
        is Genre -> parent.searchTracksCount(filterQuery)
        is Playlist -> parent.searchTracksCount(filterQuery)
        else ->medialibrary.getAudioCount(filterQuery)
    }

    class Factory(private val context: Context, private val parent: MediaLibraryItem?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedTracksModel(context.applicationContext, parent) as T
        }
    }

    override fun onMediaAdded() {
        refresh()
    }

    override fun onMediaModified() {
        refresh()
    }

    override fun onMediaDeleted() {
        refresh()
    }

    override fun onArtistsModified() {
        refresh()
    }

    override fun onAlbumsModified() {
        refresh()
    }

    override fun onGenresModified() {
        refresh()
    }

    override fun onPlaylistsModified() {
        refresh()
        if ((parent as Playlist).realTracksCount == 0) parent.delete()
    }
}
