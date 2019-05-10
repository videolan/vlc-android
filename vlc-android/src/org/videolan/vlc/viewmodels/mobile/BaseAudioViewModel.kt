package org.videolan.vlc.viewmodels.mobile

import android.content.Context
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.viewmodels.SortableModel


abstract class BaseAudioViewModel(context: Context) : SortableModel(context),
        Medialibrary.OnMedialibraryReadyListener, Medialibrary.OnDeviceChangeListener  {

    val medialibrary = Medialibrary.getInstance().apply {
            addOnMedialibraryReadyListener(this@BaseAudioViewModel)
            addOnDeviceChangeListener(this@BaseAudioViewModel)
        }
    abstract val providers : Array<MedialibraryProvider<out MediaLibraryItem>>

    override fun refresh() = providers.forEach { it.refresh() }

    override fun restore() {
        if (filterQuery !== null) filter(null)
    }

    override fun filter(query: String?) {
        filterQuery = query
        refresh()
    }

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
