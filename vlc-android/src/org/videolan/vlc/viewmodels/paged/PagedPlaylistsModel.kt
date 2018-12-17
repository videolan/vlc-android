package org.videolan.vlc.viewmodels.paged

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Playlist

class PagedPlaylistsModel(context: Context): MLPagedModel<Playlist>(context), Medialibrary.PlaylistsCb {

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.addPlaylistCb(this)
    }

    override fun onCleared() {
        medialibrary.removePlaylistCb(this)
        super.onCleared()
    }

    override fun canSortByDuration() = true

    override fun getAll() : Array<Playlist> = medialibrary.getPlaylists(sort, desc)

    override fun getPage(loadSize: Int, startposition: Int)  : Array<Playlist> {
        return if (filterQuery == null) medialibrary.getPagedPlaylists(sort, desc, loadSize, startposition)
        else medialibrary.searchPlaylist(filterQuery, sort, desc, loadSize, startposition)
    }

    override fun getTotalCount() = if (filterQuery == null) medialibrary.playlistsCount else medialibrary.getPlaylistsCount(filterQuery)

    override fun onPlaylistsAdded() {
        refresh()
    }

    override fun onPlaylistsModified() {
        refresh()
    }

    override fun onPlaylistsDeleted() {
        refresh()
    }

    class Factory(private val context: Context): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedPlaylistsModel(context.applicationContext) as T
        }
    }
}