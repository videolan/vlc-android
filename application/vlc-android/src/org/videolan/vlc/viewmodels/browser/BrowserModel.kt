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

import android.content.Context
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.Settings
import org.videolan.vlc.providers.*
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.BaseModel
import org.videolan.vlc.viewmodels.tv.TvBrowserModel

const val TYPE_FILE = 0L
const val TYPE_NETWORK = 1L
const val TYPE_PICKER = 2L
const val TYPE_STORAGE = 3L

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
open class BrowserModel(
        context: Context,
        val url: String?,
        val type: Long,
        showHiddenFiles: Boolean,
        private val showDummyCategory: Boolean,
        coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()
) : BaseModel<MediaLibraryItem>(
        context, coroutineContextProvider),
        TvBrowserModel<MediaLibraryItem>,
        IPathOperationDelegate by PathOperationDelegate()
{
    override var currentItem: MediaLibraryItem? = null
    override var nbColumns: Int = 0

    private val tv = Settings.showTvUi

    override val provider: BrowserProvider = when (type) {
        TYPE_PICKER -> FilePickerProvider(context, dataset, url).also { if (tv) it.desc = desc }
        TYPE_NETWORK -> NetworkProvider(context, dataset, url, showHiddenFiles).also { if (tv) it.desc = desc }
        TYPE_STORAGE -> StorageProvider(context, dataset, url, showHiddenFiles)
        else -> FileBrowserProvider(context, dataset, url, showHiddenFiles = showHiddenFiles, showDummyCategory = showDummyCategory).also { if (tv) it.desc = desc }
    }

    override val loading = provider.loading

    override fun refresh() = provider.refresh()

    fun browseRoot() = provider.browseRoot()

    @MainThread
    override fun sort(sort: Int) {
        viewModelScope.launch {
            this@BrowserModel.sort = sort
            desc = !desc
            if (tv) provider.desc = desc
            val comp = if (tv) {
                if (desc) tvDescComp else tvAscComp
            } else {
                if (desc) descComp else ascComp
            }
            dataset.value = withContext(coroutineContextProvider.Default) { dataset.value.apply { sortWith(comp) }.also { provider.computeHeaders(dataset.value) } }
        }
    }

    fun saveList(media: MediaWrapper) = provider.saveList(media)

    fun isFolderEmpty(mw: MediaWrapper) = provider.isFolderEmpty(mw)

    fun getDescriptionUpdate() = provider.descriptionUpdate

    fun stop() = provider.stop()

    override fun onCleared() {
        provider.release()
        super.onCleared()
    }

    fun updateShowAllFiles(value: Boolean) {
        provider.updateShowAllFiles(value)
    }

    fun updateShowHiddenFiles(value: Boolean) {
        provider.updateShowHiddenFiles(value)
    }

    fun addCustomDirectory(path: String) = DirectoryRepository.getInstance(context).addCustomDirectory(path)

    fun deleteCustomDirectory(path: String) = DirectoryRepository.getInstance(context).deleteCustomDirectory(path)

    suspend fun customDirectoryExists(path: String) = DirectoryRepository.getInstance(context).customDirectoryExists(path)

    class Factory(val context: Context, val url: String?, private val type: Long, private val showHiddenFiles: Boolean, private val showDummyCategory: Boolean = true) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BrowserModel(context.applicationContext, url, type, showHiddenFiles, showDummyCategory = showDummyCategory) as T
        }
    }

    override fun canSortByFileNameName(): Boolean = true
}

@ExperimentalCoroutinesApi
fun Fragment.getBrowserModel(category: Long, url: String?, showHiddenFiles: Boolean, showDummyCategory: Boolean = false) = if (category == TYPE_NETWORK)
    ViewModelProvider(this, NetworkModel.Factory(requireContext(), url, showHiddenFiles)).get(NetworkModel::class.java)
else
    ViewModelProvider(this, BrowserModel.Factory(requireContext(), url, category, showHiddenFiles, showDummyCategory = showDummyCategory)).get(BrowserModel::class.java)
