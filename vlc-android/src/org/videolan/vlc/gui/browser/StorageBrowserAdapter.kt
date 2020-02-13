/*
 * *************************************************************************
 *  StorageBrowserAdapter.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.browser

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.databinding.ViewDataBinding
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.containsPath

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal class StorageBrowserAdapter(fragment: StorageBrowserFragment) : BaseBrowserAdapter(fragment) {

    private var mediaDirsLocation: MutableList<String> = mutableListOf()
    private lateinit var customDirsLocation: List<String>
    private var updateJob : Job? = null

    init {
        updateMediaDirs(fragment.requireContext())
    }

    override fun onBindViewHolder(holder: ViewHolder<ViewDataBinding>, position: Int) {
        val vh = holder as MediaViewHolder
        vh.job = launch {
            var storage = getItem(position)
            if (storage.itemType == MediaLibraryItem.TYPE_MEDIA) storage = Storage((storage as AbstractMediaWrapper).uri)
            var storagePath = (storage as Storage).uri.path ?: ""
            if (!storagePath.endsWith("/")) storagePath += "/"
            vh.binding.item = storage
            updateJob?.join()
            if (updateJob?.isCancelled == true) return@launch
            val hasContextMenu = customDirsLocation.contains(storagePath)
            val checked = (fragment as StorageBrowserFragment).scannedDirectory || mediaDirsLocation.containsPath(storagePath)
            vh.binding.hasContextMenu = hasContextMenu
            when {
                checked -> vh.binding.browserCheckbox.state = ThreeStatesCheckbox.STATE_CHECKED
                hasDiscoveredChildren(storagePath) -> vh.binding.browserCheckbox.state = ThreeStatesCheckbox.STATE_PARTIAL
                else -> vh.binding.browserCheckbox.state = ThreeStatesCheckbox.STATE_UNCHECKED
            }
            vh.binding.checkEnabled = !fragment.scannedDirectory
        }
    }

    override fun onViewRecycled(holder: ViewHolder<ViewDataBinding>) {
        (holder as MediaViewHolder).apply {
            job?.cancel()
            job = null
        }
        super.onViewRecycled(holder)
    }

    private fun hasDiscoveredChildren(path: String): Boolean {
        for (directory in mediaDirsLocation) if (directory.startsWith(path)) return true
        return false
    }

    suspend fun updateListState(context: Context) {
        updateMediaDirs(context)
        updateJob?.join()
        if (updateJob?.isCancelled == false) notifyItemRangeChanged(0, itemCount)
    }

    fun updateMediaDirs(context: Context) {
        mediaDirsLocation.clear()

        updateJob = launch {
            val folders = if (!AbstractMedialibrary.getInstance().isInitiated) {
                MediaParsingService.preselectedStorages.toTypedArray()
            } else {
                withContext(Dispatchers.IO) { AbstractMedialibrary.getInstance().foldersList }
            }

            folders.forEach {
                mediaDirsLocation.add(Uri.decode(if (it.startsWith("file://")) it.substring(7) else it))
            }
            customDirsLocation = DirectoryRepository.getInstance(context).getCustomDirectories().map { it.path }
        }
    }

    override fun checkBoxAction(v: View, mrl: String) {
        (fragment as? StorageBrowserFragment)?.checkBoxAction(v, mrl)
    }
}
