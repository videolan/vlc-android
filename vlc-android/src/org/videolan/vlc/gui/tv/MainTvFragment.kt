/*
 * *************************************************************************
 *  MainTvFragment.kt
 * **************************************************************************
 *  Copyright © 2018-2019 VLC authors and VideoLAN
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
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.Observer
import kotlinx.coroutines.*
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.RecommendationsService
import org.videolan.vlc.gui.tv.TvUtil.diffCallback
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity
import org.videolan.vlc.gui.tv.browser.VerticalGridActivity
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.tv.MainTvModel
import org.videolan.vlc.viewmodels.tv.MainTvModel.Companion.getMainTvModel

private const val TAG = "VLC/MainTvFragment"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class MainTvFragment : BrowseSupportFragment(), OnItemViewSelectedListener, OnItemViewClickedListener,
        View.OnClickListener, CoroutineScope by MainScope() {

    private var backgroundManager: BackgroundManager? = null
    private lateinit var rowsAdapter: ArrayObjectAdapter

    private lateinit var nowPlayingAdapter: ArrayObjectAdapter
    private lateinit var videoAdapter: ArrayObjectAdapter
    private lateinit var categoriesAdapter: ArrayObjectAdapter
    private lateinit var historyAdapter: ArrayObjectAdapter
    private lateinit var playlistAdapter: ArrayObjectAdapter
    private lateinit var browserAdapter: ArrayObjectAdapter
    private lateinit var otherAdapter: ArrayObjectAdapter

    private lateinit var nowPlayingRow: ListRow
    private lateinit var videoRow: ListRow
    private lateinit var audioRow: ListRow
    private lateinit var historyRow: ListRow
    private lateinit var playlistRow: ListRow
    private lateinit var browsersRow: ListRow
    private lateinit var miscRow: ListRow

    private var displayHistory = false
    private var displayPlaylist = false
    private var displayNowPlaying = false
    private var selectedItem: Any? = null

    internal lateinit var model: MainTvModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set display parameters for the BrowseFragment
        headersState = HEADERS_ENABLED
        title = getString(R.string.app_name)
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.icon)

        //Enable search feature only if we detect Google Play Services.
        if (AndroidDevices.hasPlayServices) {
            setOnSearchClickedListener(this)
            // set search icon color
            searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.orange600)
        }
        brandColor = ContextCompat.getColor(requireContext(), R.color.orange900)
        backgroundManager = BackgroundManager.getInstance(requireActivity()).apply { attach(requireActivity().window) }
        model = getMainTvModel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireActivity()
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        // Now Playing
        nowPlayingAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val nowPlayingHeader = HeaderItem(HEADER_CATEGORIES, getString(R.string.music_now_playing))
        nowPlayingRow = ListRow(nowPlayingHeader, nowPlayingAdapter)
        rowsAdapter.add(nowPlayingRow)
        // Video
        videoAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val videoHeader = HeaderItem(0, getString(R.string.video))
        videoRow = ListRow(videoHeader, videoAdapter)
        rowsAdapter.add(videoRow)
        // Audio
        categoriesAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val musicHeader = HeaderItem(HEADER_CATEGORIES, getString(R.string.audio))
        audioRow = ListRow(musicHeader, categoriesAdapter)
        rowsAdapter.add(audioRow)
        //History

        // Playlists
        playlistAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val playlistHeader = HeaderItem(HEADER_PLAYLISTS, getString(R.string.playlists))
        playlistRow = ListRow(playlistHeader, playlistAdapter)
//        rowsAdapter.add(playlistRow)

        //Browser section
        browserAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val browserHeader = HeaderItem(HEADER_NETWORK, getString(R.string.browsing))
        browsersRow = ListRow(browserHeader, browserAdapter)
        rowsAdapter.add(browsersRow)
        //Misc. section
        otherAdapter = ArrayObjectAdapter(GenericCardPresenter(ctx))
        val miscHeader = HeaderItem(HEADER_MISC, getString(R.string.other))

        otherAdapter.add(GenericCardItem(ID_SETTINGS, getString(R.string.preferences), "", R.drawable.ic_menu_preferences_big, R.color.tv_card_content_dark))
        otherAdapter.add(GenericCardItem(ID_REFRESH, getString(R.string.refresh), "", R.drawable.ic_menu_scan, R.color.tv_card_content_dark))
        otherAdapter.add(GenericCardItem(ID_ABOUT_TV, getString(R.string.about), "${getString(R.string.app_name_full)} ${BuildConfig.VERSION_NAME}", R.drawable.ic_menu_info_big, R.color.tv_card_content_dark))
        otherAdapter.add(GenericCardItem(ID_LICENCE, getString(R.string.licence), "", R.drawable.ic_menu_open_source, R.color.tv_card_content_dark))
        miscRow = ListRow(miscHeader, otherAdapter)
        rowsAdapter.add(miscRow)

        historyAdapter = ArrayObjectAdapter(CardPresenter(requireActivity()))
        val historyHeader = HeaderItem(HEADER_HISTORY, getString(R.string.history))
        historyRow = ListRow(historyHeader, historyAdapter)

        adapter = rowsAdapter
        onItemViewClickedListener = this
        onItemViewSelectedListener = this
        // ViewModel setup
        registerDatasets()
    }

    private fun registerDatasets() {
        model.browsers.observe(this, Observer {
            browserAdapter.setItems(it, diffCallback)
        })
        model.audioCategories.observe(this, Observer {
            categoriesAdapter.setItems(it.toList(), diffCallback)
        })
        model.videos.observe(this, Observer {
            videoAdapter.setItems(it, diffCallback)
        })
        model.nowPlaying.observe(this, Observer {
            displayNowPlaying = it.isNotEmpty()
            nowPlayingAdapter.setItems(it, diffCallback)
        })
        model.history.observe(this, Observer {
            displayHistory = it.isNotEmpty()
            if (it.isNotEmpty()) {
                historyAdapter.setItems(it, diffCallback)
            }
            resetLines()
        })

        model.playlist.observe(this, Observer {
            displayPlaylist = it.isNotEmpty()
            playlistAdapter.setItems(it, diffCallback)
            resetLines()

        })
    }

    private fun resetLines() {
        val adapters = listOf(nowPlayingRow, videoRow, audioRow, playlistRow, historyRow, browsersRow, miscRow).filter {
            when {
                !displayHistory && it == historyRow -> false
                !displayPlaylist && it == playlistRow -> false
                !displayNowPlaying && it == nowPlayingRow -> false
                else -> true
            }

        }
        var needToRefresh = false
        if (adapters.size != rowsAdapter.size()) needToRefresh = true else
            adapters.withIndex().forEach {
                if ((rowsAdapter.get(it.index) as ListRow).headerItem != it.value.headerItem) {
                    needToRefresh = true
                    return@forEach
                }
            }
        if (needToRefresh) rowsAdapter.setItems(adapters, TvUtil.listDiffCallback)
    }

    override fun onStart() {
        super.onStart()
        if (selectedItem is AbstractMediaWrapper) updateBackground(requireContext(), backgroundManager, selectedItem)
        model.refresh()
    }

    override fun onStop() {
        super.onStop()
        if (AndroidDevices.isAndroidTv && !AndroidUtil.isOOrLater) requireActivity().startService(Intent(requireActivity(), RecommendationsService::class.java))
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    override fun onClick(v: View?) = requireActivity().startActivity(Intent(requireContext(), SearchActivity::class.java))

    fun showDetails(): Boolean {
        val media = selectedItem as? AbstractMediaWrapper ?: return false
        if (media.type != AbstractMediaWrapper.TYPE_DIR) return false
        val intent = Intent(requireActivity(), DetailsActivity::class.java)
        // pass the item information
        intent.putExtra("media", media)
        intent.putExtra("item", MediaItemDetails(media.title, media.artist, media.album, media.location, media.artworkURL))
        startActivity(intent)
        return true
    }

    override fun onItemClicked(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        val activity = requireActivity()
        when (row?.id) {
            HEADER_CATEGORIES -> {
                if ((item as DummyItem).id == CATEGORY_NOW_PLAYING) { //NOW PLAYING CARD
                    activity.startActivity(Intent(activity, AudioPlayerActivity::class.java))
                    return
                }
                val intent = Intent(activity, VerticalGridActivity::class.java)
                intent.putExtra(MainTvActivity.BROWSER_TYPE, HEADER_CATEGORIES)
                intent.putExtra(CATEGORY, item.id)
                activity.startActivity(intent)
            }
            HEADER_MISC -> {
                when ((item as GenericCardItem).id) {
                    ID_SETTINGS -> activity.startActivityForResult(Intent(activity, org.videolan.vlc.gui.tv.preferences.PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
                    ID_REFRESH -> {
                        if (!AbstractMedialibrary.getInstance().isWorking) {
                            requireActivity().reloadLibrary()
                        }
                    }
                    ID_ABOUT_TV -> activity.startActivity(Intent(activity, AboutActivity::class.java))
                    ID_LICENCE -> startActivity(Intent(activity, LicenceActivity::class.java))
                }
            }
            else -> {
                model.open(activity, item)
            }
        }
    }

    override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        selectedItem = item
        updateBackground(requireContext(), backgroundManager, item)
    }
}
