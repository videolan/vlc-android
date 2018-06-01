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
        if (ExternalMonitor.connected?.value != true) return mutableListOf()
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