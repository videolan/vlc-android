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
import org.videolan.vlc.providers.NetworkProvider

class NetworkModel(url: String? = null, showHiddenFiles: Boolean): BrowserModel(url, TYPE_NETWORK, showHiddenFiles) {
    private val networkProvider = provider as NetworkProvider
    val favorites : MutableLiveData<MutableList<MediaLibraryItem>> by lazy {
        launch(UI) { updateFavs() }
        MutableLiveData<MutableList<MediaLibraryItem>>()
    }

    fun updateFavs() = launch(UI, CoroutineStart.UNDISPATCHED) {
        favorites.value = withContext(CommonPool) { networkProvider.updateFavorites() }
    }

    override fun refresh() : Boolean {
        updateFavs()
        return provider.refresh()
    }

    class Factory(val url: String?, private val showHiddenFiles: Boolean): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NetworkModel(url, showHiddenFiles) as T
        }
    }
}