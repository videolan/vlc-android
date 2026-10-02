/*****************************************************************************
 * AudioBrowserFragment.java
 *
 * Copyright © 2011-2017 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 */

package org.videolan.vlc.gui.audio

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.waitForML
import org.videolan.tools.KEY_AUDIO_LAST_PLAYLIST
import org.videolan.tools.RESULT_RESTART
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.AudioBrowserBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.HeaderMediaListActivity
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.dialogs.CONFIRM_PERMISSION_CHANGED
import org.videolan.vlc.gui.dialogs.CURRENT_SORT
import org.videolan.vlc.gui.dialogs.DEFAULT_ACTIONS
import org.videolan.vlc.gui.dialogs.DISPLAY_IN_CARDS
import org.videolan.vlc.gui.dialogs.DisplaySettingsDialog
import org.videolan.vlc.gui.dialogs.KEY_PERMISSION_CHANGED
import org.videolan.vlc.gui.dialogs.ONLY_FAVS
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.DefaultPlaybackAction
import org.videolan.vlc.gui.helpers.DefaultPlaybackActionMediaType
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.UiTools.addFavoritesIcon
import org.videolan.vlc.gui.helpers.UiTools.removeDrawables
import org.videolan.vlc.interfaces.IListEventsHandler
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.providers.medialibrary.AudioMixerProvider
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.ContextOption.CTX_PLAY_ALL
import org.videolan.vlc.util.ContextOption.CTX_REMOVE_FROM_PLAYLIST
import org.videolan.vlc.util.FlagSet
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.isTalkbackIsEnabled
import org.videolan.vlc.util.launchWhenStarted
import org.videolan.vlc.util.onAnyChange
import org.videolan.vlc.viewmodels.PlaylistModel
import org.videolan.vlc.viewmodels.mobile.AudioBrowserViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel

class AudioBrowserFragment : BaseAudioBrowser<AudioBrowserViewModel>(), IListEventsHandler {

    private lateinit var binding: AudioBrowserBinding
    private lateinit var audioPagerAdapter: AudioPagerAdapter
    private lateinit var songsAdapter: AudioBrowserAdapter
    private lateinit var playlistAdapter: AudioBrowserAdapter
    private lateinit var mixerAdapter: AudioBrowserAdapter
    private lateinit var playlistModel: PlaylistModel
    private var mixerTouchHelper: ItemTouchHelper? = null
    private var refreshMixerControls: (() -> Unit)? = null
    private var playlistDisplayMode: ImageButton? = null

    private val lists = mutableListOf<RecyclerView>()
    private lateinit var settings: SharedPreferences
    override val hasTabs = false
    override fun hasFAB() = false
    private var spacing = 0
    private var restorePositions: SparseArray<Int> = SparseArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        spacing = requireActivity().resources.getDimension(R.dimen.kl_small).toInt()
        PlaylistManager.currentPlayedMedia.observe(this) {
            songsAdapter.currentMedia = it
        }

        if (!::settings.isInitialized) settings = Settings.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = AudioBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appbar = view.rootView.findViewById<AppBarLayout>(R.id.appbar)
        val coordinator = view.rootView.findViewById<CoordinatorLayout>(R.id.coordinator)
        val fab = view.rootView.findViewById<FloatingActionButton>(R.id.fab)
        binding.songsFastScroller.attachToCoordinator(appbar, coordinator, fab)
        binding.audioEmptyLoading.setOnNoMediaClickListener {
            if (currentTab == MIXER_TAB) {
                viewPager.currentItem = TRACKS_TAB
                UiTools.snacker(requireActivity(), R.string.audio_mixer_empty)
            } else requireActivity().setResult(RESULT_RESTART)
        }

        val views = ArrayList<View>(MODE_TOTAL)
        for (i in 0 until MODE_TOTAL) {
            viewPager.getChildAt(i).let {
                views.add(it)
                lists.add(it.findViewById(R.id.audio_list))
            }
        }
        val titles = arrayOf(getString(R.string.tracks), getString(R.string.playlists), getString(R.string.audio_mixer))
        viewPager.offscreenPageLimit = MODE_TOTAL - 1
        audioPagerAdapter = AudioPagerAdapter(views.toTypedArray(), titles)
        @Suppress("UNCHECKED_CAST")
        viewPager.adapter = audioPagerAdapter
        setupMixerControls(views[MIXER_TAB])
        savedInstanceState?.getIntegerArrayList(KEY_LISTS_POSITIONS)?.withIndex()?.forEach {
            restorePositions.put(it.index, it.value)
        }

        if (!::songsAdapter.isInitialized) setupModels()
        setupPlaylistControls(views[PLAYLISTS_TAB])
        if (viewModel.showResumeCard) {
            (requireActivity() as AudioPlayerContainerActivity).proposeCard()
            viewModel.showResumeCard = false
        }

        for (i in 0 until MODE_TOTAL) {
            @Suppress("UNCHECKED_CAST")
            setupLayoutManager(viewModel.providersInCard[i], lists[i], viewModel.providers[i] as MedialibraryProvider<MediaLibraryItem>, adapters[i], spacing)
            if (i == PLAYLISTS_TAB) setPlaylistListFullWidth()
            (lists[i].layoutManager as LinearLayoutManager).recycleChildrenOnDetach = true
            val list = lists[i]
            list.adapter = adapters[i]
            list.addOnScrollListener(scrollListener)
        }
        ItemTouchHelper(SwipeDragItemTouchHelperCallback(songsAdapter, swipeFlags = ItemTouchHelper.LEFT, swipeThreshold = 0.25f)).attachToRecyclerView(lists[TRACKS_TAB])
        mixerTouchHelper = ItemTouchHelper(SwipeDragItemTouchHelperCallback(mixerAdapter, true)).also { it.attachToRecyclerView(lists[MIXER_TAB]) }
        viewPager.setOnTouchListener { _, _ -> true }
        swipeRefreshLayout.setOnRefreshListener {
            (requireActivity() as ContentActivity).closeSearchView()
            viewModel.refresh()
        }
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                activity?.invalidateOptionsMenu()
            }
        })
        adapters.forEach {
            it.onAnyChange { updateEmptyView() }
        }
        requireActivity().supportFragmentManager.setFragmentResultListener(CONFIRM_PERMISSION_CHANGED, viewLifecycleOwner) { requestKey, bundle ->
            val changed = bundle.getBoolean(KEY_PERMISSION_CHANGED)
            if (changed) viewModel.refresh()
        }
        PlaylistManager.showAudioPlayer.observe(viewLifecycleOwner) {
            if (it == true && currentTab == MIXER_TAB) (activity as? AudioPlayerContainerActivity)?.hideAudioPlayer()
        }
    }

    override fun onDestroy() {
        (activity as? AudioPlayerContainerActivity)?.setMixerControlsAtNavigation(false)
        viewPager.setOnTouchListener(null)
        super.onDestroy()
    }

    override fun onDisplaySettingChanged(key: String, value: Any) {
        when (key) {
            DISPLAY_IN_CARDS -> {
                viewModel.providersInCard[currentTab] = value as Boolean
                @Suppress("UNCHECKED_CAST")
                setupLayoutManager(viewModel.providersInCard[currentTab], lists[currentTab], viewModel.providers[currentTab] as MedialibraryProvider<MediaLibraryItem>, adapters[currentTab], spacing)
                if (currentTab == PLAYLISTS_TAB) setPlaylistListFullWidth()
                lists[currentTab].adapter = adapters[currentTab]
                if (currentTab == TRACKS_TAB && songsAdapter.currentMedia != null) {
                    songsAdapter.currentMedia = null
                    songsAdapter.currentMedia = PlaylistManager.currentPlayedMedia.value
                }
                activity?.invalidateOptionsMenu()
                Settings.getInstance(requireActivity()).putSingle(viewModel.displayModeKeys[currentTab], value)
                if (currentTab == PLAYLISTS_TAB) updatePlaylistDisplayButton()
            }
            ONLY_FAVS -> {
                viewModel.providers[currentTab].showOnlyFavs(value as Boolean)
                viewModel.providers[currentTab].refresh()
                updateTabs()
            }
            CURRENT_SORT -> {
                lifecycleScope.launch {
                    displaySettingsViewModel.lockSorts(true)
                }
                @Suppress("UNCHECKED_CAST") val sort = value as Pair<Int, Boolean>
                viewModel.providers[currentTab].sort = sort.first
                viewModel.providers[currentTab].desc = sort.second
                viewModel.providers[currentTab].saveSort()
                viewModel.refresh()
            }
            DEFAULT_ACTIONS -> {
                settings.putSingle(getDefaultActionMediaType().defaultActionKey, (value as DefaultPlaybackAction).name)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        for (i in 0 until MODE_TOTAL) {
            if (i >= lists.size || i >= adapters.size) continue
            if (lists[i].layoutManager is GridLayoutManager) {
                val gridLayoutManager = lists[i].layoutManager as GridLayoutManager
                gridLayoutManager.spanCount = nbColumns
                lists[i].layoutManager = gridLayoutManager
                adapters[i].notifyItemRangeChanged(gridLayoutManager.findFirstVisibleItemPosition(), gridLayoutManager.findLastVisibleItemPosition() - gridLayoutManager.findFirstVisibleItemPosition())
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val positions = ArrayList<Int>(MODE_TOTAL)
        for (i in 0 until MODE_TOTAL) {
            positions.add((lists[i].layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition())
        }
        outState.putIntegerArrayList(KEY_LISTS_POSITIONS, positions)
        super.onSaveInstanceState(outState)
    }

    private fun setPlaylistListFullWidth() {
        if (viewModel.providersInCard[PLAYLISTS_TAB]) return
        lists[PLAYLISTS_TAB].layoutParams = lists[PLAYLISTS_TAB].layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    private fun setupModels() {
        viewModel = getViewModel()
        viewModel.currentTab = if (arguments?.getBoolean(EXTRA_OPEN_AUDIO_MIXER) == true) MIXER_TAB else TRACKS_TAB
        currentTab = viewModel.currentTab

        songsAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, this).apply { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
        playlistModel = PlaylistModel.get(this)
        songsAdapter.setModel(playlistModel)
        playlistModel.dataset.asFlow().conflate().onEach {
            songsAdapter.setCurrentlyPlaying(playlistModel.playing)
            delay(50L)
        }.launchWhenStarted(lifecycleScope)
        playlistAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_PLAYLIST, this, playlistStyle = true).apply { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
        mixerAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, this).apply { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
        mixerAdapter.currentMedia = PlaybackService.instance?.mixerMedia ?: AudioMixerProvider.selected(requireContext())
        adapters = arrayOf(songsAdapter, playlistAdapter, mixerAdapter)
        setupProvider()
    }

    private fun setupPlaylistControls(view: View) {
        view.findViewById<View>(R.id.playlist_sort).setOnClickListener { showDisplaySettings() }
        playlistDisplayMode = view.findViewById<ImageButton>(R.id.playlist_display_mode).apply {
            setOnClickListener { onDisplaySettingChanged(DISPLAY_IN_CARDS, !viewModel.providersInCard[PLAYLISTS_TAB]) }
        }
        updatePlaylistDisplayButton()
    }

    private fun updatePlaylistDisplayButton() {
        val inCards = viewModel.providersInCard[PLAYLISTS_TAB]
        playlistDisplayMode?.apply {
            setImageResource(if (inCards) R.drawable.ic_tv_browser_list else R.drawable.ic_tv_browser_grid)
            contentDescription = getString(if (inCards) R.string.display_in_list else R.string.display_in_grid)
        }
    }

    private fun showDisplaySettings() {
        val sorts = arrayListOf(Medialibrary.SORT_ALPHA, Medialibrary.SORT_FILENAME, Medialibrary.SORT_ARTIST, Medialibrary.SORT_ALBUM, Medialibrary.SORT_DURATION, Medialibrary.SORT_RELEASEDATE, Medialibrary.SORT_LASTMODIFICATIONDATE, Medialibrary.SORT_FILESIZE, Medialibrary.NbMedia, Medialibrary.SORT_INSERTIONDATE).filter {
            viewModel.providers[currentTab].canSortBy(it)
        }
        DisplaySettingsDialog.newInstance(
                displayInCards = viewModel.providersInCard[currentTab],
                showAllArtists = null,
                onlyFavs = viewModel.providers[currentTab].onlyFavorites,
                sorts = sorts,
                currentSort = viewModel.providers[currentTab].sort,
                currentSortDesc = viewModel.providers[currentTab].desc,
                defaultPlaybackActions = getDefaultActionMediaType().getDefaultPlaybackActions(settings),
                defaultActionType = getString(getDefaultActionMediaType().title)
        ).show(requireActivity().supportFragmentManager, "DisplaySettingsDialog")
    }

    private fun setupMixerControls(view: View) {
        val selected = view.findViewById<TextView>(R.id.audio_mixer_selected)
        val toggle = view.findViewById<MaterialButton>(R.id.audio_mixer_off)
        val loop = view.findViewById<MaterialButton>(R.id.audio_mixer_loop)
        val more = view.findViewById<ImageButton>(R.id.audio_mixer_more)
        val volume = view.findViewById<Slider>(R.id.audio_mixer_volume)
        val volumeValue = view.findViewById<TextView>(R.id.audio_mixer_volume_value)
        val volumeDown = view.findViewById<MaterialButton>(R.id.audio_mixer_volume_down)
        val volumeUp = view.findViewById<MaterialButton>(R.id.audio_mixer_volume_up)
        loop.isCheckable = true
        fun setVolume(value: Int, persist: Boolean = true) {
            val volumeInt = value.coerceIn(1, 100)
            volume.value = volumeInt.toFloat()
            volumeValue.text = "$volumeInt%"
            if (persist) PlaybackService.instance?.setAudioMixerVolume(volumeInt)
        }
        fun update() {
            val service = PlaybackService.instance
            val media = service?.mixerMedia ?: AudioMixerProvider.selected(requireContext())
            selected.text = media?.title?.let { getString(R.string.audio_mixer_selected_track, it) }
            selected.visibility = if (media == null) View.GONE else View.VISIBLE
            val mixerEnabled = service?.mixerEnabled == true
            toggle.setText(if (mixerEnabled) R.string.audio_mixer_on else R.string.audio_mixer_turn_off)
            toggle.isSelected = mixerEnabled
            toggle.setIconResource(if (mixerEnabled) R.drawable.ic_pause_player else R.drawable.ic_play)
            toggle.isEnabled = media != null
            loop.isChecked = service?.mixerLoop ?: true
            loop.isSelected = loop.isChecked
            loop.setIconResource(if (loop.isChecked) R.drawable.ic_repeat_all else R.drawable.ic_repeat)
            more.isEnabled = AudioMixerProvider.count(requireContext()) > 0
            setVolume(service?.mixerVolume ?: 50, false)
        }
        volume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) setVolume(value.toInt())
        }
        volumeDown.setOnClickListener { setVolume(volume.value.toInt() - 1) }
        volumeUp.setOnClickListener { setVolume(volume.value.toInt() + 1) }
        toggle.setOnClickListener {
            val media = PlaybackService.instance?.mixerMedia ?: AudioMixerProvider.selected(requireContext()) ?: return@setOnClickListener
            PlaybackService.instance?.setAudioMixerEnabled(PlaybackService.instance?.mixerEnabled != true) ?: startMixerService(media)
            update()
        }
        loop.addOnCheckedChangeListener { _, checked ->
            PlaybackService.instance?.setAudioMixerLoop(checked)
            update()
        }
        fun clearMixer() {
            val snapshot = AudioMixerProvider.snapshot(requireContext())
            AudioMixerProvider.clear(requireContext())
            PlaybackService.instance?.clearAudioMixer()
            mixerAdapter.currentMedia = null
            viewModel.mixerTracksProvider.refresh()
            updateTabs()
            update()
            UiTools.snackerConfirm(requireActivity(), getString(R.string.audio_mixer_cleared), confirmMessage = R.string.undo) {
                AudioMixerProvider.replace(requireContext(), snapshot)
                viewModel.mixerTracksProvider.refresh()
                updateTabs()
                update()
            }
        }
        more.setOnClickListener {
            PopupMenu(requireContext(), more).apply {
                menu.add(R.string.audio_mixer_clear).setOnMenuItemClickListener {
                    clearMixer()
                    true
                }
                show()
            }
        }
        refreshMixerControls = ::update
        update()
    }

    private fun setupProvider(index: Int = viewModel.currentTab) {
        val provider = viewModel.providers[index.coerceIn(0, viewModel.providers.size - 1)]
        if (provider.loading.hasObservers()) return
        provider.loading.observe(viewLifecycleOwner) { loading ->
            if (loading == null || currentTab != index) return@observe
            setRefreshing(loading) { refresh ->
                if (refresh) updateEmptyView()
                else {
                    swipeRefreshLayout.isEnabled = (getCurrentRV().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() <= 0
                    binding.songsFastScroller.setRecyclerView(getCurrentRV(), viewModel.providers[currentTab])
                }
            }
        }
        provider.liveHeaders.observe(viewLifecycleOwner) {
            lists[currentTab].invalidateItemDecorations()
        }
        lifecycleScope.launchWhenStarted {
            waitForML()
                provider.pagedList.observe(viewLifecycleOwner) { items ->
                    @Suppress("UNCHECKED_CAST")
                    if (items != null) adapters.getOrNull(index)?.submitList(items as PagedList<MediaLibraryItem>?)
                    updateEmptyView()
                    restorePositions.get(index)?.let {
                        lists[index].scrollToPosition(it)
                        restorePositions.delete(index)
                    }
                    setFabPlayShuffleAllVisibility(items.isNotEmpty())
                }
        }
    }

    override fun onStart() {
        super.onStart()
        setFabPlayVisibility(false)
        updateMainPlayerForTab()
    }

    override fun onDestroyView() {
        showMainPlayerIfNeeded()
        super.onDestroyView()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.ml_menu_last_playlist)?.isVisible = settings.contains(KEY_AUDIO_LAST_PLAYLIST)
        (viewModel.providers[currentTab]).run {
            menu.findItem(R.id.ml_menu_sortby).isVisible = false
            menu.findItem(R.id.ml_menu_display_options).isVisible = true
        }
        sortMenuTitles(currentTab)
        reopenSearchIfNeeded()
        menu.findItem(R.id.shuffle_all).isVisible = false
   }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.shuffle_all -> {
                onFabPlayClick(binding.audioEmptyLoading)
                true
            }
            R.id.ml_menu_display_options -> {
                showDisplaySettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun sortBy(sort: Int) {
        viewModel.providers[currentTab].sort(sort)
    }

    override fun onFabPlayClick(view: View) {
        MediaUtils.playAll(activity, viewModel.tracksProvider, 0, true)
    }

    private fun setFabPlayShuffleAllVisibility(force: Boolean = false) {
        setFabPlayVisibility(false)
    }

    override fun getTitle(): String = getString(R.string.music)

    override fun enableSearchOption() = true

    private fun updateEmptyView() {
        if (!isAdded) return
        swipeRefreshLayout.visibility = if (Medialibrary.getInstance().isInitiated) View.VISIBLE else View.GONE
        binding.audioEmptyLoading.emptyText = viewModel.filterQuery?.let {  getString(R.string.empty_search, it) } ?: if (viewModel.providers[currentTab].onlyFavorites) getString(R.string.nofav) else getString(R.string.nomedia)
        if (currentTab == MIXER_TAB && viewModel.filterQuery == null) binding.audioEmptyLoading.emptyText = getString(R.string.audio_mixer_empty)
        binding.audioEmptyLoading.state = when {
            !Permissions.canReadStorage(requireActivity()) && empty -> EmptyLoadingState.MISSING_PERMISSION
            !Permissions.canReadAudios(AppContextProvider.appContext) && empty -> EmptyLoadingState.MISSING_AUDIO_PERMISSION
            viewModel.providers[currentTab].loading.value == true && empty -> EmptyLoadingState.LOADING
            emptyAt(currentTab) && viewModel.providers[currentTab].onlyFavorites -> EmptyLoadingState.EMPTY_FAVORITES
            emptyAt(currentTab) && viewModel.filterQuery?.isNotEmpty() == true -> EmptyLoadingState.EMPTY_SEARCH
            emptyAt(currentTab) -> EmptyLoadingState.EMPTY
            else -> EmptyLoadingState.NONE

        }
    }

    override fun setupTabLayout() {
        updateTabs()
    }

    /**
     * Setup the tabs custom views
     *
     */
    private fun updateTabs() {
        if (!hasTabs || tabLayout == null) return
        for (i in 0 until tabLayout!!.tabCount) {
            val tab = tabLayout!!.getTabAt(i)
            val view = tab?.customView ?: View.inflate(requireActivity(), R.layout.audio_tab, null)
            val title = view.findViewById<TextView>(R.id.tab_title)
            val pageTitle = audioPagerAdapter.getPageTitle(i)
            title.text = if (i == MIXER_TAB) "$pageTitle (${AudioMixerProvider.count(requireContext())})" else pageTitle
            if (viewModel.providers[i].onlyFavorites) title.addFavoritesIcon() else title.removeDrawables()
            tab?.customView = view
        }
    }

    override fun onPageSelected(position: Int) {
        updateEmptyView()
        setFabPlayShuffleAllVisibility()
        if (position == MIXER_TAB) setFabPlayVisibility(false)
        updateMainPlayerForTab()
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        adapter = adapters[tab.position]
        viewModel.currentTab = tab.position
        setupProvider()
        super.onTabSelected(tab)
        updateMainPlayerForTab()
        if (tab.position == MIXER_TAB) setFabPlayVisibility(false)
        binding.songsFastScroller.setRecyclerView(lists[tab.position], viewModel.providers[tab.position])
        if (Medialibrary.getInstance().isInitiated) setRefreshing(viewModel.providers[currentTab].isRefreshing)
        activity?.invalidateOptionsMenu()
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        super.onTabUnselected(tab)
        onDestroyActionMode(lists[tab.position].adapter as AudioBrowserAdapter?)
        viewModel.restore()
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
        lists[tab.position].smoothScrollToPosition(0)
    }

    override fun onCtxAction(position: Int, option: ContextOption) {
        @Suppress("UNCHECKED_CAST")
        if (option == CTX_PLAY_ALL) MediaUtils.playAll(activity, viewModel.providers[currentTab] as MedialibraryProvider<MediaWrapper>, position, false)
        else if (currentTab == MIXER_TAB && option == CTX_REMOVE_FROM_PLAYLIST) {
            (mixerAdapter.getItem(position) as? MediaWrapper)?.let { removeMixerItem(it) }
        } else super.onCtxAction(position, option)
    }

    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {
        if (currentTab == MIXER_TAB && item is MediaWrapper) {
            showContext(requireActivity(), this, position, item, FlagSet(ContextOption::class.java).apply {
                add(CTX_REMOVE_FROM_PLAYLIST)
            })
        } else super.onCtxClick(v, position, item)
    }

    private val TAG = this::class.java.name
    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            super.onClick(v, position, item)
            return
        }
        if (inSearchMode()) UiTools.setKeyboardVisibility(v, false)
        if (currentTab == MIXER_TAB && item is MediaWrapper) {
            if (!item.isPresent) {
                UiTools.snackerMissing(requireActivity())
                return
            }
            AudioMixerProvider.select(requireContext(), item)
            PlaybackService.instance?.playInAudioMixer(item) ?: startMixerService(item)
            mixerAdapter.currentMedia = item
            refreshMixerControls?.invoke()
            return
        }
        if (item.itemType == MediaLibraryItem.TYPE_MEDIA) {
            if (item is MediaWrapper && !item.isPresent) {
                UiTools.snackerMissing(requireActivity())
                return
            }
            onMainActionClick(v, position, item)
            return
        }
        val i: Intent
        when (item.itemType) {
            MediaLibraryItem.TYPE_ARTIST, MediaLibraryItem.TYPE_GENRE -> {
                i = Intent(activity, SecondaryActivity::class.java)
                i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUMS_SONGS)
                i.putExtra(TAG_ITEM, item)
            }
            MediaLibraryItem.TYPE_ALBUM, MediaLibraryItem.TYPE_PLAYLIST -> {
                i = Intent(activity, HeaderMediaListActivity::class.java)
                i.putExtra(TAG_ITEM, item)
            }
            else -> return
        }
        startActivity(i)
    }

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {
        super.onUpdateFinished(adapter)
        lifecycleScope.launchWhenStarted { // force a dispatch
            if (adapter === getCurrentAdapter()) {
                swipeRefreshLayout.isEnabled = (getCurrentRV().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() <= 0
                binding.songsFastScroller.setRecyclerView(getCurrentRV(), viewModel.providers[currentTab])
            } else
                setFabPlayShuffleAllVisibility()
        }
    }

    override fun getCurrentRV() = lists[currentTab]

    override fun getDefaultActionMediaType() = if (currentTab == PLAYLISTS_TAB) DefaultPlaybackActionMediaType.PLAYLIST else DefaultPlaybackActionMediaType.TRACK

    override fun getCurrentProvider() = viewModel.providers[currentTab] as? MedialibraryProvider<MediaWrapper>

    override fun getCurrentAdapter() = adapters[currentTab]

    override fun allowedToExpand() = getCurrentRV().scrollState == RecyclerView.SCROLL_STATE_IDLE

    override fun onRemove(position: Int, item: MediaLibraryItem) {
        val media = item as? MediaWrapper ?: return
        if (currentTab == MIXER_TAB) removeMixerItem(media)
        else addSwipedTrackToPlaylist(position, media, songsAdapter)
    }

    override fun onMove(oldPosition: Int, newPosition: Int) {
        AudioMixerProvider.move(requireContext(), oldPosition, newPosition)
        viewModel.mixerTracksProvider.refresh()
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        mixerTouchHelper?.startDrag(viewHolder)
    }

    private fun removeMixerItem(media: MediaWrapper) {
        AudioMixerProvider.remove(requireContext(), media)
        PlaybackService.instance?.clearAudioMixer(media)
        if (mixerAdapter.currentMedia?.uri == media.uri) mixerAdapter.currentMedia = null
        viewModel.mixerTracksProvider.refresh()
        updateTabs()
        refreshMixerControls?.invoke()
        UiTools.snacker(requireActivity(), R.string.audio_mixer_removed)
    }

    private fun addSwipedTrackToPlaylist(position: Int, media: MediaWrapper, adapter: RecyclerView.Adapter<*>) {
        if (!media.isPresent) UiTools.snackerMissing(requireActivity())
        else requireActivity().addToPlaylist(media.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
        adapter.notifyItemChanged(position)
    }

    private fun startMixerService(media: MediaWrapper) {
        PlaybackService.start(requireContext())
        lifecycleScope.launch {
            PlaybackService.serviceFlow.filterNotNull().first().playInAudioMixer(media)
            refreshMixerControls?.invoke()
            if (currentTab == MIXER_TAB) setFabPlayVisibility(false)
        }
    }

    private fun updateMainPlayerForTab() {
        (activity as? AudioPlayerContainerActivity)?.setMixerControlsAtNavigation(currentTab == MIXER_TAB)
        if (currentTab == MIXER_TAB) (activity as? AudioPlayerContainerActivity)?.hideAudioPlayer()
        else showMainPlayerIfNeeded()
    }

    private fun showMainPlayerIfNeeded() {
        if (PlaybackService.instance?.hasMedia() == true) PlaylistManager.showAudioPlayer.value = true
    }

    companion object {
        const val TAG = "VLC/AudioBrowserFragment"

        private const val KEY_LISTS_POSITIONS = "key_lists_position"
        private const val MODE_TOTAL = 3
        private const val TRACKS_TAB = 0
        private const val PLAYLISTS_TAB = 1
        private const val MIXER_TAB = 2
        const val EXTRA_OPEN_AUDIO_MIXER = "extra_open_audio_mixer"

        const val TAG_ITEM = "ML_ITEM"
    }
}
