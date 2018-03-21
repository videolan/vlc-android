package org.videolan.vlc.gui.tv

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v17.leanback.app.BackgroundManager
import android.support.v17.leanback.app.BrowseSupportFragment
import android.support.v17.leanback.widget.*
import android.support.v4.content.ContextCompat
import android.view.View
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.R
import org.videolan.vlc.RecommendationsService
import org.videolan.vlc.gui.preferences.PreferencesFragment
import org.videolan.vlc.gui.tv.MainTvActivity.ACTIVITY_RESULT_PREFERENCES
import org.videolan.vlc.gui.tv.MainTvActivity.BROWSER_TYPE
import org.videolan.vlc.gui.tv.TvUtil.diffCallback
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity
import org.videolan.vlc.gui.tv.browser.VerticalGridActivity
import org.videolan.vlc.media.MediaDatabase
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Constants
import org.videolan.vlc.viewmodels.HistoryProvider
import org.videolan.vlc.viewmodels.VideosProvider

private const val NUM_ITEMS_PREVIEW = 5
private const val TAG = "VLC/MainTvFragment"

class MainTvFragment : BrowseSupportFragment(), OnItemViewSelectedListener, OnItemViewClickedListener, View.OnClickListener {

    private var backgroundManager: BackgroundManager? = null
    private lateinit var videoProvider: VideosProvider
    private lateinit var historyProvider: HistoryProvider
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var videoAdapter: ArrayObjectAdapter
    private lateinit var categoriesAdapter: ArrayObjectAdapter
    private lateinit var historyAdapter: ArrayObjectAdapter
    private lateinit var browserAdapter: ArrayObjectAdapter
    private lateinit var otherAdapter: ArrayObjectAdapter
    private lateinit var settings: SharedPreferences
    private var selectedItem: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = PreferenceManager.getDefaultSharedPreferences(requireContext())
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

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireActivity()
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        // Video
        videoAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val videoHeader = HeaderItem(0, getString(R.string.video))
        rowsAdapter.add(ListRow(videoHeader, videoAdapter))
        // Audio
        categoriesAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val musicHeader = HeaderItem(Constants.HEADER_CATEGORIES, getString(R.string.audio))
        categoriesAdapter.add(DummyItem(Constants.CATEGORY_ARTISTS, getString(R.string.artists), "Hello"))
        categoriesAdapter.add(DummyItem(Constants.CATEGORY_ALBUMS, getString(R.string.albums), ""))
        categoriesAdapter.add(DummyItem(Constants.CATEGORY_GENRES, getString(R.string.genres), ""))
        categoriesAdapter.add(DummyItem(Constants.CATEGORY_SONGS, getString(R.string.songs), ""))
        rowsAdapter.add(ListRow(musicHeader, categoriesAdapter))
        //History
        val showHistory = settings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true)
        if (showHistory) {
            historyAdapter = ArrayObjectAdapter(CardPresenter(ctx))
            val historyHeader = HeaderItem(Constants.HEADER_HISTORY, getString(R.string.history))
            rowsAdapter.add(ListRow(historyHeader, historyAdapter))
        }
        //Browser section
        browserAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val browserHeader = HeaderItem(Constants.HEADER_NETWORK, getString(R.string.browsing))
        updateBrowsers()
        rowsAdapter.add(ListRow(browserHeader, browserAdapter))
        //Misc. section
        otherAdapter = ArrayObjectAdapter(CardPresenter(ctx))
        val miscHeader = HeaderItem(Constants.HEADER_MISC, getString(R.string.other))

        otherAdapter.add(DummyItem(Constants.ID_SETTINGS, getString(R.string.preferences), ""))
        otherAdapter.add(DummyItem(Constants.ID_ABOUT_TV, getString(R.string.about), "${getString(R.string.app_name_full)} ${BuildConfig.VERSION_NAME}"))
        otherAdapter.add(DummyItem(Constants.ID_LICENCE, getString(R.string.licence), ""))
        rowsAdapter.add(ListRow(miscHeader, otherAdapter))

        adapter = rowsAdapter
        setupProviders(showHistory)
        onItemViewClickedListener = this
        onItemViewSelectedListener = this
    }

    override fun onStart() {
        super.onStart()
        historyProvider.refresh()
        if (selectedItem is MediaWrapper) TvUtil.updateBackground(backgroundManager, selectedItem)
    }

    override fun onStop() {
        super.onStop()
        if (AndroidDevices.isAndroidTv && !AndroidUtil.isOOrLater) requireActivity().startService(Intent(requireActivity(), RecommendationsService::class.java))
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

    fun updateBrowsers() {
        val list = mutableListOf<MediaLibraryItem>()
        val directories = AndroidDevices.getMediaDirectoriesList()
        if (!AndroidDevices.showInternalStorage && !directories.isEmpty()) directories.removeAt(0)
        for (directory in directories) list.add(directory)

        if (ExternalMonitor.isLan()) {
            try {
                val favs = MediaDatabase.getInstance().allNetworkFav
                list.add(DummyItem(Constants.HEADER_NETWORK, getString(R.string.network_browsing), null))
                list.add(DummyItem(Constants.HEADER_STREAM, getString(R.string.open_mrl), null))

                if (!favs.isEmpty()) {
                    for (fav in favs) {
                        fav.description = fav.uri.scheme
                        list.add(fav)
                    }
                }
            } catch (ignored: Exception) {} //SQLite can explode
        }
        browserAdapter.setItems(list, diffCallback)
    }

    // TODO
//    fun updateNowPlayingCard() = launch(UI, CoroutineStart.UNDISPATCHED) {
//        if (mService == null) return
//        val hasmedia = mService.hasMedia()
//        val canSwitch = mService.canSwitchToVideo()
//        if ((!hasmedia || canSwitch) && mNowPlayingCard != null) {
//            mCategoriesAdapter.removeItems(0, 1)
//            mNowPlayingCard = null
//        } else if (hasmedia && !canSwitch) {
//            val mw = mService.getCurrentMediaWrapper()
//            val display = MediaUtils.getMediaTitle(mw) + " - " + MediaUtils.getMediaReferenceArtist(this@MainTvActivity, mw)
//            val cover = withContext(CommonPool) {AudioUtil.readCoverBitmap(Uri.decode(mw.getArtworkMrl()), VLCApplication.getAppResources().getDimensionPixelSize(R.dimen.grid_card_thumb_width)) }
//            if (mNowPlayingCard == null) {
//                mNowPlayingCard = if (cover != null)
//                    CardPresenter.SimpleCard(MusicFragment.CATEGORY_NOW_PLAYING.toLong(), display, cover)
//                else
//                    CardPresenter.SimpleCard(MusicFragment.CATEGORY_NOW_PLAYING.toLong(), display, R.drawable.ic_default_cone)
//                mCategoriesAdapter.add(0, mNowPlayingCard)
//            } else {
//                mNowPlayingCard.setId(MusicFragment.CATEGORY_NOW_PLAYING.toLong())
//                mNowPlayingCard.setName(display)
//                if (cover != null) mNowPlayingCard.setImage(cover)
//                else mNowPlayingCard.setImageId(R.drawable.ic_default_cone)
//            }
//            mCategoriesAdapter.notifyArrayItemRangeChanged(0, 1)
//        }
//    }

    override fun onItemClicked(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        val activity = requireActivity()
        when(row?.id) {
            Constants.HEADER_CATEGORIES -> {
                if ((item as DummyItem).id == Constants.CATEGORY_NOW_PLAYING) { //NOW PLAYING CARD
                    activity.startActivity(Intent(activity, AudioPlayerActivity::class.java))
                    return
                }
                val intent = Intent(activity, VerticalGridActivity::class.java)
                intent.putExtra(BROWSER_TYPE, Constants.HEADER_CATEGORIES)
                intent.putExtra(Constants.AUDIO_CATEGORY, item.id)
                activity.startActivity(intent)
            }
            Constants.HEADER_MISC -> {
                val id = (item as DummyItem).id
                when (id) {
                    Constants.ID_SETTINGS -> activity.startActivityForResult(Intent(activity, org.videolan.vlc.gui.tv.preferences.PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
                    Constants.ID_ABOUT_TV -> activity.startActivity(Intent(activity, org.videolan.vlc.gui.tv.AboutActivity::class.java))
                    Constants.ID_LICENCE -> startActivity(Intent(activity, org.videolan.vlc.gui.tv.LicenceActivity::class.java))
                }
            }
            else -> TvUtil.openMedia(activity, item, row)
        }
    }

    override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        selectedItem = item
        TvUtil.updateBackground(backgroundManager, item)
    }

    //TODO video groups
    private fun setupProviders(showHistory: Boolean) {
        videoProvider = VideosProvider.get(this, null, 0, Medialibrary.SORT_INSERTIONDATE)
        videoProvider.dataset.observe(this, Observer {
            it?.let {
                val list = mutableListOf<Any>()
                list.add(DummyItem(Constants.HEADER_VIDEO, "All videos", "${it.size} ${getString(R.string.videos)}"))
                // Update video section
                if (!it.isEmpty()) {
                    for ((index, video) in it.withIndex()) {
                        if (index == NUM_ITEMS_PREVIEW) break
                        Tools.setMediaDescription(video)
                        list.add(video)
                    }
                }
                videoAdapter.setItems(list, diffCallback)
            }
            (requireActivity() as MainTvActivity).hideLoading()
        })
        if (showHistory) {
            historyProvider = ViewModelProviders.of(this).get(HistoryProvider::class.java)
            historyProvider.dataset.observe(this, Observer { historyAdapter.setItems(it!!, diffCallback) })
        }
        ExternalMonitor.connected.observe(this, Observer { updateBrowsers() })
    }
}