package org.videolan.vlc.viewmodels.paged

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Genre
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.util.Settings

class PagedGenresModel(context: Context): MLPagedModel<Genre>(context), Medialibrary.GenresCb by EmptyMLCallbacks {

    init {
        sort = Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", false)
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.addGenreCb(this)
    }

    override fun onCleared() {
        medialibrary.removeGenreCb(this)
        super.onCleared()
    }

    override fun getAll() : Array<Genre> = medialibrary.getGenres(sort, desc)

    override fun getPage(loadSize: Int, startposition: Int) : Array<Genre> {
        return if (filterQuery == null) medialibrary.getPagedGenres(sort, desc, loadSize, startposition)
        else medialibrary.searchGenre(filterQuery, sort, desc, loadSize, startposition)
    }

    override fun getTotalCount() = if (filterQuery == null) medialibrary.genresCount else medialibrary.getGenresCount(filterQuery)

    override fun onGenresAdded() {
        refresh()
    }

    override fun onGenresDeleted() {
        refresh()
    }

    class Factory(private val context: Context): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedGenresModel(context.applicationContext) as T
        }
    }
}