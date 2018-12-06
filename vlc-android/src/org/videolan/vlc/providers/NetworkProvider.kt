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

import android.content.Context
import androidx.lifecycle.Observer
import kotlinx.coroutines.launch
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.R
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.LiveDataset

class NetworkProvider(context: Context, dataset: LiveDataset<MediaLibraryItem>, url: String? = null, showHiddenFiles: Boolean): BrowserProvider(context, dataset, url, showHiddenFiles), Observer<List<MediaWrapper>> {

    private val favorites = if (url == null && !AndroidDevices.showTvUi(context)) BrowserFavRepository.getInstance(context).networkFavorites else null

    init {
        favorites?.observeForever(this)
    }

    override suspend fun browseRoot() {
        if (ExternalMonitor.allowLan()) browse()
    }

    override fun fetch() {}

    override fun refresh(): Boolean {
        return if (url == null) {
            dataset.value = mutableListOf<MediaLibraryItem>().apply {
                getFavoritesList(favorites?.value)?.let { addAll(it) }
            }
            launch { browseRoot() }
            true
        } else super.refresh()
    }

    override fun parseSubDirectories() {
        if (url != null) super.parseSubDirectories()
    }

    override fun stop(): Unit? {
        if (url == null) mediabrowser?.changeEventListener(null)
        return super.stop()
    }

    override fun release() {
        favorites?.removeObserver(this)
        super.release()
    }

    override fun onChanged(favs: List<MediaWrapper>?) {
        val data = dataset.value.toMutableList()
        data.listIterator().run {
            while (hasNext()) {
                val item = next()
                if (item.hasStateFlags(MediaLibraryItem.FLAG_FAVORITE) || item is DummyItem) remove()
            }
        }
        dataset.value = data.apply { getFavoritesList(favs)?.let { addAll(0, it) } }
    }

    private fun getFavoritesList(favs: List<MediaWrapper>?): MutableList<MediaLibraryItem>? {
        if (favs?.isNotEmpty() == true) {
            val list = mutableListOf<MediaLibraryItem>()
            list.add(0, DummyItem(context.getString(R.string.network_favorites)))
            for ((index, fav) in favs.withIndex()) list.add(index + 1, fav)
            list.add(DummyItem(context.getString(R.string.network_shared_folders)))
            return list
        }
        return null
    }
}