package org.videolan.vlc.viewmodels

import android.content.Context
import android.view.Menu
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.viewmodels.mobile.VideosViewModel


abstract class MedialibraryViewModel(context: Context) : SortableModel(context),
        ICallBackHandler by CallBackDelegate(), IDisplaySettingsCallBackHandler by DisplaySettingsCallBackDelegate()  {

    init {
        @Suppress("LeakingThis")
        viewModelScope.registerCallBacks { refresh() }
        viewModelScope.registerDisplaySettingsCallBacks(
            refresh = {
                refresh()
            },
            changeGrouping = {
                if (this is VideosViewModel) changeGroupingType(it)
            },
            getAllProviders = {
                providers
            })
    }

    abstract val providers : Array<MedialibraryProvider<out MediaLibraryItem>>

    override fun refresh() = providers.forEach { it.refresh() }

    fun isEmpty() = providers.all { it.isEmpty() }

    fun isEmptyAt(index:Int) = providers[index].isEmpty()

    override fun restore() {
        if (filterQuery !== null) filter(null)
    }

    override fun filter(query: String?) {
        filterQuery = query
        refresh()
    }

    override fun sort(sort: Int) { providers.forEach { it.sort(sort) } }

    fun isFiltering() = filterQuery != null

    override fun onCleared() {
        releaseCallbacks()
        releaseDisplaySettingsCallbacks()
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
    override fun canSortByMediaNumber() = providers.any { it.canSortByMediaNumber() }


    suspend fun changeFavorite(tracks: List<MediaLibraryItem>, favorite: Boolean) = withContext(Dispatchers.IO) {
        tracks.forEach {
            it.isFavorite = favorite
        }
    }
}

fun MedialibraryViewModel.prepareOptionsMenu(menu: Menu) {
    menu.findItem(R.id.ml_menu_sortby).isVisible = canSortByName()
    menu.findItem(R.id.ml_menu_sortby_filename).isVisible = canSortByFileNameName()
    menu.findItem(R.id.ml_menu_sortby_artist_name).isVisible = canSortByArtist()
    menu.findItem(R.id.ml_menu_sortby_album_name).isVisible = canSortByAlbum()
    menu.findItem(R.id.ml_menu_sortby_length).isVisible = canSortByDuration()
    menu.findItem(R.id.ml_menu_sortby_date).isVisible = canSortByReleaseDate()
    menu.findItem(R.id.ml_menu_sortby_last_modified).isVisible = canSortByLastModified()
    menu.findItem(R.id.ml_menu_sortby_media_number).isVisible = canSortByMediaNumber()
    menu.findItem(R.id.ml_menu_sortby_number).isVisible = false
}

fun MedialibraryViewModel.sortMenuTitles(menu: Menu, index : Int) {
    UiTools.updateSortTitles(menu, providers[index])
}
