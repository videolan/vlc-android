package org.videolan.vlc.viewmodels.paged

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.content.Context
import org.videolan.medialibrary.media.Playlist

class PagedPlaylistsModel(context: Context): MLPagedModel<Playlist>(context) {

    override fun canSortByDuration() = true

    override fun getPage(loadSize: Int, startposition: Int) = if (filter == null) medialibrary.getPagedPlaylists(sort, desc, loadSize, startposition)
    else medialibrary.searchPlaylist(filter, sort, desc, loadSize, startposition)

    override fun getTotalCount() = if (filter == null) medialibrary.playlistsCount else medialibrary.getPlaylistsCount(filter)

    class Factory(private val context: Context): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedPlaylistsModel(context.applicationContext) as T
        }
    }
}