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