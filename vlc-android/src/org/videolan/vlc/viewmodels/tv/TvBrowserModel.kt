package org.videolan.vlc.viewmodels.tv

import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.providers.HeaderProvider

interface TvBrowserModel {

    var currentItem: MediaLibraryItem?
    var nbColumns: Int
    val provider: HeaderProvider
}