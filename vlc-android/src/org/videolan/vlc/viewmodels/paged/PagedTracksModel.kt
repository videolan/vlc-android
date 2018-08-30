package org.videolan.vlc.viewmodels.paged

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.content.Context
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.*
import org.videolan.vlc.util.Settings

class PagedTracksModel(context: Context, val parent: MediaLibraryItem? = null): MLPagedModel<MediaWrapper>(context) {

    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"
    override fun canSortByDuration() = true
    override fun canSortByAlbum() = parent !== null

    init {
        sort = Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", false)
        if (sort == Medialibrary.SORT_ALPHA) sort = when (parent) {
            is Artist -> Medialibrary.SORT_ALBUM
            is Album -> Medialibrary.SORT_DEFAULT
            else -> Medialibrary.SORT_ALPHA
        }
    }

    override fun onMediaAdded(mediaList: Array<out MediaWrapper>?) {
        refresh()
    }

    override fun onMediaUpdated(mediaList: Array<out MediaWrapper>?) {
        refresh()
    }

    override fun getPage(loadSize: Int, startposition: Int) : Array<MediaWrapper> = if (filter == null) when(parent) {
        is Artist -> parent.getPagedTracks(sort, desc, loadSize, startposition)
        is Album -> parent.getPagedTracks(sort, desc, loadSize, startposition)
        is Genre -> parent.getPagedTracks(sort, desc, loadSize, startposition)
        is Playlist -> parent.getPagedTracks(loadSize, startposition)
        else -> medialibrary.getPagedAudio(sort, desc, loadSize, startposition)
    } else when(parent) {
        is Artist -> parent.searchTracks(filter, sort, desc, loadSize, startposition)
        is Album -> parent.searchTracks(filter, sort, desc, loadSize, startposition)
        is Genre -> parent.searchTracks(filter, sort, desc, loadSize, startposition)
        is Playlist -> parent.searchTracks(filter, sort, desc, loadSize, startposition)
        else -> medialibrary.searchAudio(filter, sort, desc, loadSize, startposition)
    }

    override fun getTotalCount() = if (filter == null) parent?.tracksCount ?: medialibrary.audioCount
    else when(parent) {
        is Artist -> parent.searchTracksCount(filter)
        is Album -> parent.searchTracksCount(filter)
        is Genre -> parent.searchTracksCount(filter)
        is Playlist -> parent.searchTracksCount(filter)
        else ->medialibrary.getAudioCount(filter)
    }

    class Factory(private val context: Context, private val parent: MediaLibraryItem?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedTracksModel(context.applicationContext, parent) as T
        }
    }
}