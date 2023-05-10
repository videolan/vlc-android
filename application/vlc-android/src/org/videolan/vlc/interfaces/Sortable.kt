package org.videolan.vlc.interfaces

import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.viewmodels.BaseModel


interface Sortable : PopupMenu.OnMenuItemClickListener {
    fun getVM() : BaseModel<out MediaLibraryItem>

    fun sort(v: View) {
        val vm = getVM()
        val menu = PopupMenu(v.context, v)
        menu.inflate(R.menu.sort_options)
        menu.menu.findItem(R.id.ml_menu_sortby_filename).isVisible = vm.canSortByFileNameName()
        menu.menu.findItem(R.id.ml_menu_sortby_length).isVisible = vm.canSortByDuration()
        menu.menu.findItem(R.id.ml_menu_sortby_date).isVisible = vm.canSortByReleaseDate()
        menu.menu.findItem(R.id.ml_menu_sortby_last_modified).isVisible = vm.canSortByLastModified()
        menu.menu.findItem(R.id.ml_menu_sortby_number).isVisible = false
        menu.setOnMenuItemClickListener(this)
        menu.show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val vm = getVM()
        vm.sort(when (item.itemId) {
            R.id.ml_menu_sortby_name -> Medialibrary.SORT_ALPHA
            R.id.ml_menu_sortby_filename -> Medialibrary.SORT_FILENAME
            R.id.ml_menu_sortby_length -> Medialibrary.SORT_DURATION
            R.id.ml_menu_sortby_last_modified -> Medialibrary.SORT_LASTMODIFICATIONDATE
            R.id.ml_menu_sortby_date -> Medialibrary.SORT_RELEASEDATE
            else -> return false
        })
        return true
    }
}