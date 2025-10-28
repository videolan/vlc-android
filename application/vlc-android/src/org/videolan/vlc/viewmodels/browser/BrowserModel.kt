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
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.GROUP_VIDEOS_FOLDER
import org.videolan.resources.GROUP_VIDEOS_NAME
import org.videolan.resources.GROUP_VIDEOS_NONE
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.KEY_GROUP_VIDEOS
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.providers.BrowserProvider
import org.videolan.vlc.providers.FileBrowserProvider
import org.videolan.vlc.providers.FilePickerProvider
import org.videolan.vlc.providers.NetworkProvider
import org.videolan.vlc.providers.PickerType
import org.videolan.vlc.providers.StorageProvider
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.viewmodels.BaseModel
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType
import org.videolan.vlc.viewmodels.mobile.VideosViewModel
import org.videolan.vlc.viewmodels.mobile.VideosViewModel.Companion.PARENT_FOLDER_KEY
import org.videolan.vlc.viewmodels.mobile.VideosViewModel.Companion.PARENT_GROUP_KEY
import org.videolan.vlc.viewmodels.tv.TvBrowserModel

const val TYPE_FILE = 0L
const val TYPE_NETWORK = 1L
const val TYPE_PICKER = 2L
const val TYPE_STORAGE = 3L

open class BrowserModel(
        context: Context,
        val url: String?,
        val type: Long,
        private val showDummyCategory: Boolean,
        pickerType: PickerType = PickerType.SUBTITLE,
        mocked: ArrayList<MediaLibraryItem>? = null,
        coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()
) : BaseModel<MediaLibraryItem>(
        context, coroutineContextProvider),
        TvBrowserModel<MediaLibraryItem>,
        IPathOperationDelegate by PathOperationDelegate() {
    override var currentItem: MediaLibraryItem? = null
    override var nbColumns: Int = 0

    override val provider: BrowserProvider = when (type) {
        TYPE_PICKER -> FilePickerProvider(context, dataset, url, pickerType = pickerType)
        TYPE_NETWORK -> NetworkProvider(context, dataset, url, mocked)
        TYPE_STORAGE -> StorageProvider(context, dataset, url)
        else -> FileBrowserProvider(context, dataset, url, showDummyCategory = showDummyCategory, sort = sort, desc = desc)
    }

    override val loading = provider.loading

    override fun refresh() = provider.refresh()
    fun refreshMW() = provider.refresh()

    fun browseRoot() = provider.browseRoot()

    /**
     * Sorts again. Useful on resume
     *
     */
    fun reSort() {
        viewModelScope.launch {
            dataset.value = withContext(coroutineContextProvider.Default) { dataset.value.apply { provider.sort(this) }.also { provider.computeHeaders(dataset.value) } }
        }
    }

    /**
     * Resets the sorts info from the shared preferences for the model and provider
     *
     */
    fun resetSort() {
        sort = settings.getInt(sortKey, Medialibrary.SORT_DEFAULT)
        desc = settings.getBoolean("${sortKey}_desc", false)
        provider.desc = desc
        provider.sort = sort
    }

    @MainThread
    override fun sort(sort: Int) {
        viewModelScope.launch {
            this@BrowserModel.sort = sort
            desc = if (sort == Medialibrary.SORT_DEFAULT) false else !desc
            provider.sort = sort
            provider.desc = desc
            dataset.value = withContext(coroutineContextProvider.Default) { dataset.value.apply { provider.sort(this) }.also { provider.computeHeaders(dataset.value) } }
            settings.putSingle(sortKey, sort)
            settings.putSingle("${sortKey}_desc", desc)
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

    fun addCustomDirectory(path: String) = DirectoryRepository.getInstance(context).addCustomDirectory(path)

    fun deleteCustomDirectory(path: String) = DirectoryRepository.getInstance(context).deleteCustomDirectory(path)

    suspend fun customDirectoryExists(path: String) = DirectoryRepository.getInstance(context).customDirectoryExists(path)

    class Factory(val context: Context, val url: String?, private val type: Long, private val showDummyCategory: Boolean = true, private val pickerType: PickerType = PickerType.SUBTITLE) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BrowserModel(context.applicationContext, url, type, showDummyCategory = showDummyCategory, pickerType = pickerType) as T
        }
    }

    override fun canSortByFileNameName(): Boolean = true

    suspend fun toggleBanState(path: String) = withContext(Dispatchers.IO) {
        val bannedFolders = Medialibrary.getInstance().bannedFolders()
        if (MedialibraryUtils.isStrictlyBanned(path, bannedFolders.toList())) Medialibrary.getInstance().unbanFolder(path) else if (!MedialibraryUtils.isBanned(path, bannedFolders.toList())) Medialibrary.getInstance().banFolder(path)
    }

    fun refreshMedia(mw: MediaWrapper, timeChanged: Long) {
        (dataset.getList().firstOrNull { it.id == mw.id } as? MediaWrapper)?.let { media ->
            media.displayTime = timeChanged
            media.time = timeChanged
            Tools.setMediaDescription(media)
        }
    }

    suspend fun updateMediaPlayed(mw: MediaWrapper) = withContext(Dispatchers.IO) {
        if (dataset.getList().contains(mw)) {
            provider.medialibrary.getMedia(mw.id)?.let { item ->
                withContext(Dispatchers.Main) {
                    dataset.replace(item)
                }
            }
        }
    }

    fun saveSort() {
        Settings.getInstance(context).edit {
            putInt(sortKey, sort)
            putBoolean("${sortKey}_desc", desc)
        }
    }

    companion object {
        // Define a custom key for your dependency
        val URL_KEY = object : CreationExtras.Key<String?> {}
        val TYPE_KEY = object : CreationExtras.Key<Long> {}
        val SHOW_DUMMY_KEY = object : CreationExtras.Key<Boolean> {}
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // Get the dependency in your factory
                val application = checkNotNull(this[APPLICATION_KEY])
                val url = this[URL_KEY]
                val type = checkNotNull(this[TYPE_KEY])
                val showDummy = checkNotNull(this[SHOW_DUMMY_KEY])


                BrowserModel(
                    application,
                    url,
                    type,
                    showDummy
                )
            }
        }
    }
}

fun Fragment.getBrowserModel(category: Long, url: String?, showDummyCategory: Boolean = false, mocked: ArrayList<MediaLibraryItem>? = null) = if (category == TYPE_NETWORK)
    ViewModelProvider(this, NetworkModel.Factory(requireContext(), url, mocked))[NetworkModel::class.java]
else
    ViewModelProvider(this, BrowserModel.Factory(requireContext(), url, category, showDummyCategory = showDummyCategory))[BrowserModel::class.java]
