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
import androidx.core.content.ContextCompat
import androidx.databinding.ViewDataBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.resources.AndroidDevices
import org.videolan.tools.containsPath
import org.videolan.tools.removeFileScheme
import org.videolan.tools.stripTrailingSlash
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.getDescriptionSpan
import org.videolan.vlc.util.isSchemeFile

class StorageBrowserAdapter(browserContainer: BrowserContainer<MediaLibraryItem>) : BaseBrowserAdapter(browserContainer) {

    private var mediaDirsLocation: MutableList<String> = mutableListOf()
    private lateinit var customDirsLocation: List<String>
    var bannedFolders: List<String> = listOf()
    private var updateJob : Job? = null

    init {
        updateMediaDirs(browserContainer.containerActivity())
    }

    override fun onBindViewHolder(holder: ViewHolder<ViewDataBinding>, position: Int) {
        val vh = holder as MediaViewHolder
        vh.job = launch {
            var storage = getItemByPosition(position)
            val title = storage.title
            if (storage.itemType == MediaLibraryItem.TYPE_MEDIA) storage = Storage((storage as MediaWrapper).uri)
            val uri = (storage as Storage).uri
            var storagePath = if (uri.scheme.isSchemeFile()) uri.path ?: "" else Uri.decode(uri.toString())
            if (!storagePath.endsWith("/")) storagePath += "/"
            if (storage.title.isNullOrBlank()) storage.title = title
            vh.bindingContainer.setItem(storage)
            vh.bindingContainer.setIsTv(AndroidDevices.isTv)
            updateJob?.join()
            if (updateJob?.isCancelled == true) return@launch
            val hasContextMenu = customDirsLocation.contains(storagePath.stripTrailingSlash()) && !multiSelectHelper.inActionMode
            val checked = browserContainer.scannedDirectory || mediaDirsLocation.containsPath(storagePath)
            vh.bindingContainer.setHasContextMenu(hasContextMenu)
            val banned = MedialibraryUtils.isBanned(uri, bannedFolders)
            val bannedParent = banned && !MedialibraryUtils.isStrictlyBanned(uri, bannedFolders)

            vh.bindingContainer.setIsBanned(banned)
            vh.bindingContainer.setIsBannedByParent(bannedParent)
            when {
                banned -> vh.bindingContainer.browserCheckbox.state = ThreeStatesCheckbox.STATE_UNCHECKED
                checked -> vh.bindingContainer.browserCheckbox.state = ThreeStatesCheckbox.STATE_CHECKED
                hasDiscoveredChildren(storagePath) -> vh.bindingContainer.browserCheckbox.state = ThreeStatesCheckbox.STATE_PARTIAL
                else -> vh.bindingContainer.browserCheckbox.state = ThreeStatesCheckbox.STATE_UNCHECKED
            }
            if(AndroidDevices.isTv && !browserContainer.isRootDirectory) vh.bindingContainer.banIcon.visibility = View.VISIBLE
            vh.bindingContainer.setCheckEnabled(!browserContainer.scannedDirectory)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder<ViewDataBinding>, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] is CharSequence) {
            if (!MedialibraryUtils.isBanned((getItemByPosition(position) as Storage).uri, bannedFolders)) {
                (holder as MediaViewHolder).bindingContainer.text.visibility = View.VISIBLE
                holder.bindingContainer.text.text = (payloads[0] as CharSequence).getDescriptionSpan(holder.bindingContainer.text.context)
            }
        } else super.onBindViewHolder(holder, position, payloads)
    }

    /**
     * Manages the item visibility on focus changes
     *
     * @param position the item position
     * @param hasFocus true if the item has focus
     * @param bindingContainer the [BrowserItemBindingContainer] associated with the item
     */

    override fun itemFocusChanged(position: Int, hasFocus: Boolean, bindingContainer: BrowserItemBindingContainer) {
        if (!AndroidDevices.isTv) return
        if (browserContainer.isRootDirectory) return
        if (position < 0 || position > itemCount - 1) return
        val uri = (getItemByPosition(position) as Storage).uri.toString()
        val banned = MedialibraryUtils.isBanned(uri, bannedFolders)
        val context = bindingContainer.container.context
        val bannedParent = banned && !MedialibraryUtils.isStrictlyBanned(uri, bannedFolders)
        val alpha = when {
            banned || bannedParent -> 1F
            hasFocus -> 1F
            else ->0F
        }

        bindingContainer.banIcon.animate().alpha(alpha)
        bindingContainer.banIcon.setImageDrawable( ContextCompat.getDrawable(context, if (banned || bannedParent) R.drawable.ic_banned else R.drawable.ic_ban))
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
            val folders = if (!Medialibrary.getInstance().isInitiated) {
                MediaParsingService.preselectedStorages.toTypedArray()
            } else {
                withContext(Dispatchers.IO) { Medialibrary.getInstance().foldersList }
            }

            folders.forEach {
                mediaDirsLocation.add(Uri.decode(it.removeFileScheme()))
            }
            customDirsLocation = DirectoryRepository.getInstance(context).getCustomDirectories().map { it.path }
        }
    }

    override fun checkBoxAction(v: View, mrl: String) {
        browserContainer.getStorageDelegate()?.checkBoxAction(v, mrl)
    }
}
