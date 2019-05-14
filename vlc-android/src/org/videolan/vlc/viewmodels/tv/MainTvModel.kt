/*
 * *************************************************************************
 *  MainTvModel.kt
 * **************************************************************************
 *  Copyright © 2019 VLC authors and VideoLAN
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.viewmodels.tv

import android.app.Application
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.database.models.BrowserFav
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.preferences.PreferencesFragment
import org.videolan.vlc.gui.tv.MainTvActivity
import org.videolan.vlc.gui.tv.NowPlayingDelegate
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity
import org.videolan.vlc.gui.tv.browser.TVActivity
import org.videolan.vlc.gui.tv.browser.VerticalGridActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.*

private const val NUM_ITEMS_PREVIEW = 5

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class MainTvModel(app: Application) : AndroidViewModel(app), Medialibrary.OnMedialibraryReadyListener,
        Medialibrary.OnDeviceChangeListener, CoroutineScope by MainScope() {

    val context = getApplication<Application>().baseContext!!
    private val medialibrary = Medialibrary.getInstance()
    val settings = Settings.getInstance(context)
    private val showInternalStorage = AndroidDevices.showInternalStorage()
    private val browserFavRepository = BrowserFavRepository.getInstance(context)
    private var updatedFavoritList: List<MediaWrapper> = listOf()
    private var showHistory = false
    // LiveData
    private val favorites: LiveData<List<BrowserFav>> = browserFavRepository.browserFavorites
    val videos : LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioCategories : LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val browsers : LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val history : LiveData<List<MediaWrapper>> = MutableLiveData()

    private val nowPlayingDelegate = NowPlayingDelegate(this)

    private val updateActor = actor<Unit>(capacity = Channel.CONFLATED) {
        for (action in channel) updateBrowsers()
    }

    private val favObserver = Observer<List<BrowserFav>> { list ->
        updatedFavoritList = convertFavorites(list)
        if (!updateActor.isClosedForSend) updateActor.offer(Unit)
    }

    private val monitorObserver = Observer<Any> { updateActor.offer(Unit) }

    private val playerObserver = Observer<Boolean> { updateAudioCategories() }

    init {
        medialibrary.addOnMedialibraryReadyListener(this)
        medialibrary.addOnDeviceChangeListener(this)
        favorites.observeForever(favObserver)
        ExternalMonitor.connected.observeForever(monitorObserver)
        ExternalMonitor.storageUnplugged.observeForever(monitorObserver)
        ExternalMonitor.storagePlugged.observeForever(monitorObserver)
        PlaylistManager.showAudioPlayer.observeForever(playerObserver)
    }

    fun refresh() = launch {
        updateAudioCategories()
        updateActor.offer(Unit)
        updateVideos()
        setHistory()
    }

    private fun setHistory() {
        val historyEnabled = settings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true)
        if (showHistory != historyEnabled) {
            showHistory = historyEnabled
            if (!historyEnabled) (history as MutableLiveData).value = emptyList()
            else updateHistory()
        }
    }

    fun updateHistory() {
        if (showHistory) launch {
            (history as MutableLiveData).value = withContext(Dispatchers.Default) { medialibrary.lastMediaPlayed().toMutableList() }
        }
    }

    private fun updateVideos() = launch {
        context.getFromMl {
            getPagedVideos(Medialibrary.SORT_INSERTIONDATE, true, NUM_ITEMS_PREVIEW, 0)
        }.let {
            (videos as MutableLiveData).value = mutableListOf<MediaLibraryItem>().apply {
                add(DummyItem(HEADER_VIDEO, context.getString(R.string.videos_all), context.resources.getQuantityString(R.plurals.videos_quantity, it.size, it.size)))
                addAll(it)
            }
        }
    }

    fun updateAudioCategories() {
        val list = mutableListOf<MediaLibraryItem>(
                DummyItem(CATEGORY_ARTISTS, context.getString(R.string.artists), ""),
                DummyItem(CATEGORY_ALBUMS, context.getString(R.string.albums), ""),
                DummyItem(CATEGORY_GENRES, context.getString(R.string.genres), ""),
                DummyItem(CATEGORY_SONGS, context.getString(R.string.tracks), "")
        )
        PlaybackService.service.value?.run {
            currentMediaWrapper?.let {
                DummyItem(CATEGORY_NOW_PLAYING, it.title, it.artist).apply { setArtWork(coverArt) }
            }
        }?.let { list.add(0, it) }
        (audioCategories as MutableLiveData).value = list
    }

    private suspend fun updateBrowsers() {
        val list = mutableListOf<MediaLibraryItem>()
        val directories = DirectoryRepository.getInstance(context).getMediaDirectoriesList(context).toMutableList()
        if (!showInternalStorage && directories.isNotEmpty()) directories.removeAt(0)
        directories.forEach { if (it.location.scanAllowed()) list.add(it) }

        if (ExternalMonitor.isLan) {
            list.add(DummyItem(HEADER_NETWORK, context.getString(R.string.network_browsing), null))
            list.add(DummyItem(HEADER_STREAM, context.getString(R.string.open_mrl), null))
            list.add(DummyItem(HEADER_SERVER, context.getString(R.string.server_add_title), null))
            updatedFavoritList.forEach {
                it.description = it.uri.scheme
                list.add(it)
            }
        }
        (browsers as MutableLiveData).value = list
        delay(500L)
    }

    override fun onMedialibraryIdle() { refresh() }

    override fun onMedialibraryReady() { refresh() }

    override fun onDeviceChange() { refresh() }

    override fun onCleared() {
        super.onCleared()
        medialibrary.removeOnMedialibraryReadyListener(this)
        medialibrary.removeOnDeviceChangeListener(this)
        favorites.removeObserver(favObserver)
        ExternalMonitor.connected.removeObserver(monitorObserver)
        ExternalMonitor.storageUnplugged.removeObserver(monitorObserver)
        ExternalMonitor.storagePlugged.removeObserver(monitorObserver)
        PlaylistManager.showAudioPlayer.removeObserver(playerObserver)
        nowPlayingDelegate.onClear()
        cancel()
    }

    fun open(activity: FragmentActivity, item: Any?) {
        when (item) {
            is MediaWrapper -> when {
                item.type == MediaWrapper.TYPE_DIR -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, if ("file" == item.uri.scheme) HEADER_DIRECTORIES else HEADER_NETWORK)
                    intent.data = item.uri
                    activity.startActivity(intent)
                }
                else -> {
                    MediaUtils.openMedia(activity, item)
                    if (item.type == MediaWrapper.TYPE_AUDIO) {
                        activity.startActivity(Intent(activity, AudioPlayerActivity::class.java))
                    }
                }
            }
            is DummyItem -> when {
                item.id == HEADER_STREAM -> {
                    val intent = Intent(activity, TVActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, HEADER_STREAM)
                    activity.startActivity(intent)
                }
                item.id == HEADER_SERVER -> activity.startActivity(Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_SERVER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, item.id)
                    activity.startActivity(intent)
                }
            }
            is MediaLibraryItem -> TvUtil.openAudioCategory(activity, item)
        }
    }

    companion object {
        fun Fragment.getMainTvModel() = ViewModelProviders.of(requireActivity(), Factory(requireActivity().application)).get(MainTvModel::class.java)
    }

    class Factory(private val app: Application): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MainTvModel(app) as T
        }
    }
}
