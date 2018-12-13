package org.videolan.vlc.viewmodels.paged

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.media.Folder
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.getAll

class PagedFoldersModel(context: Context, val type: Int) : MLPagedModel<Folder>(context) {
    override fun getAll() = emptyArray<Folder>()

    override fun getTotalCount() = medialibrary.getFoldersCount(type)

    override fun getPage(loadSize: Int, startposition: Int) : Array<Folder> = medialibrary.getFolders(type, sort, desc, loadSize, startposition)

    suspend fun play(position: Int) {
        val list = withContext(Dispatchers.IO) { pagedList.value?.get(position)?.getAll()}
        list?.let { MediaUtils.openList(context, it, 0) }
    }

    suspend fun append(position: Int) {
        val list = withContext(Dispatchers.IO) { pagedList.value?.get(position)?.getAll()}
        list?.let { MediaUtils.appendMedia(context, it) }
    }

    fun playSelection(selection: List<Folder>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.openList(context, list, 0)
    }

    fun appendSelection(selection: List<Folder>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.appendMedia(context, list)
    }

    class Factory(private val context: Context, val type: Int): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedFoldersModel(context.applicationContext, type) as T
        }
    }
}