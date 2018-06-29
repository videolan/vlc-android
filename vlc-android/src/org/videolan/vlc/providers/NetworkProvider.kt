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

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.media.MediaDatabase
import org.videolan.vlc.util.LiveDataset
import java.util.*

class NetworkProvider(dataset: LiveDataset<MediaLibraryItem>, url: String? = null, showHiddenFiles: Boolean): BrowserProvider(dataset, url, showHiddenFiles) {

    override fun browseRoot() {
        if (ExternalMonitor.allowLan()) browse()
    }

    suspend fun updateFavorites() : MutableList<MediaLibraryItem> {
        if (!ExternalMonitor.isConnected()) return mutableListOf()
        val favs: MutableList<MediaLibraryItem> = withContext(CommonPool) { MediaDatabase.getInstance().allNetworkFav }.toMutableList()
        if (!ExternalMonitor.allowLan()) {
            val schemes = Arrays.asList("ftp", "sftp", "ftps", "http", "https")
            val toRemove = favs.filterNotTo(mutableListOf()) { schemes.contains((it as MediaWrapper).uri.scheme) }
            if (!toRemove.isEmpty()) for (mw in toRemove) favs.remove(mw)
        }
        return favs
    }

    override fun fetch() { if (ExternalMonitor.allowLan()) super.fetch() }

    override fun refresh(): Boolean {
        if (url == null) {
            dataset.value = mutableListOf()
            browseRoot()
        } else super.refresh()
        return true
    }
}