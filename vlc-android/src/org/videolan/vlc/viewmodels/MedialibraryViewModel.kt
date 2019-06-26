package org.videolan.vlc.viewmodels

import android.content.Context
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider


abstract class MedialibraryViewModel(context: Context) : SortableModel(context),
        AbstractMedialibrary.OnMedialibraryReadyListener, AbstractMedialibrary.OnDeviceChangeListener  {

    val medialibrary = AbstractMedialibrary.getInstance().apply {
        addOnMedialibraryReadyListener(this@MedialibraryViewModel)
        addOnDeviceChangeListener(this@MedialibraryViewModel)
    }

    abstract val providers : Array<MedialibraryProvider<out MediaLibraryItem>>

    override fun refresh() = providers.forEach { it.refresh() }

    fun isEmpty() = providers.all { it.isEmpty() }

    override fun restore() {
        if (filterQuery !== null) filter(null)
    }

    override fun filter(query: String?) {
        filterQuery = query
        refresh()
    }

    override fun sort(sort: Int) { providers.forEach { it.sort(sort) } }

    fun isFiltering() = filterQuery != null

    override fun onMedialibraryReady() = refresh()

    override fun onMedialibraryIdle() = refresh()

    override fun onDeviceChange() = refresh()

    override fun onCleared() {
        medialibrary.removeOnMedialibraryReadyListener(this)
        medialibrary.removeOnDeviceChangeListener(this)
        super.onCleared()
    }

    override fun canSortByName() = providers.any { it.canSortByName() }
    override fun canSortByFileNameName() = providers.any { it.canSortByFileNameName() }
    override fun canSortByDuration() = providers.any { it.canSortByDuration() }
    override fun canSortByInsertionDate() = providers.any { it.canSortByInsertionDate() }
    override fun canSortByLastModified() = providers.any { it.canSortByLastModified() }
    override fun canSortByReleaseDate() = providers.any { it.canSortByReleaseDate() }
    override fun canSortByFileSize() = providers.any { it.canSortByFileSize() }
    override fun canSortByArtist() = providers.any { it.canSortByArtist() }
    override fun canSortByAlbum () = providers.any { it.canSortByAlbum () }
    override fun canSortByPlayCount() = providers.any { it.canSortByPlayCount() }
}
