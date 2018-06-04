/*****************************************************************************
 * StorageProvider.kt
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

import android.net.Uri
import android.text.TextUtils
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.medialibrary.media.Storage
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.CustomDirectories
import org.videolan.vlc.util.LiveDataset
import java.io.File
import java.util.*

class StorageProvider(dataset: LiveDataset<MediaLibraryItem>, url: String?, showHiddenFiles: Boolean): FileBrowserProvider(dataset, url, false, showHiddenFiles) {

    override fun browseRoot() {
        val storages = AndroidDevices.getMediaDirectories()
        val customDirectories = CustomDirectories.getCustomDirectories()
        var storage: Storage
        val storagesList = ArrayList<MediaLibraryItem>()
        for (mediaDirLocation in storages) {
            if (TextUtils.isEmpty(mediaDirLocation)) continue
            storage = Storage(Uri.fromFile(File(mediaDirLocation)))
            if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, mediaDirLocation))
                storage.name = VLCApplication.getAppResources().getString(R.string.internal_memory)
            storagesList.add(storage)
        }
        customLoop@ for (customDir in customDirectories) {
            for (mediaDirLocation in storages) {
                if (TextUtils.isEmpty(mediaDirLocation)) continue
                if (customDir.startsWith(mediaDirLocation)) continue@customLoop
            }
            storage = Storage(Uri.parse(customDir))
            storagesList.add(storage)
        }
        dataset.value = storagesList
    }

    override fun addMedia(media: MediaLibraryItem) {
        if (media.itemType == MediaLibraryItem.TYPE_MEDIA) {
            if ((media as MediaWrapper).type == MediaWrapper.TYPE_DIR) super.addMedia(Storage(media.uri))
            return
        } else if (media.itemType != MediaLibraryItem.TYPE_STORAGE) return
        super.addMedia(media)
    }
}