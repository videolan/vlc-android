package org.videolan.vlc.viewmodels.paged

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Album
import org.videolan.medialibrary.media.Artist
import org.videolan.medialibrary.media.Genre
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.util.Settings


class PagedAlbumsModel(context: Context, val parent: MediaLibraryItem? = null) : MLPagedModel<Album>(context), Medialibrary.AlbumsCb by EmptyMLCallbacks {

    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"
    override fun canSortByDuration() = true
    override fun canSortByReleaseDate() = true

    init {
        sort = Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", false)
        if (sort == Medialibrary.SORT_ALPHA && parent is Artist) sort = Medialibrary.SORT_RELEASEDATE
    }

    override fun onAlbumsAdded() {
        refresh()
    }

    override fun getAll() : Array<Album> = when (parent) {
        is Artist -> parent.getAlbums(sort, desc)
        is Genre -> parent.getAlbums(sort, desc)
        else -> medialibrary.getAlbums(sort, desc)
    }

    override fun getPage(loadSize: Int, startposition: Int) : Array<Album> = if (filterQuery == null) when(parent) {
        is Artist -> parent.getPagedAlbums(sort, desc, loadSize, startposition)
        is Genre -> parent.getPagedAlbums(sort, desc, loadSize, startposition)
        else -> medialibrary.getPagedAlbums(sort, desc, loadSize, startposition)
    } else when(parent) {
        is Artist -> parent.searchAlbums(filterQuery, sort, desc, loadSize, startposition)
        is Genre -> parent.searchAlbums(filterQuery, sort, desc, loadSize, startposition)
        else -> medialibrary.searchAlbum(filterQuery, sort, desc, loadSize, startposition)
    }

    override fun getTotalCount() = if (filterQuery == null) when(parent) {
        is Artist -> parent.albumsCount
        is Genre -> parent.albumsCount
        else -> medialibrary.albumsCount
    } else when (parent) {
        is Artist -> parent.searchAlbumsCount(filterQuery)
        is Genre -> parent.searchAlbumsCount(filterQuery)
        else -> medialibrary.getAlbumsCount(filterQuery)
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.addAlbumsCb(this)
    }

    override fun onCleared() {
        medialibrary.removeAlbumsCb(this)
        super.onCleared()
    }

    class Factory(private val context: Context, val parent: MediaLibraryItem?) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedAlbumsModel(context.applicationContext, parent) as T
        }
    }
}