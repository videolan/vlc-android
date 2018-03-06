package org.videolan.vlc.viewmodels.audio

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.async
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.*
import org.videolan.vlc.util.ModelsHelper

class TracksProvider(val parent: MediaLibraryItem? = null, private val separators: Boolean = true): AudioModel() {

    override fun canSortByDuration() = true
    override fun canSortByAlbum() = parent !== null

    init {
        sort = when (parent) {
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
    override suspend fun updateList() {
        dataset.value = async {
            val list = when (parent) {
                is Artist -> parent.getTracks(sort, desc)
                is Album -> parent.getTracks(sort, desc)
                is Genre -> parent.getTracks(sort, desc)
                is Playlist -> parent.getTracks()
                else -> medialibrary.getAudio(sort, desc)
            }
            @Suppress("UNCHECKED_CAST")
            if (separators) ModelsHelper.generateSections(sort, list)
            else list.toMutableList() as MutableList<MediaLibraryItem>
        }.await()
    }

    class Factory(val parent: MediaLibraryItem?, private val separators: Boolean): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TracksProvider(parent, separators) as T
        }
    }
}