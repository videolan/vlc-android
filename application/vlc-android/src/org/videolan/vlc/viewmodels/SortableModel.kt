package org.videolan.vlc.viewmodels

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.tools.Settings
import org.videolan.vlc.util.RefreshModel
import org.videolan.vlc.util.SortModule

abstract class SortableModel(protected val context: Context): ViewModel(), RefreshModel,
    SortModule
{
    open val settings = Settings.getInstance(context)
    protected open val sortKey : String = this.javaClass.simpleName
    var sort = settings.getInt(sortKey, Medialibrary.SORT_DEFAULT)
    var desc = settings.getBoolean("${sortKey}_desc", false)

    var filterQuery : String? = null

    fun getKey() = sortKey

    override fun sort(sort: Int) {
        if (canSortBy(sort)) {
            desc = when (this.sort) {
                Medialibrary.SORT_DEFAULT -> sort == Medialibrary.SORT_ALPHA
                sort -> !desc
                else -> false
            }
            this.sort = sort
            refresh()
            settings.edit {
                putInt(sortKey, sort)
                putBoolean("${sortKey}_desc", desc)
            }
        }
    }

    abstract fun restore()
    abstract fun filter(query: String?)
}
