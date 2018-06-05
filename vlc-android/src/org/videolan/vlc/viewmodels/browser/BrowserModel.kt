/*****************************************************************************
 * BrowserModel.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.viewmodels.browser

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.support.annotation.MainThread
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.providers.FileBrowserProvider
import org.videolan.vlc.providers.FilePickerProvider
import org.videolan.vlc.providers.NetworkProvider
import org.videolan.vlc.providers.StorageProvider
import org.videolan.vlc.viewmodels.BaseModel

const val TYPE_FILE = 0
const val TYPE_NETWORK = 1
const val TYPE_PICKER = 2
const val TYPE_STORAGE = 3

open class BrowserModel(val url: String?, type: Int, showHiddenFiles: Boolean) : BaseModel<MediaLibraryItem>() {

    protected val provider = when (type) {
        TYPE_PICKER -> FilePickerProvider(dataset, url)
        TYPE_NETWORK -> NetworkProvider(dataset, url, showHiddenFiles)
        TYPE_STORAGE -> StorageProvider(dataset, url, showHiddenFiles)
        else -> FileBrowserProvider(dataset, url, showHiddenFiles = showHiddenFiles)
    }

    public override fun fetch() {}

    override fun refresh() = provider.refresh()

    fun browserRoot() = provider.browseRoot()

    @MainThread
    override fun sort(sort: Int) {
        launch(UI, CoroutineStart.UNDISPATCHED) {
            this@BrowserModel.sort = sort
            desc = !desc
            dataset.value = withContext(CommonPool) { dataset.value.apply { sortWith(if (desc) descComp else ascComp) } }
        }
    }

    fun saveList(media: MediaWrapper) = provider.saveList(media)

    fun isFolderEmpty(mw: MediaWrapper) = provider.isFolderEmpty(mw)

    fun getDescriptionUpdate() = provider.descriptionUpdate

    fun stop() = provider.stop()

    override fun onCleared() {
        provider.release()
    }

    class Factory(val url: String?, private val type: Int,  private val showHiddenFiles: Boolean): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BrowserModel(url, type, showHiddenFiles) as T
        }
    }
}

private val ascComp by lazy {
    Comparator<MediaLibraryItem> { item1, item2 ->
        if (item1?.itemType == MediaLibraryItem.TYPE_MEDIA) {
            val type1 = (item1 as MediaWrapper).type
            val type2 = (item2 as MediaWrapper).type
            if (type1 == MediaWrapper.TYPE_DIR && type2 != MediaWrapper.TYPE_DIR) return@Comparator -1
            else if (type1 != MediaWrapper.TYPE_DIR && type2 == MediaWrapper.TYPE_DIR) return@Comparator 1
        }
        item1?.title?.toLowerCase()?.compareTo(item2?.title?.toLowerCase() ?: "") ?: -1
    }
}
private val descComp by lazy {
    Comparator<MediaLibraryItem> { item1, item2 ->
        if (item1?.itemType == MediaLibraryItem.TYPE_MEDIA) {
            val type1 = (item1 as MediaWrapper).type
            val type2 = (item2 as MediaWrapper).type
            if (type1 == MediaWrapper.TYPE_DIR && type2 != MediaWrapper.TYPE_DIR) return@Comparator -1
            else if (type1 != MediaWrapper.TYPE_DIR && type2 == MediaWrapper.TYPE_DIR) return@Comparator 1
        }
        item2?.title?.toLowerCase()?.compareTo(item1?.title?.toLowerCase() ?: "") ?: -1
    }
}