package org.videolan.vlc.viewmodels.audio

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.*
import org.videolan.vlc.VLCApplication

class TracksProvider(val parent: MediaLibraryItem? = null): AudioModel() {

    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"
    override fun canSortByDuration() = true
    override fun canSortByAlbum() = parent !== null

    init {
        sort = VLCApplication.getSettings().getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = VLCApplication.getSettings().getBoolean("${sortKey}_desc", false)
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

    @Suppress("UNCHECKED_CAST")
    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) { when (parent) {
            is Artist -> parent.getTracks(sort, desc)
            is Album -> parent.getTracks(sort, desc)
            is Genre -> parent.getTracks(sort, desc)
            is Playlist -> parent.getTracks()
            else -> medialibrary.getAudio(sort, desc)
        }.toMutableList() as MutableList<MediaLibraryItem>
        }
    }

    class Factory(val parent: MediaLibraryItem?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TracksProvider(parent) as T
        }
    }
}