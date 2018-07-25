/*****************************************************************************
 * NetworkProvider.kt
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

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Transformations
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.LiveDataset
import java.util.*

class NetworkProvider(dataset: LiveDataset<MediaLibraryItem>, url: String? = null, showHiddenFiles: Boolean): BrowserProvider(dataset, url, showHiddenFiles) {

    private val browserFavRepository by lazy { BrowserFavRepository(VLCApplication.getAppContext())}

    override fun browseRoot() {
        if (ExternalMonitor.allowLan()) browse()
    }

    fun getFavorites() : LiveData<List<MediaLibraryItem>> {
        val allNetworkFavs = browserFavRepository.getAllNetworkFavs()
        return Transformations.map(allNetworkFavs) { favs ->
            if (!ExternalMonitor.isConnected())  return@map listOf<MediaLibraryItem>()

            if (!ExternalMonitor.allowLan()) {
                val schemes = Arrays.asList("ftp", "sftp", "ftps", "http", "https")
                return@map favs?.filter { schemes.contains((it as MediaWrapper).uri.scheme) }
            }
            favs
        }
    }

    override fun fetch() {}

    override fun refresh(): Boolean {
        if (url == null) {
            dataset.value = mutableListOf()
            browseRoot()
        } else super.refresh()
        return true
    }

    override suspend fun parseSubDirectories() {
        if (url != null) super.parseSubDirectories()
    }
}