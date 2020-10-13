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

package org.videolan.television.viewmodel

import android.app.Application
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.moviepedia.database.models.MediaMetadataWithImages
import org.videolan.moviepedia.repository.MediaMetadataRepository
import org.videolan.resources.*
import org.videolan.resources.util.getFromMl
import org.videolan.television.ui.MainTvActivity
import org.videolan.television.ui.NowPlayingDelegate
import org.videolan.television.ui.audioplayer.AudioPlayerActivity
import org.videolan.television.ui.browser.TVActivity
import org.videolan.television.ui.browser.VerticalGridActivity
import org.videolan.tools.NetworkMonitor
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.Settings
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.mediadb.models.BrowserFav
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.convertFavorites
import org.videolan.vlc.util.scanAllowed

private const val NUM_ITEMS_PREVIEW = 5
private const val TAG = "MainTvModel"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class MainTvModel(app: Application) : AndroidViewModel(app), Medialibrary.OnMedialibraryReadyListener,
        Medialibrary.OnDeviceChangeListener {

    val context = getApplication<Application>().baseContext!!
    private val medialibrary = Medialibrary.getInstance()
    private val networkMonitor = NetworkMonitor.getInstance(context)
    val settings = Settings.getInstance(context)
    private val showInternalStorage = AndroidDevices.showInternalStorage()
    private val browserFavRepository = BrowserFavRepository.getInstance(context)
    private val mediaMetadataRepository = MediaMetadataRepository.getInstance(context)
    private var updatedFavoritList: List<MediaWrapper> = listOf()
    var showHistory = false
        private set
    // LiveData
    private val favorites: LiveData<List<BrowserFav>> = browserFavRepository.browserFavorites.asLiveData(viewModelScope.coroutineContext)
    val nowPlaying: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val videos: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioCategories: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val browsers: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val history: LiveData<List<MediaWrapper>> = MutableLiveData()
    val playlist: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val recentlyPlayed: MediatorLiveData<List<MediaMetadataWithImages>> = MediatorLiveData()
    val recentlyAdded: MediatorLiveData<List<MediaMetadataWithImages>> = MediatorLiveData()

    private val nowPlayingDelegate = NowPlayingDelegate(this)

    private val updateActor = viewModelScope.actor<Unit>(capacity = Channel.CONFLATED) {
        for (action in channel) updateBrowsers()
    }

    private val historyActor = viewModelScope.actor<Unit>(capacity = Channel.CONFLATED) {
        for (action in channel) setHistory()
    }

    private val favObserver = Observer<List<BrowserFav>> { list ->
        updatedFavoritList = convertFavorites(list)
        if (!updateActor.isClosedForSend) updateActor.offer(Unit)
    }

    private val playerObserver = Observer<Boolean> { updateAudioCategories() }

    private val videoObserver = Observer<Any> { updateVideos() }

    init {
        medialibrary.addOnMedialibraryReadyListener(this)
        medialibrary.addOnDeviceChangeListener(this)
        favorites.observeForever(favObserver)
        networkMonitor.connectionFlow.onEach { updateActor.offer(Unit) }.launchIn(viewModelScope)
        ExternalMonitor.storageEvents.onEach { updateActor.offer(Unit) }.launchIn(viewModelScope)
        PlaylistManager.showAudioPlayer.observeForever(playerObserver)
        mediaMetadataRepository.getAllLive().observeForever(videoObserver)
    }

    fun refresh() = viewModelScope.launch {
        updateNowPlaying()
        updateVideos()
        updateRecentlyPlayed()
        updateRecentlyAdded()
        updateAudioCategories()
        historyActor.offer(Unit)
        updateActor.offer(Unit)
        updatePlaylists()
    }

    private suspend fun setHistory() {
        if (!medialibrary.isStarted) return
        val historyEnabled = settings.getBoolean(PLAYBACK_HISTORY, true)
        showHistory = historyEnabled
        if (!historyEnabled) (history as MutableLiveData).value = emptyList()
        else updateHistory()
    }

    suspend fun updateHistory() {
        if (!showHistory) return
        (history as MutableLiveData).value = context.getFromMl { lastMediaPlayed().toMutableList() }
    }

    private fun updateVideos() = viewModelScope.launch {
        val allMovies = withContext(Dispatchers.IO) { mediaMetadataRepository.getMovieCount() }
        val allTvshows = withContext(Dispatchers.IO) { mediaMetadataRepository.getTvshowsCount() }
        val videoNb = context.getFromMl { videoCount }
        context.getFromMl {
            getPagedVideos(Medialibrary.SORT_INSERTIONDATE, true, NUM_ITEMS_PREVIEW, 0)
        }.let {
            (videos as MutableLiveData).value = mutableListOf<MediaLibraryItem>().apply {
                add(DummyItem(HEADER_VIDEO, context.getString(R.string.videos_all), context.resources.getQuantityString(R.plurals.videos_quantity, videoNb, videoNb)))
                if (allMovies > 0) {
                    add(DummyItem(HEADER_MOVIES, context.getString(R.string.header_movies), context.resources.getQuantityString(R.plurals.movies_quantity, allMovies, allMovies)))
                }
                if (allTvshows > 0) {
                    add(DummyItem(HEADER_TV_SHOW, context.getString(R.string.header_tvshows), context.resources.getQuantityString(R.plurals.tvshow_quantity, allTvshows, allTvshows)))
                }
                addAll(it)
            }
        }
    }

    private fun updateRecentlyPlayed() = viewModelScope.launch {
        val history = context.getFromMl { lastMediaPlayed().toMutableList() }
        recentlyPlayed.addSource(withContext(Dispatchers.IO) { mediaMetadataRepository.getByIds(history.map { it.id }) }) {
            recentlyPlayed.value = it.sortedBy { history.indexOf(history.find { media -> media.id == it.metadata.mlId }) }
        }

    }

    private fun updateRecentlyAdded() = viewModelScope.launch {
        recentlyAdded.addSource(withContext(Dispatchers.IO) { mediaMetadataRepository.getRecentlyAdded() }) {
            recentlyAdded.value = it
        }

    }

    fun updateNowPlaying() = viewModelScope.launch {
        val list = mutableListOf<MediaLibraryItem>()
        PlaybackService.instance?.run {
            currentMediaWrapper?.let {
                if (this.playlistManager.player.isVideoPlaying())
                    DummyItem(CATEGORY_NOW_PLAYING_PIP, it.title, it.artist).apply { setArtWork(coverArt) }
                else
                    DummyItem(CATEGORY_NOW_PLAYING, it.title, it.artist).apply { setArtWork(coverArt) }
            }
        }?.let { list.add(0, it) }
        (nowPlaying as MutableLiveData).value = list
    }

    private fun updatePlaylists() = viewModelScope.launch {
        context.getFromMl {
            getPagedPlaylists(Medialibrary.SORT_INSERTIONDATE, true, NUM_ITEMS_PREVIEW, 0)
        }.let {
            (playlist as MutableLiveData).value = mutableListOf<MediaLibraryItem>().apply {
                //                add(DummyItem(HEADER_PLAYLISTS, context.getString(R.string.playlists), ""))
                addAll(it)
            }
        }
    }

    private fun updateAudioCategories() {
        val list = mutableListOf<MediaLibraryItem>(
                DummyItem(CATEGORY_ARTISTS, context.getString(R.string.artists), ""),
                DummyItem(CATEGORY_ALBUMS, context.getString(R.string.albums), ""),
                DummyItem(CATEGORY_GENRES, context.getString(R.string.genres), ""),
                DummyItem(CATEGORY_SONGS, context.getString(R.string.tracks), "")
        )
        (audioCategories as MutableLiveData).value = list
    }

    private suspend fun updateBrowsers() {
        val list = mutableListOf<MediaLibraryItem>()
        val directories = DirectoryRepository.getInstance(context).getMediaDirectoriesList(context).toMutableList()
        if (!showInternalStorage && directories.isNotEmpty()) directories.removeAt(0)
        directories.forEach { if (it.location.scanAllowed()) list.add(it) }

        if (networkMonitor.isLan) {
            list.add(DummyItem(HEADER_NETWORK, context.getString(R.string.network_browsing), null))
            list.add(DummyItem(HEADER_STREAM, context.getString(R.string.streams), null))
            list.add(DummyItem(HEADER_SERVER, context.getString(R.string.server_add_title), null))
            updatedFavoritList.forEach {
                it.description = it.uri.scheme
                list.add(it)
            }
        }
        (browsers as MutableLiveData).value = list
        delay(500L)
    }

    override fun onMedialibraryIdle() {
        refresh()
    }

    override fun onMedialibraryReady() {
        refresh()
    }

    override fun onDeviceChange() {
        refresh()
    }

    override fun onCleared() {
        super.onCleared()
        medialibrary.removeOnMedialibraryReadyListener(this)
        medialibrary.removeOnDeviceChangeListener(this)
        favorites.removeObserver(favObserver)
        PlaylistManager.showAudioPlayer.removeObserver(playerObserver)
        nowPlayingDelegate.onClear()
    }

    fun open(activity: FragmentActivity, item: Any?) {
        when (item) {
            is MediaWrapper -> when {
                item.type == MediaWrapper.TYPE_DIR -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, if ("file" == item.uri.scheme) HEADER_DIRECTORIES else HEADER_NETWORK)
                    intent.putExtra(FAVORITE_TITLE, item.title)
                    intent.data = item.uri
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
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
            is MediaMetadataWithImages -> {
                item.metadata.mlId?.let {
                    viewModelScope.launch {
                        context.getFromMl {
                            getMedia(it)
                        }.let {
                            val intent = Intent(activity, org.videolan.television.ui.DetailsActivity::class.java)
                            // pass the item information
                            intent.putExtra("media", it)
                            intent.putExtra("item", org.videolan.television.ui.MediaItemDetails(it.title, it.artist, it.album, it.location, it.artworkURL))
                            activity.startActivity(intent)
                        }
                    }
                }
            }
            is MediaLibraryItem -> org.videolan.television.ui.TvUtil.openAudioCategory(activity, item)
        }
    }

    companion object {
        fun Fragment.getMainTvModel() = ViewModelProviders.of(requireActivity(), Factory(requireActivity().application)).get(MainTvModel::class.java)
    }

    class Factory(private val app: Application) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MainTvModel(app) as T
        }
    }
}
