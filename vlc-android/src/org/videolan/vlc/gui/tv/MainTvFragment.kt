/*
 * *************************************************************************
 *  MainTvFragment.kt
 * **************************************************************************
 *  Copyright © 2018 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
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

package org.videolan.vlc.gui.tv

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.tools.coroutineScope
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.R
import org.videolan.vlc.RecommendationsService
import org.videolan.vlc.database.models.BrowserFav
import org.videolan.vlc.gui.preferences.PreferencesFragment
import org.videolan.vlc.gui.tv.MainTvActivity.ACTIVITY_RESULT_PREFERENCES
import org.videolan.vlc.gui.tv.MainTvActivity.BROWSER_TYPE
import org.videolan.vlc.gui.tv.TvUtil.diffCallback
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity
import org.videolan.vlc.gui.tv.browser.VerticalGridActivity
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.repository.createDirectory
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.HistoryModel
import org.videolan.vlc.viewmodels.VideosModel

private const val NUM_ITEMS_PREVIEW = 5
private const val TAG = "VLC/MainTvFragment"

class MainTvFragment : BrowseSupportFragment(), OnItemViewSelectedListener, OnItemViewClickedListener, View.OnClickListener, Observer<MutableList<MediaWrapper>> {

    private var backgroundManager: BackgroundManager? = null
    private lateinit var videoModel: VideosModel
    private lateinit var historyModel: HistoryModel

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var videoAdapter: ArrayObjectAdapter
    private lateinit var categoriesAdapter: ArrayObjectAdapter
    private lateinit var historyAdapter: ArrayObjectAdapter
    private lateinit var browserAdapter: ArrayObjectAdapter
    private lateinit var otherAdapter: ArrayObjectAdapter

    private lateinit var videoRow: ListRow
    private lateinit var audioRow: ListRow
    private lateinit var historyRow: ListRow
    private lateinit var browsersRow: ListRow
    private lateinit var miscRow: ListRow

    private lateinit var settings: SharedPreferences
    private lateinit var nowPlayingDelegate: NowPlayingDelegate
    private var displayHistory = false
    private var selectedItem: Any? = null
    private var restart = false

    private lateinit var browserFavRepository: BrowserFavRepository

    private var updatedFavoritList: List<MediaWrapper> = listOf()
    private lateinit var favorites: LiveData<List<BrowserFav>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings.getInstance(requireContext())
        // Set display parameters for the BrowseFragment
        headersState = BrowseSupportFragment.HEADERS_ENABLED
        title = getString(R.string.app_name)
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.icon)

        //Enable search feature only if we detect Google Play Services.
        if (AndroidDevices.hasPlayServices) {
            setOnSearchClickedListener(this)
            // set search icon color
            searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.orange500)
        }
        brandColor = ContextCompat.getColor(requireContext(), R.color.orange800)
        backgroundManager = BackgroundManager.getInstance(requireActivity()).apply { attach(requireActivity().window) }
        nowPlayingDelegate = NowPlayingDelegate(this)

        browserFavRepository = BrowserFavRepository.getInstance(requireContext())
        favorites = browserFavRepository.browserFavorites
        favorites.observe(this, Observer{
            updatedFavoritList = convertFavorites(it)
            updateActor.offer(Browsers)
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireActivity()
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        // Video
        videoAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val videoHeader = HeaderItem(0, getString(R.string.video))
        videoRow = ListRow(videoHeader, videoAdapter)
        rowsAdapter.add(videoRow)
        // Audio
        categoriesAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val musicHeader = HeaderItem(HEADER_CATEGORIES, getString(R.string.audio))
        updateAudioCategories()
        audioRow = ListRow(musicHeader, categoriesAdapter)
        rowsAdapter.add(audioRow)
        //History

        //Browser section
        browserAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val browserHeader = HeaderItem(HEADER_NETWORK, getString(R.string.browsing))
        browsersRow = ListRow(browserHeader, browserAdapter)
        rowsAdapter.add(browsersRow)
        updateActor.offer(Browsers)
        //Misc. section
        otherAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val miscHeader = HeaderItem(HEADER_MISC, getString(R.string.other))

        otherAdapter.add(DummyItem(ID_SETTINGS, getString(R.string.preferences), ""))
        otherAdapter.add(DummyItem(ID_ABOUT_TV, getString(R.string.about), "${getString(R.string.app_name_full)} ${BuildConfig.VERSION_NAME}"))
        otherAdapter.add(DummyItem(ID_LICENCE, getString(R.string.licence), ""))
        miscRow = ListRow(miscHeader, otherAdapter)
        rowsAdapter.add(miscRow)

        adapter = rowsAdapter
        videoModel = VideosModel.get(requireContext(), this, null, Medialibrary.SORT_INSERTIONDATE, -1, desc = true)
        videoModel.dataset.observe(this, Observer {
            updateVideos(it)
            (requireActivity() as MainTvActivity).hideLoading()
        })
        ExternalMonitor.connected.observe(this, Observer { updateActor.offer(Browsers) })
        ExternalMonitor.storageUnplugged.observe(this, Observer { updateActor.offer(Browsers) })
        ExternalMonitor.storagePlugged.observe(this, Observer { updateActor.offer(Browsers) })
        onItemViewClickedListener = this
        onItemViewSelectedListener = this
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val browsePath = activity?.intent?.getStringExtra(EXTRA_PATH) ?: return
        TvUtil.openMedia(requireActivity(), createDirectory(browsePath, requireContext()), null)
    }

    fun updateAudioCategories(current: DummyItem? = null) {
        val list = mutableListOf<MediaLibraryItem>(
                DummyItem(CATEGORY_ARTISTS, getString(R.string.artists), ""),
                DummyItem(CATEGORY_ALBUMS, getString(R.string.albums), ""),
                DummyItem(CATEGORY_GENRES, getString(R.string.genres), ""),
                DummyItem(CATEGORY_SONGS, getString(R.string.tracks), "")
        )
        if (current !== null) list.add(0, current)
        categoriesAdapter.setItems(list.toList(), diffCallback)
    }

    override fun onStart() {
        super.onStart()
        if (restart) {
            if (this::historyModel.isInitialized) historyModel.refresh()
            videoModel.refresh()
        } else restart = true
        if (selectedItem is MediaWrapper) TvUtil.updateBackground(backgroundManager, selectedItem)
        setHistoryModel()
    }

    override fun onStop() {
        super.onStop()
        if (AndroidDevices.isAndroidTv && !AndroidUtil.isOOrLater) requireActivity().startService(Intent(requireActivity(), RecommendationsService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        nowPlayingDelegate.onClear()
    }

    override fun onClick(v: View?) = requireActivity().startActivity(Intent(requireContext(), SearchActivity::class.java))

    fun showDetails() : Boolean {
        val media = selectedItem as? MediaWrapper ?: return false
        if (media.type != MediaWrapper.TYPE_DIR) return false
        val intent = Intent(requireActivity(), DetailsActivity::class.java)
        // pass the item information
        intent.putExtra("media", media)
        intent.putExtra("item", MediaItemDetails(media.title, media.artist, media.album, media.location, media.artworkURL))
        startActivity(intent)
        return true
    }

    suspend fun updateBrowsers() {
        val list = mutableListOf<MediaLibraryItem>()
        val directories = DirectoryRepository.getInstance(requireContext()).getMediaDirectoriesList(requireContext().applicationContext).toMutableList()
        if (!AndroidDevices.showInternalStorage && !directories.isEmpty()) directories.removeAt(0)
        for (directory in directories) {
            if (directory.location.scanAllowed()) list.add(directory)
        }

        if (ExternalMonitor.isLan) {
            list.add(DummyItem(HEADER_NETWORK, getString(R.string.network_browsing), null))
            list.add(DummyItem(HEADER_STREAM, getString(R.string.open_mrl), null))
            list.add(DummyItem(HEADER_SERVER, getString(R.string.server_add_title), null))

            updatedFavoritList.forEach{
                it.description = it.uri.scheme
                list.add(it)
            }
        }
        browserAdapter.setItems(list, diffCallback)
    }

    override fun onItemClicked(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        val activity = requireActivity()
        when(row?.id) {
            HEADER_CATEGORIES -> {
                if ((item as DummyItem).id == CATEGORY_NOW_PLAYING) { //NOW PLAYING CARD
                    activity.startActivity(Intent(activity, AudioPlayerActivity::class.java))
                    return
                }
                val intent = Intent(activity, VerticalGridActivity::class.java)
                intent.putExtra(BROWSER_TYPE, HEADER_CATEGORIES)
                intent.putExtra(AUDIO_CATEGORY, item.id)
                activity.startActivity(intent)
            }
            HEADER_MISC -> {
                val id = (item as DummyItem).id
                when (id) {
                    ID_SETTINGS -> activity.startActivityForResult(Intent(activity, org.videolan.vlc.gui.tv.preferences.PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
                    ID_ABOUT_TV -> activity.startActivity(Intent(activity, org.videolan.vlc.gui.tv.AboutActivity::class.java))
                    ID_LICENCE -> startActivity(Intent(activity, org.videolan.vlc.gui.tv.LicenceActivity::class.java))
                }
            }
            else -> TvUtil.openMedia(activity, item, row)
        }
    }

    override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        selectedItem = item
        TvUtil.updateBackground(backgroundManager, item)
    }

    private fun setHistoryModel() {
        val historyEnabled = settings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true)
        if (historyEnabled == displayHistory) return
        if (historyEnabled) {
            historyModel = ViewModelProviders.of(this, HistoryModel.Factory(requireContext())).get(HistoryModel::class.java)
            historyModel.dataset.observe(this, this)
        } else {
            displayHistory = false
            historyModel.dataset.removeObserver(this)
        }
    }

    fun updateHistory() {
        if (this::historyModel.isInitialized) historyModel.refresh()
    }

    override fun onChanged(list: MutableList<MediaWrapper>?) {
        list?.let {
            if (!it.isEmpty()) {
                if (!displayHistory) {
                    displayHistory = true
                    if (!this::historyRow.isInitialized) {
                        historyAdapter = ArrayObjectAdapter(CardPresenter(requireActivity()))
                        val historyHeader = HeaderItem(HEADER_HISTORY, getString(R.string.history))
                        historyRow = ListRow(historyHeader, historyAdapter)
                    }
                }
                historyAdapter.setItems(it, diffCallback)
                val adapters = listOf(videoRow, audioRow, historyRow, browsersRow, miscRow)
                rowsAdapter.setItems(adapters, TvUtil.listDiffCallback)
            } else if (displayHistory) {
                displayHistory = false
                val adapters = listOf(videoRow, audioRow, browsersRow, miscRow)
                rowsAdapter.setItems(adapters, TvUtil.listDiffCallback)
            }
        }
    }

    private fun updateVideos(videos: List<MediaWrapper>?) {
        videos?.let {
            val list = mutableListOf<Any>()
            list.add(DummyItem(HEADER_VIDEO, getString(R.string.videos_all), resources.getQuantityString(R.plurals.videos_quantity, it.size, it.size)))
            if (!it.isEmpty()) for ((index, video) in it.withIndex()) {
                if (index == NUM_ITEMS_PREVIEW) break
                list.add(video)
            }
            videoAdapter.setItems(list, diffCallback)
        }
    }

    private val updateActor = coroutineScope.actor<Update>(capacity = Channel.CONFLATED) {
        for (action in channel) when (action) {
            Browsers -> {
                updateBrowsers()
                delay(500L)
            }
        }
    }
}

private sealed class Update
private object Browsers : Update()