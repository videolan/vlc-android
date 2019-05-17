package org.videolan.vlc.viewmodels.tv

import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.providers.HeaderProvider

interface TvBrowserModel {
    fun canSortByFileNameName(): Boolean
    fun canSortByDuration(): Boolean
    fun canSortByInsertionDate(): Boolean
    fun canSortByReleaseDate(): Boolean
    fun canSortByLastModified(): Boolean
    fun sort(sort: Int)

    var currentItem: MediaLibraryItem?
    var nbColumns: Int
    val provider: HeaderProvider


}