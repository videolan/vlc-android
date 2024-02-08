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

package org.videolan.television.ui

//import org.videolan.vlc.donations.BillingStatus
//import org.videolan.vlc.donations.VLCBilling
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.resources.AndroidDevices
import org.videolan.resources.CATEGORY
import org.videolan.resources.CATEGORY_NOW_PLAYING
import org.videolan.resources.CATEGORY_NOW_PLAYING_PIP
import org.videolan.resources.HEADER_CATEGORIES
import org.videolan.resources.HEADER_HISTORY
import org.videolan.resources.HEADER_MISC
import org.videolan.resources.HEADER_NETWORK
import org.videolan.resources.HEADER_NOW_PLAYING
import org.videolan.resources.HEADER_PLAYLISTS
import org.videolan.resources.HEADER_RECENTLY_ADDED
import org.videolan.resources.HEADER_RECENTLY_PLAYED
import org.videolan.resources.HEADER_VIDEO
import org.videolan.resources.ID_ABOUT_TV
import org.videolan.resources.ID_PIN_LOCK
import org.videolan.resources.ID_REFRESH
import org.videolan.resources.ID_REMOTE_ACCESS
import org.videolan.resources.ID_SETTINGS
import org.videolan.resources.ID_SPONSOR
import org.videolan.television.ui.TvUtil.diffCallback
import org.videolan.television.ui.TvUtil.metadataDiffCallback
import org.videolan.television.ui.audioplayer.AudioPlayerActivity
import org.videolan.television.ui.browser.VerticalGridActivity
import org.videolan.television.ui.preferences.PreferencesActivity
import org.videolan.television.viewmodel.MainTvModel
import org.videolan.television.viewmodel.MainTvModel.Companion.getMainTvModel
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.RecommendationsService
import org.videolan.vlc.StartActivity
import org.videolan.vlc.gui.helpers.UiTools.showDonations
import org.videolan.vlc.gui.helpers.hf.PinCodeDelegate
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.Permissions

private const val TAG = "VLC/MainTvFragment"

class MainTvFragment : BrowseSupportFragment(), OnItemViewSelectedListener, OnItemViewClickedListener,
        View.OnClickListener {

    private var backgroundManager: BackgroundManager? = null
    private lateinit var rowsAdapter: ArrayObjectAdapter

    private lateinit var nowPlayingAdapter: ArrayObjectAdapter
    private lateinit var recentlyPlayedAdapter: ArrayObjectAdapter
    private lateinit var recentlyAddedAdapter: ArrayObjectAdapter
    private lateinit var videoAdapter: ArrayObjectAdapter
    private lateinit var categoriesAdapter: ArrayObjectAdapter
    private lateinit var historyAdapter: ArrayObjectAdapter
    private lateinit var playlistAdapter: ArrayObjectAdapter
    private lateinit var favoritesAdapter: ArrayObjectAdapter
    private lateinit var browserAdapter: ArrayObjectAdapter
    private lateinit var otherAdapter: ArrayObjectAdapter

    private lateinit var nowPlayingRow: ListRow
    private lateinit var recentlyPlayedRow: ListRow
    private lateinit var recentlyAdddedRow: ListRow
    private lateinit var videoRow: ListRow
    private lateinit var audioRow: ListRow
    private lateinit var historyRow: ListRow
    private lateinit var playlistRow: ListRow
    private lateinit var favoritesRow: ListRow
    private lateinit var browsersRow: ListRow
    private lateinit var miscRow: ListRow

    private var displayHistory = false
    private var displayPlaylist = false
    private var displayNowPlaying = false
    private var displayRecentlyPlayed = false
    private var displayRecentlyAdded = false
    private var displayFavorites = false
    private var selectedItem: Any? = null

    private var lines: Int = 7
    private var loadedLines = ArrayList<Long>()

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
        val nowPlayingHeader = HeaderItem(HEADER_NOW_PLAYING, getString(R.string.music_now_playing))
        nowPlayingRow = ListRow(nowPlayingHeader, nowPlayingAdapter)
        rowsAdapter.add(nowPlayingRow)
        //Recently played
        recentlyPlayedAdapter = ArrayObjectAdapter(MetadataCardPresenter(ctx))
        val recentlyPlayedHeader = HeaderItem(HEADER_RECENTLY_PLAYED, getString(R.string.recently_played))
        recentlyPlayedRow = ListRow(recentlyPlayedHeader, recentlyPlayedAdapter)
        rowsAdapter.add(recentlyPlayedRow)
        //Recently added
        recentlyAddedAdapter = ArrayObjectAdapter(MetadataCardPresenter(ctx))
        val recentlyAddedHeader = HeaderItem(HEADER_RECENTLY_ADDED, getString(R.string.recently_added))
        recentlyAdddedRow = ListRow(recentlyAddedHeader, recentlyAddedAdapter)
        rowsAdapter.add(recentlyAdddedRow)
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

        favoritesAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val favoritesHeader = HeaderItem(HEADER_PLAYLISTS, getString(R.string.favorites))
        favoritesRow = ListRow(favoritesHeader, favoritesAdapter)

        //Browser section
        browserAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val browserHeader = HeaderItem(HEADER_NETWORK, getString(R.string.browsing))
        browsersRow = ListRow(browserHeader, browserAdapter)
        rowsAdapter.add(browsersRow)
        //Misc. section
        otherAdapter = ArrayObjectAdapter(GenericCardPresenter(ctx))
        val miscHeader = HeaderItem(HEADER_MISC, getString(R.string.other))

        val lockItem = GenericCardItem(ID_PIN_LOCK, getString(R.string.lock_with_pin_short), "", R.drawable.ic_pin_lock_big, R.color.tv_card_content_dark)
        if (PinCodeDelegate.pinUnlocked.value == true) otherAdapter.add(lockItem)
        otherAdapter.add(GenericCardItem(ID_SETTINGS, getString(R.string.preferences), "", R.drawable.ic_settings_big, R.color.tv_card_content_dark))
        val remoteAccessCard = GenericCardItem(ID_REMOTE_ACCESS, getString(R.string.remote_access), "", R.drawable.ic_remote_access_big, R.color.tv_card_content_dark)
        Settings.remoteAccessEnabled.observe(requireActivity()) {
            if (it)
                otherAdapter.add(otherAdapter.size() - 2, remoteAccessCard)
            else
                otherAdapter.remove(remoteAccessCard)
        }
        if (Permissions.canReadStorage(requireActivity())) otherAdapter.add(GenericCardItem(ID_REFRESH, getString(R.string.refresh), "", R.drawable.ic_scan_big, R.color.tv_card_content_dark))
        otherAdapter.add(GenericCardItem(ID_ABOUT_TV, getString(R.string.about), "${getString(R.string.app_name_full)} ${BuildConfig.VLC_VERSION_NAME}", R.drawable.ic_info_big, R.color.tv_card_content_dark))
        val donateCard = GenericCardItem(ID_SPONSOR, getString(R.string.tip_jar), "", R.drawable.ic_donate_big, R.color.tv_card_content_dark)

//        VLCBilling.getInstance(requireActivity().application).addStatusListener {
//            manageDonationVisibility(donateCard)
//        }

        PinCodeDelegate.pinUnlocked.observe(requireActivity()) {
            if (it) {
                if ((otherAdapter.get(0) as GenericCardItem).id != ID_PIN_LOCK) {
                    otherAdapter.add(0, lockItem)
                }
            } else {
                if ((otherAdapter.get(0) as GenericCardItem).id == ID_PIN_LOCK) {
                    otherAdapter.removeItems(0, 1)
                }
            }
        }
        manageDonationVisibility(donateCard)
        miscRow = ListRow(miscHeader, otherAdapter)
        rowsAdapter.add(miscRow)

        historyAdapter = ArrayObjectAdapter(CardPresenter(requireActivity(), fromHistory = true))
        val historyHeader = HeaderItem(HEADER_HISTORY, getString(R.string.history))
        historyRow = ListRow(historyHeader, historyAdapter)

        adapter = rowsAdapter
        onItemViewClickedListener = this
        onItemViewSelectedListener = this
        // ViewModel setup
        registerDatasets()
    }

    private fun manageDonationVisibility(donateCard: GenericCardItem) {
        if (activity == null) return
        otherAdapter.remove(donateCard)
//        if (VLCBilling.getInstance(requireActivity().application).status != BillingStatus.FAILURE && VLCBilling.getInstance(requireActivity().application).skuDetails.isNotEmpty()) otherAdapter.add(1, donateCard)
    }

    private fun registerDatasets() {
        model.browsers.observe(requireActivity()) {
            browserAdapter.setItems(it, diffCallback)
            addAndCheckLoadedLines(HEADER_NETWORK)
        }
        model.favoritesList.observe(requireActivity()) {
            displayFavorites = it.isNotEmpty()
            favoritesAdapter.setItems(it, diffCallback)
        }
        model.audioCategories.observe(requireActivity()) {
            categoriesAdapter.setItems(it.toList(), diffCallback)
            addAndCheckLoadedLines(HEADER_CATEGORIES)
        }
        model.videos.observe(requireActivity()) {
            videoAdapter.setItems(it, diffCallback)
            addAndCheckLoadedLines(HEADER_VIDEO)
        }
        model.nowPlaying.observe(requireActivity()) {
            displayNowPlaying = it.isNotEmpty()
            nowPlayingAdapter.setItems(it, diffCallback)
            addAndCheckLoadedLines(HEADER_NOW_PLAYING)
        }
        model.recentlyPlayed.observe(requireActivity()) {
            displayRecentlyPlayed = it.isNotEmpty()
            recentlyPlayedAdapter.setItems(it, metadataDiffCallback)
            resetLines()
            addAndCheckLoadedLines(HEADER_RECENTLY_PLAYED)
        }
        model.recentlyAdded.observe(requireActivity()) {
            displayRecentlyAdded = it.isNotEmpty()
            recentlyAddedAdapter.setItems(it, metadataDiffCallback)
            resetLines()
            addAndCheckLoadedLines(HEADER_RECENTLY_ADDED)
        }
        model.history.observe(requireActivity()) {
            displayHistory = it.isNotEmpty()
            if (it.isNotEmpty()) {
                historyAdapter.setItems(it, diffCallback)
            }
            resetLines()
            addAndCheckLoadedLines(HEADER_HISTORY)
        }

        model.playlist.observe(requireActivity()) {
            displayPlaylist = it.isNotEmpty()
            playlistAdapter.setItems(it, diffCallback)
            resetLines()
            addAndCheckLoadedLines(HEADER_PLAYLISTS)

        }
    }

    /**
     * Used for initial selection. Here we wait that all the lines have been loaded and when done, we force the selection to the first item
     * It's done that way to prevent the default selection that falls back to the first item that cannot be hidden (Videos lines)
     */
    private fun addAndCheckLoadedLines(header: Long) {
        if (!loadedLines.contains(header)) loadedLines.add(header)
        if (lines == loadedLines.size) {
            selectedPosition = 0
            lines = -1
        }
    }

    private fun resetLines() {
        val adapters = listOf(nowPlayingRow, recentlyPlayedRow, recentlyAdddedRow, videoRow, audioRow, playlistRow, historyRow, favoritesRow, browsersRow, miscRow).filter {
            when {
                !displayRecentlyPlayed && it == recentlyPlayedRow -> false
                !displayRecentlyAdded && it == recentlyAdddedRow -> false
                !displayHistory && it == historyRow -> false
                !displayPlaylist && it == playlistRow -> false
                !displayNowPlaying && it == nowPlayingRow -> false
                !displayFavorites && it == favoritesRow -> false
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
        if (selectedItem is MediaWrapper) lifecycleScope.updateBackground(requireActivity(), backgroundManager, selectedItem)
        model.refresh()
    }

    override fun onStop() {
        super.onStop()
        if (AndroidDevices.isAndroidTv && !AndroidUtil.isOOrLater) requireActivity().startService(Intent(requireActivity(), RecommendationsService::class.java))
    }

    override fun onClick(v: View?) = requireActivity().startActivity(Intent(requireContext(), SearchActivity::class.java))

    fun showDetails(): Boolean {
        val media = selectedItem as? MediaWrapper
                ?: return false
        if (media.type != MediaWrapper.TYPE_DIR) return false
        val intent = Intent(requireActivity(), DetailsActivity::class.java)
        // pass the item information
        intent.putExtra(EXTRA_MEDIA, media)
        intent.putExtra(EXTRA_ITEM, MediaItemDetails(media.title, media.artist, media.album, media.location, media.artworkURL))
        startActivity(intent)
        return true
    }

    override fun onItemClicked(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        val activity = requireActivity()
        when (row?.id) {
            HEADER_CATEGORIES -> {
                val intent = Intent(activity, VerticalGridActivity::class.java)
                intent.putExtra(MainTvActivity.BROWSER_TYPE, HEADER_CATEGORIES)
                intent.putExtra(CATEGORY, (item as DummyItem).id)
                activity.startActivity(intent)
            }
            HEADER_MISC -> {
                when ((item as GenericCardItem).id) {
                    ID_SETTINGS -> activity.startActivityForResult(Intent(activity, PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
                    ID_REFRESH -> {
                        if (!Medialibrary.getInstance().isWorking) {
                            requireActivity().reloadLibrary()
                        }
                    }
                    ID_ABOUT_TV -> activity.startActivity(Intent(activity, AboutActivity::class.java))
                    ID_SPONSOR -> activity.showDonations()
                    ID_PIN_LOCK -> PinCodeDelegate.pinUnlocked.postValue(false)
                    ID_REMOTE_ACCESS -> requireActivity().startActivity(Intent(activity, StartActivity::class.java).apply { action = "vlc.remoteaccess.share" })

                }
            }
            HEADER_NOW_PLAYING -> {
                if ((item as DummyItem).id == CATEGORY_NOW_PLAYING) { //NOW PLAYING CARD
                    activity.startActivity(Intent(activity, AudioPlayerActivity::class.java))
                } else if (item.id == CATEGORY_NOW_PLAYING_PIP) { //NOW PLAYING CARD in PiP Mode
                    activity.startActivity(Intent(activity, VideoPlayerActivity::class.java))
                }
            }
            else -> {
                model.open(activity, item)
            }
        }
    }

    override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        selectedItem = item
        lifecycleScope.updateBackground(requireActivity(), backgroundManager, item)
    }
}
