package org.videolan.vlc.viewmodels.tv

import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.providers.HeaderProvider

interface TvBrowserModel {
    fun isEmpty() : Boolean
    var currentItem: MediaLibraryItem?
    var nbColumns: Int
    val provider: HeaderProvider
}