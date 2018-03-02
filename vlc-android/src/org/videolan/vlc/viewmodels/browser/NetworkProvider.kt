package org.videolan.vlc.viewmodels.browser

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.media.MediaDatabase
import java.util.*

class NetworkProvider(url: String?): BrowserProvider(url) {
    val favorites by lazy {
        launch(UI) { updateFavorites() }
        MutableLiveData<MutableList<MediaLibraryItem>>()
    }
    override fun browseRoot() {
        launch(UI, CoroutineStart.UNDISPATCHED) { updateFavorites() }
        if (allowLAN()) launch(browserContext) {
            initBrowser()
            mediabrowser?.discoverNetworkShares()
        }

    }

    override fun refresh(): Boolean {
        return url != null && return super.refresh()
    }

    private suspend fun updateFavorites() {
        if (!ExternalMonitor.isConnected()) favorites.value = mutableListOf()
        val favs: MutableList<MediaLibraryItem> = async { MediaDatabase.getInstance().allNetworkFav }.await().toMutableList()
        if (!allowLAN()) {
            val schemes = Arrays.asList("ftp", "sftp", "ftps", "http", "https")
            val toRemove = favs.filterNotTo(mutableListOf()) { schemes.contains((it as MediaWrapper).uri.scheme) }
            if (!toRemove.isEmpty()) for (mw in toRemove) favs.remove(mw)
        }
        favorites.value = favs
    }

    private fun allowLAN(): Boolean {
        return ExternalMonitor.isLan() || ExternalMonitor.isVPN()
    }

    class Factory(val url: String?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NetworkProvider(url) as T
        }
    }
}