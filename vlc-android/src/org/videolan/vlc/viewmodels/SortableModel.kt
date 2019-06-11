package org.videolan.vlc.viewmodels

import android.content.Context
import org.videolan.medialibrary.interfaces.AMedialibrary
import org.videolan.vlc.util.RefreshModel
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.canSortBy

abstract class SortableModel(protected val context: Context): ScopedModel(), RefreshModel {

    protected open val sortKey : String = this.javaClass.simpleName
    var sort = AMedialibrary.SORT_DEFAULT
    var desc = false

    var filterQuery : String? = null

    open fun canSortByName() = true
    open fun canSortByFileNameName() = false
    open fun canSortByDuration() = false
    open fun canSortByInsertionDate() = false
    open fun canSortByLastModified() = false
    open fun canSortByReleaseDate() = false
    open fun canSortByFileSize() = false
    open fun canSortByArtist() = false
    open fun canSortByAlbum ()= false
    open fun canSortByPlayCount() = false

    fun getKey() = sortKey

    open fun sort(sort: Int) {
        if (canSortBy(sort)) {
            desc = when (this.sort) {
                AMedialibrary.SORT_DEFAULT -> sort == AMedialibrary.SORT_ALPHA
                sort -> !desc
                else -> false
            }
            this.sort = sort
            refresh()
            Settings.getInstance(context).edit()
                    .putInt(sortKey, sort)
                    .putBoolean("${sortKey}_desc", desc)
                    .apply()
        }
    }

    abstract fun restore()
    abstract fun filter(query: String?)
}
