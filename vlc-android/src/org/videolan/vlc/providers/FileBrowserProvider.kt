/*****************************************************************************
 * FileBrowserProvider.kt
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

package org.videolan.vlc.providers

import android.content.Context
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.text.TextUtils
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.R
import org.videolan.vlc.database.models.BrowserFav
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate
import org.videolan.vlc.gui.helpers.hf.getDocumentFiles
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.LiveDataset
import org.videolan.vlc.util.convertFavorites
import java.io.File

open class FileBrowserProvider(
        context: Context,
        dataset: LiveDataset<MediaLibraryItem>,
        url: String?, private val filePicker: Boolean = false,
        showHiddenFiles: Boolean) : BrowserProvider(context, dataset,
        url, showHiddenFiles
), Observer<MutableList<UsbDevice>> {

    private var storagePosition = -1
    private var otgPosition = -1
    private val showFavorites : Boolean
    private val favorites = if (url == null && !filePicker) BrowserFavRepository.getInstance(context).localFavorites else null

    private val favoritesObserver by lazy { Observer<List<BrowserFav>> {
        val favs = convertFavorites(it)
        val data = dataset.value.toMutableList()
        if (data.size > 1) {
            data.listIterator(1).run {
                while (hasNext()) {
                    val item = next()
                    if (item.hasStateFlags(MediaLibraryItem.FLAG_FAVORITE) || item is DummyItem) remove()
                }
            }
        }
        launch {
            if (favs.isNotEmpty()) {
                job?.cancelAndJoin()
                val position = data.size
                var favAdded = false
                for (fav in favs) if (File(fav.uri.path).exists()) {
                    favAdded = true
                    data.add(fav)
                }
                if (favAdded) {
                    val quickAccess = context.getString(R.string.browser_quick_access)
                    data.add(position, DummyItem(quickAccess))
                }
            }
            dataset.value = data
            parseSubDirectories()
        }
    } }

    init {
        showFavorites = url == null && !filePicker && this !is StorageProvider
    }

    private lateinit var storageObserver : Observer<Boolean>

    override suspend fun browseRoot() {
        var storageAccess = false
        val internalmemoryTitle = context.getString(R.string.internal_memory)
        val browserStorage = context.getString(R.string.browser_storages)
        val storages = DirectoryRepository.getInstance(context).getMediaDirectories()
        val devices = mutableListOf<MediaLibraryItem>()
        if (!filePicker) devices.add(DummyItem(browserStorage))
        for (mediaDirLocation in storages) {
            val file = File(mediaDirLocation)
            if (!file.exists() || !file.canRead()) continue
            storageAccess = true
            val directory = MediaWrapper(AndroidUtil.PathToUri(mediaDirLocation))
            directory.type = MediaWrapper.TYPE_DIR
            if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, mediaDirLocation)) {
                directory.setDisplayTitle(internalmemoryTitle)
                storagePosition = devices.size
            } else {
                val deviceName = FileUtils.getStorageTag(directory.title)
                if (deviceName != null) directory.setDisplayTitle(deviceName)
                directory.addStateFlags(MediaLibraryItem.FLAG_STORAGE)
            }
            devices.add(directory)
        }
        if (AndroidUtil.isMarshMallowOrLater && !storageAccess) {
            storageObserver = Observer { if (it == true) launch { browseRoot() } }
            StoragePermissionsDelegate.storageAccessGranted.observeForever(storageObserver)
        }
        if (!storageAccess) return // For first launch, storage access may not already be granted
        if (AndroidUtil.isLolliPopOrLater && !ExternalMonitor.devices.value.isEmpty()) {
            val otg = MediaWrapper(Uri.parse("otg://")).apply {
                title = context.getString(R.string.otg_device_title)
                type = MediaWrapper.TYPE_DIR
            }
            otgPosition = devices.size
            devices.add(otg)
        }
        dataset.value = devices
        // observe devices & favorites
        ExternalMonitor.devices.observeForever(this@FileBrowserProvider)
        if (showFavorites) favorites?.observeForever(favoritesObserver)
    }


    override fun browse(url: String?) {
        when {
            url == "otg://" || url?.startsWith("content:") == true -> launch {
                dataset.value = withContext(Dispatchers.IO) { getDocumentFiles(context, Uri.parse(url).path?.substringAfterLast(':') ?: "") as? MutableList<MediaLibraryItem> ?: mutableListOf() }
            }
            else -> super.browse(url)
        }
    }

    override fun release() {
        if (url == null) {
            ExternalMonitor.devices.removeObserver(this)
            if (showFavorites) favorites?.removeObserver(favoritesObserver)
            if (this::storageObserver.isInitialized) {
                StoragePermissionsDelegate.storageAccessGranted.removeObserver(storageObserver)
            }
        }
        super.release()
    }

    override fun onChanged(list: MutableList<UsbDevice>?) {
        if (list?.isEmpty() != false) {
            if (otgPosition != -1) {
                dataset.remove(otgPosition)
                otgPosition = -1
            }
        } else if (otgPosition == -1) {
            val otg = MediaWrapper(Uri.parse("otg://")).apply {
                title = context.getString(R.string.otg_device_title)
                type = MediaWrapper.TYPE_DIR
            }
            otgPosition = storagePosition+1
            dataset.add(otgPosition, otg)
        }
    }
}