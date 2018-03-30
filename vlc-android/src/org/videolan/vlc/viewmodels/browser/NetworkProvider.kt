package org.videolan.vlc.viewmodels.browser

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.media.MediaDatabase
import java.util.*

class NetworkProvider(url: String? = null, showHiddenFiles: Boolean): BrowserProvider(url, showHiddenFiles) {
    val favorites by lazy {
        MutableLiveData<MutableList<MediaLibraryItem>>()
    }
    override fun browseRoot() {
        launch(UI, CoroutineStart.UNDISPATCHED) { updateFavorites() }
        if (allowLAN()) launch(browserContext) {
            initBrowser()
            mediabrowser?.discoverNetworkShares()
        }
    }

    fun updateFavs() = launch(UI, CoroutineStart.UNDISPATCHED) { updateFavorites() }

    private suspend fun updateFavorites() {
        if (ExternalMonitor.connected?.value != true) favorites.value = mutableListOf()
        val favs: MutableList<MediaLibraryItem> = withContext(CommonPool) { MediaDatabase.getInstance().allNetworkFav }.toMutableList()
        if (!allowLAN()) {
            val schemes = Arrays.asList("ftp", "sftp", "ftps", "http", "https")
            val toRemove = favs.filterNotTo(mutableListOf()) { schemes.contains((it as MediaWrapper).uri.scheme) }
            if (!toRemove.isEmpty()) for (mw in toRemove) favs.remove(mw)
        }
        favorites.value = favs
    }

    override fun fetch() {}

    override fun refresh(): Boolean {
        super.fetch()
        return true
    }

    private fun allowLAN(): Boolean {
        return ExternalMonitor.isLan() || ExternalMonitor.isVPN()
    }

    class Factory(val url: String?, private val showHiddenFiles: Boolean): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NetworkProvider(url, showHiddenFiles) as T
        }
    }
}