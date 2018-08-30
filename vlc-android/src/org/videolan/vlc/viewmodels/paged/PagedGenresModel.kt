package org.videolan.vlc.viewmodels.paged

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.content.Context
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Genre
import org.videolan.vlc.util.Settings

class PagedGenresModel(context: Context): MLPagedModel<Genre>(context) {

    init {
        sort = Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", false)
    }

    override fun getPage(loadSize: Int, startposition: Int) = if (filter == null) medialibrary.getPagedGenres(sort, desc, loadSize, startposition)
    else medialibrary.searchGenre(filter, sort, desc, loadSize, startposition)

    override fun getTotalCount() = if (filter == null) medialibrary.genresCount else medialibrary.getGenresCount(filter)

    class Factory(private val context: Context): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedGenresModel(context.applicationContext) as T
        }
    }
}