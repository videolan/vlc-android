/*****************************************************************************
 * VideoGridFragment.kt
 *
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.video

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.paging.InitialPagedList
import androidx.paging.PagedList
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.EventTools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.FolderImpl
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider
import org.videolan.resources.GROUP_VIDEOS_FOLDER
import org.videolan.resources.GROUP_VIDEOS_NAME
import org.videolan.resources.GROUP_VIDEOS_NONE
import org.videolan.resources.KEY_FOLDER
import org.videolan.resources.KEY_GROUP
import org.videolan.resources.KEY_GROUPING
import org.videolan.resources.MOVIEPEDIA_ACTIVITY
import org.videolan.resources.MOVIEPEDIA_MEDIA
import org.videolan.resources.PLAYLIST_TYPE_VIDEO
import org.videolan.resources.UPDATE_SEEN
import org.videolan.resources.util.parcelable
import org.videolan.resources.util.parcelableArray
import org.videolan.resources.util.waitForML
import org.videolan.tools.KEY_CASTING_AUDIO_ONLY
import org.videolan.tools.KEY_GROUP_VIDEOS
import org.videolan.tools.KEY_MEDIA_LAST_PLAYLIST
import org.videolan.tools.KEY_MEDIA_SEEN
import org.videolan.tools.KEY_VIDEOS_CARDS
import org.videolan.tools.MultiSelectHelper
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.RESULT_RESTART
import org.videolan.tools.Settings
import org.videolan.tools.dp
import org.videolan.tools.isStarted
import org.videolan.tools.putSingle
import org.videolan.tools.retrieveParent
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VideoGridBinding
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.dialogs.AddToGroupDialog
import org.videolan.vlc.gui.dialogs.CONFIRM_ADD_TO_GROUP_RESULT
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_BAN_FOLDER
import org.videolan.vlc.gui.dialogs.CONFIRM_PERMISSION_CHANGED
import org.videolan.vlc.gui.dialogs.CONFIRM_RENAME_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.CURRENT_SORT
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.DEFAULT_ACTIONS
import org.videolan.vlc.gui.dialogs.DISPLAY_IN_CARDS
import org.videolan.vlc.gui.dialogs.DisplaySettingsDialog
import org.videolan.vlc.gui.dialogs.KEY_PERMISSION_CHANGED
import org.videolan.vlc.gui.dialogs.ONLY_FAVS
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_MEDIA
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_NEW_NAME
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.VIDEO_GROUPING
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.AudioUtil.setRingtone
import org.videolan.vlc.gui.helpers.DefaultPlaybackAction
import org.videolan.vlc.gui.helpers.DefaultPlaybackActionMediaType
import org.videolan.vlc.gui.helpers.ItemOffsetDecoration
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToGroup
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.UiTools.createShortcut
import org.videolan.vlc.gui.helpers.UiTools.showPinIfNeeded
import org.videolan.vlc.gui.helpers.fillActionMode
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.getAll
import org.videolan.vlc.providers.medialibrary.VideosProvider
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.ContextOption.CTX_ADD_GROUP
import org.videolan.vlc.util.ContextOption.CTX_ADD_SHORTCUT
import org.videolan.vlc.util.ContextOption.CTX_ADD_TO_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_APPEND
import org.videolan.vlc.util.ContextOption.CTX_BAN_FOLDER
import org.videolan.vlc.util.ContextOption.CTX_DELETE
import org.videolan.vlc.util.ContextOption.CTX_DOWNLOAD_SUBTITLES
import org.videolan.vlc.util.ContextOption.CTX_FAV_ADD
import org.videolan.vlc.util.ContextOption.CTX_FAV_REMOVE
import org.videolan.vlc.util.ContextOption.CTX_FIND_METADATA
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_FOLDER
import org.videolan.vlc.util.ContextOption.CTX_GROUP_SIMILAR
import org.videolan.vlc.util.ContextOption.CTX_INFORMATION
import org.videolan.vlc.util.ContextOption.CTX_MARK_ALL_AS_PLAYED
import org.videolan.vlc.util.ContextOption.CTX_MARK_ALL_AS_UNPLAYED
import org.videolan.vlc.util.ContextOption.CTX_MARK_AS_PLAYED
import org.videolan.vlc.util.ContextOption.CTX_MARK_AS_UNPLAYED
import org.videolan.vlc.util.ContextOption.CTX_PLAY
import org.videolan.vlc.util.ContextOption.CTX_PLAY_ALL
import org.videolan.vlc.util.ContextOption.CTX_PLAY_AS_AUDIO
import org.videolan.vlc.util.ContextOption.CTX_PLAY_FROM_START
import org.videolan.vlc.util.ContextOption.CTX_PLAY_NEXT
import org.videolan.vlc.util.ContextOption.CTX_REMOVE_GROUP
import org.videolan.vlc.util.ContextOption.CTX_RENAME_GROUP
import org.videolan.vlc.util.ContextOption.CTX_SET_RINGTONE
import org.videolan.vlc.util.ContextOption.CTX_SHARE
import org.videolan.vlc.util.ContextOption.CTX_UNGROUP
import org.videolan.vlc.util.ContextOption.Companion.createCtxFolderFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxVideoFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxVideoGroupFlags
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.isMissing
import org.videolan.vlc.util.isTalkbackIsEnabled
import org.videolan.vlc.util.launchWhenStarted
import org.videolan.vlc.util.onAnyChange
import org.videolan.vlc.util.share
import org.videolan.vlc.util.showParentFolder
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType
import org.videolan.vlc.viewmodels.mobile.VideosViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel

private const val TAG = "VLC/VideoListFragment"

class VideoGridFragment : MediaBrowserFragment<VideosViewModel>(), SwipeRefreshLayout.OnRefreshListener, CtxActionReceiver {

    private lateinit var dataObserver: RecyclerView.AdapterDataObserver
    private lateinit var videoListAdapter: VideoListAdapter
    private lateinit var multiSelectHelper: MultiSelectHelper<MediaLibraryItem>
    private lateinit var binding: VideoGridBinding
    private var gridItemDecoration: RecyclerView.ItemDecoration? = null
    private lateinit var settings: SharedPreferences
    //in case of fragment being hosted by other fragments, it's useful to prevent the
    //FAB visibility to be locked hidden
    override val isMainNavigationPoint = false
    override val hasTabs: Boolean
        get() = parentFragment != null

    private fun FragmentActivity.open(item: MediaLibraryItem) {
        val i = Intent(activity, SecondaryActivity::class.java)
        i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.VIDEO_GROUP_LIST)
        if (item is Folder) i.putExtra(KEY_FOLDER, item)
        else if (item is VideoGroup) i.putExtra(KEY_GROUP, item)
        startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!::settings.isInitialized) settings = Settings.getInstance(requireContext())
        if (!::videoListAdapter.isInitialized) {
            val seenMarkVisible = settings.getBoolean(KEY_MEDIA_SEEN, true) && settings.getBoolean(PLAYBACK_HISTORY, true)
            videoListAdapter = VideoListAdapter(seenMarkVisible, !settings.getBoolean(PLAYBACK_HISTORY, true)).apply { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
            dataObserver = videoListAdapter.onAnyChange {
                updateEmptyView()
                if (::binding.isInitialized) binding.fastScroller.setRecyclerView(binding.videoGrid, viewModel.provider)
            }
            multiSelectHelper = videoListAdapter.multiSelectHelper
            val folder = if (savedInstanceState != null) savedInstanceState.parcelable<Folder>(KEY_FOLDER)
            else arguments?.parcelable(KEY_FOLDER)
            val parentGroup = if (savedInstanceState != null) savedInstanceState.parcelable<VideoGroup>(KEY_GROUP)
                    else arguments?.parcelable(KEY_GROUP)
            val grouping = if (parentGroup != null || folder != null) VideoGroupingType.NONE else when (Settings.getInstance(requireContext()).getString(KEY_GROUP_VIDEOS, null) ?: GROUP_VIDEOS_NAME) {
                GROUP_VIDEOS_NONE -> VideoGroupingType.NONE
                GROUP_VIDEOS_FOLDER -> VideoGroupingType.FOLDER
                else -> VideoGroupingType.NAME
            }
            viewModel = getViewModel(grouping, folder, parentGroup)
            setDataObservers()
            EventTools.getInstance().lastThumb.observe(this, thumbObs)
            videoListAdapter.events.onEach { it.process() }.launchWhenStarted(lifecycleScope)
        }
    }

    private fun setDataObservers() {
        videoListAdapter.dataType = viewModel.groupingType
        viewModel.provider.loading.observe(this@VideoGridFragment) { loading ->
            if (isResumed) setRefreshing(loading) { refresh ->
                if (!refresh) {
                    menu?.let { UiTools.updateSortTitles(it, viewModel.provider) }
                    restoreMultiSelectHelper()
                }
            }
        }
        videoListAdapter.showFilename.set(viewModel.groupingType == VideoGroupingType.NONE && viewModel.provider.sort == Medialibrary.SORT_FILENAME)
        lifecycleScope.launch {
            waitForML()
            viewModel.provider.pagedList.observe(this@VideoGridFragment) {
//                if (!Permissions.canReadVideos(AppContextProvider.appContext)) {
//                    if (viewModel.provider.isEmpty())
//                        viewModel.provider.clear()
//                    else
//                        lifecycleScope.launch(Dispatchers.Main) {
//                            updateEmptyView()
//                        }
//                    return@observe
//                }
                @Suppress("UNCHECKED_CAST")
                (it as? PagedList<MediaLibraryItem>)?.let { pagedList -> videoListAdapter.submitList(pagedList) }
                updateEmptyView()
                restoreMultiSelectHelper()
                if (it !is InitialPagedList<*, *> && activity?.isFinishing == false && viewModel.group != null && it.size < 2 && viewModel.filterQuery.isNullOrEmpty()) requireActivity().finish()
                setFabPlayVisibility(true)
            }
        }
        EventTools.getInstance().lastThumb.observe(this) {
            videoListAdapter.updateThumb(it)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.ml_menu_last_playlist).isVisible = settings.contains(KEY_MEDIA_LAST_PLAYLIST)
        menu.findItem(R.id.rename_group).isVisible = viewModel.group != null
        menu.findItem(R.id.ungroup).isVisible = viewModel.group != null
        menu.findItem(R.id.ml_menu_sortby).isVisible = false
        menu.findItem(R.id.ml_menu_display_options).isVisible = true
        if (requireActivity().isTalkbackIsEnabled()) menu.findItem(R.id.play_all).isVisible = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ml_menu_last_playlist -> {
                MediaUtils.loadlastPlaylist(activity, PLAYLIST_TYPE_VIDEO)
            }
            R.id.rename_group -> {
                viewModel.group?.let { renameGroup(it) }
            }
            R.id.ungroup -> {
                viewModel.group?.let { lifecycleScope.launch{if (!requireActivity().showPinIfNeeded()) viewModel.ungroup(it) } }
            }
            R.id.play_all -> {
                onFabPlayClick(binding.root)
            }
            R.id.ml_menu_display_options -> {
                //filter all sorts and keep only applicable ones
                val sorts = arrayListOf(Medialibrary.SORT_ALPHA, Medialibrary.SORT_FILENAME, Medialibrary.SORT_ARTIST, Medialibrary.SORT_ALBUM, Medialibrary.SORT_DURATION, Medialibrary.SORT_RELEASEDATE, Medialibrary.SORT_LASTMODIFICATIONDATE, Medialibrary.SORT_FILESIZE, Medialibrary.NbMedia, Medialibrary.SORT_INSERTIONDATE).filter {
                    viewModel.provider.canSortBy(it)
                }
                //Open the display settings Bottom sheet
                DisplaySettingsDialog.newInstance(
                    displayInCards = settings.getBoolean(KEY_VIDEOS_CARDS, true),
                    onlyFavs = viewModel.provider.onlyFavorites,
                    sorts = sorts,
                    currentSort = viewModel.provider.sort,
                    currentSortDesc = viewModel.provider.desc,
                    videoGroup = settings.getString(KEY_GROUP_VIDEOS, GROUP_VIDEOS_NAME),
                    defaultPlaybackActions = DefaultPlaybackActionMediaType.VIDEO.getDefaultPlaybackActions(settings),
                    defaultActionType = getString(DefaultPlaybackActionMediaType.VIDEO.title)
                )
                        .show(requireActivity().supportFragmentManager, "DisplaySettingsDialog")
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun sortBy(sort: Int) {
        videoListAdapter.showFilename.set(sort == Medialibrary.SORT_FILENAME)
        super.sortBy(sort)
    }

    private fun changeGroupingType(type: VideoGroupingType) {
        viewModel.provider.pagedList.removeObservers(this)
        viewModel.provider.loading.removeObservers(this)
        viewModel.changeGroupingType(type)
        setDataObservers()
        (activity as? AppCompatActivity)?.run {
            supportActionBar?.title = title
            invalidateOptionsMenu()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = VideoGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val empty = viewModel.isEmpty()
        binding.emptyLoading.state = if (empty) EmptyLoadingState.LOADING else EmptyLoadingState.NONE
        binding.empty = empty
        binding.emptyLoading.setOnNoMediaClickListener {
            requireActivity().setResult(RESULT_RESTART)
        }
        swipeRefreshLayout.setOnRefreshListener(this)
        binding.videoGrid.adapter = videoListAdapter
        binding.fastScroller.attachToCoordinator(requireActivity().findViewById<View>(R.id.appbar) as AppBarLayout, requireActivity().findViewById<View>(R.id.coordinator) as CoordinatorLayout, requireActivity().findViewById<View>(R.id.fab) as FloatingActionButton)
        binding.fastScroller.setRecyclerView(binding.videoGrid, viewModel.provider)

        (parentFragment as? VideoBrowserFragment)?.videoGridOnlyFavorites = viewModel.provider.onlyFavorites
        requireActivity().supportFragmentManager.setFragmentResultListener(CONFIRM_ADD_TO_GROUP_RESULT, viewLifecycleOwner) { requestKey, bundle ->
            lifecycleScope.launch {
                val selection = bundle.parcelableArray<MediaWrapper>(AddToGroupDialog.KEY_TRACKS) ?: arrayOf()
                viewModel.createGroup(selection.toList())?.let {
                    // we already are in a group. Finishing to avoid stacking multiple group activities
                    if (viewModel.groupingType == VideoGroupingType.NONE) requireActivity().finish()
                    activity?.open(it)
                }
            }
        }
        requireActivity().supportFragmentManager.setFragmentResultListener(CONFIRM_RENAME_DIALOG_RESULT, viewLifecycleOwner) { requestKey, bundle ->
            val media = bundle.parcelable<MediaLibraryItem>(RENAME_DIALOG_MEDIA) ?: return@setFragmentResultListener
            val name = bundle.getString(RENAME_DIALOG_NEW_NAME) ?: return@setFragmentResultListener
            viewModel.renameGroup(media as VideoGroup, name)
            (activity as? AppCompatActivity)?.run {
                supportActionBar?.title = name
            }
        }
        requireActivity().supportFragmentManager.setFragmentResultListener(CONFIRM_PERMISSION_CHANGED, viewLifecycleOwner) { requestKey, bundle ->
            val changed = bundle.getBoolean(KEY_PERMISSION_CHANGED)
            if (changed) viewModel.refresh()
        }
    }

    override fun onDisplaySettingChanged(key: String, value: Any) {
        when (key) {
            DISPLAY_IN_CARDS -> {
                settings.putSingle(KEY_VIDEOS_CARDS, value as Boolean)
                updateViewMode()
            }
            ONLY_FAVS -> {
                viewModel.provider.showOnlyFavs(value as Boolean)
                viewModel.refresh()
                (parentFragment as? VideoBrowserFragment)?.videoGridOnlyFavorites = value
            }
            CURRENT_SORT -> {
                @Suppress("UNCHECKED_CAST") val sort = value as Pair<Int, Boolean>
                viewModel.provider.sort = sort.first
                viewModel.provider.desc = sort.second
                viewModel.provider.saveSort()
                viewModel.refresh()
            }
            VIDEO_GROUPING -> {
                val videoGroup = value as DisplaySettingsDialog.VideoGroup
                settings.putSingle(KEY_GROUP_VIDEOS, videoGroup.value)
                changeGroupingType(videoGroup.type)
            }
            DEFAULT_ACTIONS -> {
                Settings.getInstance(requireActivity()).putSingle(DefaultPlaybackActionMediaType.VIDEO.defaultActionKey, (value as DefaultPlaybackAction).name)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateEmptyView()
    }

    override fun onStart() {
        super.onStart()
        registerForContextMenu(binding.videoGrid)
        updateViewMode()
        setFabPlayVisibility(true)
        fabPlay?.setImageResource(R.drawable.ic_fab_play)
        fabPlay?.contentDescription = getString(R.string.play)
        if (!viewModel.isEmpty() && getFilterQuery() == null) viewModel.refresh()
    }

    override fun onStop() {
        super.onStop()
        unregisterForContextMenu(binding.videoGrid)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_FOLDER, viewModel.folder)
        outState.putParcelable(KEY_GROUP, viewModel.group)
        outState.putSerializable(KEY_GROUPING, viewModel.groupingType)
    }

    override fun onDestroy() {
        super.onDestroy()
        gridItemDecoration = null
        swipeRefreshLayout.setOnRefreshListener(null)
        if (::dataObserver.isInitialized) videoListAdapter.unregisterAdapterDataObserver(dataObserver)
    }

    override fun getTitle() = when(viewModel.groupingType) {
        VideoGroupingType.NONE -> viewModel.folder?.displayTitle ?: viewModel.group?.displayTitle
        ?: getString(R.string.videos)
        VideoGroupingType.FOLDER -> getString(R.string.videos_folders_title)
        VideoGroupingType.NAME -> getString(R.string.videos_groups_title)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getMultiHelper(): MultiSelectHelper<VideosViewModel>? = if (::videoListAdapter.isInitialized) videoListAdapter.multiSelectHelper as? MultiSelectHelper<VideosViewModel> else null

    private fun updateViewMode() {
        if (view == null || activity == null) {
            Log.w(TAG, "Unable to setup the view")
            return
        }
        val res = resources
        if (gridItemDecoration == null) gridItemDecoration = ItemOffsetDecoration(resources, R.dimen.left_right_1610_margin, R.dimen.top_bottom_1610_margin)
        val listMode = !settings.getBoolean(KEY_VIDEOS_CARDS, true)

        // Select between grid or list
        binding.videoGrid.removeItemDecoration(gridItemDecoration!!)
        if (!listMode) {
            val thumbnailWidth = res.getDimensionPixelSize(R.dimen.grid_card_thumb_width)
            val margin = binding.videoGrid.paddingStart + binding.videoGrid.paddingEnd
            val columnWidth = binding.videoGrid.getPerfectColumnWidth(thumbnailWidth, margin) - res.getDimensionPixelSize(R.dimen.left_right_1610_margin) * 2
            binding.videoGrid.columnWidth = columnWidth
            binding.videoGrid.addItemDecoration(gridItemDecoration!!)
            binding.videoGrid.setPadding(4.dp, 4.dp, 4.dp, 4.dp)
        } else {
            binding.videoGrid.setPadding(0, 0, 0, 0)
        }
        binding.videoGrid.setNumColumns(if (listMode) 1 else -1)
        videoListAdapter.isListMode = listMode
    }

    override fun onFabPlayClick(view: View) {
        viewModel.playAll(activity)
    }

    private fun updateEmptyView() {
        if (!::binding.isInitialized) return
        if (!isAdded) return
        val empty = viewModel.isEmpty() && videoListAdapter.currentList.isNullOrEmpty()
        val working = viewModel.provider.loading.value != false
        binding.emptyLoading.emptyText = viewModel.filterQuery?.let {  getString(R.string.empty_search, it) } ?: if (viewModel.provider.onlyFavorites) getString(R.string.nofav) else getString(R.string.nomedia)
        binding.emptyLoading.state = when {
            !Permissions.canReadStorage(AppContextProvider.appContext) && empty -> EmptyLoadingState.MISSING_PERMISSION
            !Permissions.canReadVideos(AppContextProvider.appContext) && empty -> EmptyLoadingState.MISSING_VIDEO_PERMISSION
            empty && working -> EmptyLoadingState.LOADING
            empty && !working && viewModel.provider.onlyFavorites -> EmptyLoadingState.EMPTY_FAVORITES
            empty && !working && viewModel.filterQuery == null -> EmptyLoadingState.EMPTY
            empty && !working && viewModel.filterQuery != null -> EmptyLoadingState.EMPTY_SEARCH
            else -> EmptyLoadingState.NONE
        }
        binding.empty = empty && !working
    }

    override fun onRefresh() {
        activity?.reloadLibrary()
    }

    override fun setFabPlayVisibility(enable: Boolean) {
        super.setFabPlayVisibility(!viewModel.isEmpty() && enable)
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        when (viewModel.groupingType) {
            VideoGroupingType.NONE -> mode.menuInflater.inflate(R.menu.action_mode_video, menu)
            VideoGroupingType.FOLDER -> mode.menuInflater.inflate(R.menu.action_mode_folder, menu)
            VideoGroupingType.NAME -> mode.menuInflater.inflate(R.menu.action_mode_video_group, menu)
        }
        multiSelectHelper.toggleActionMode(true, videoListAdapter.itemCount)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = multiSelectHelper.getSelectionCount()
        if (count == 0) {
            stopActionMode()
            return false
        }
        lifecycleScope.launch { fillActionMode(requireActivity(), mode, multiSelectHelper) }
        when (viewModel.groupingType) {
            VideoGroupingType.NONE -> {
                menu.findItem(R.id.action_video_append).isVisible = PlaylistManager.hasMedia()
                menu.findItem(R.id.action_video_info).isVisible = count == 1
                menu.findItem(R.id.action_remove_from_group).isVisible = viewModel.group != null
                menu.findItem(R.id.action_add_to_group).isVisible = viewModel.group != null && count > 1
                menu.findItem(R.id.action_mode_go_to_folder).isVisible = checkFolderToParent(count)
            }
            VideoGroupingType.NAME -> {
                menu.findItem(R.id.action_ungroup).isVisible = !multiSelectHelper.getSelection().any { it !is VideoGroup }
                menu.findItem(R.id.action_rename).isVisible = count == 1 && !multiSelectHelper.getSelection().any { it !is VideoGroup }
                menu.findItem(R.id.action_group_similar).isVisible = count == 1 && multiSelectHelper.getSelection().filterIsInstance<VideoGroup>().isEmpty()
                menu.findItem(R.id.action_mode_go_to_folder).isVisible = checkFolderToParent(count)
            }
            else -> {}
        }
        menu.findItem(R.id.action_mode_favorite_add).isVisible = multiSelectHelper.getSelection().none { it.isFavorite }
        menu.findItem(R.id.action_mode_favorite_remove).isVisible = multiSelectHelper.getSelection().none { !it.isFavorite }
        return true
    }

    private fun checkFolderToParent(count: Int) = if (count == 1) {
        (multiSelectHelper.getSelection().firstOrNull() as? MediaWrapper)?.let {
            if (it.type != MediaWrapper.TYPE_VIDEO) return@let false
            return@let it.uri.retrieveParent() != null
        } == true
    } else false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (!isStarted()) return false
        when (viewModel.groupingType) {
            VideoGroupingType.NONE -> {
                val list = multiSelectHelper.getSelection().map { it as MediaWrapper }
                if (list.isNotEmpty()) {
                    when (item.itemId) {
                        R.id.action_video_play -> MediaUtils.openList(activity, list, 0)
                        R.id.action_video_append -> MediaUtils.appendMedia(activity, list)
                        R.id.action_video_share -> requireActivity().share(list)
                        R.id.action_video_info -> showInfoDialog(list.first())
                        R.id.action_video_play_audio -> {
                            for (media in list) media.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                            MediaUtils.openList(activity, list, 0)
                        }
                        R.id.action_mode_audio_add_playlist -> requireActivity().addToPlaylist(list)
                        R.id.action_video_delete -> removeItems(list)
                        R.id.action_remove_from_group -> viewModel.removeFromGroup(list)
                        R.id.action_ungroup -> viewModel.ungroup(list)
                        R.id.action_add_to_group -> addToGroup(list)
                        R.id.action_mode_go_to_folder -> (list.first() as? MediaWrapper)?.let { showParentFolder(it) }
                        R.id.action_mode_favorite_add -> lifecycleScope.launch { viewModel.changeFavorite(list, true)}
                        R.id.action_mode_favorite_remove -> lifecycleScope.launch { viewModel.changeFavorite(list, false)}
                        else -> {
                            stopActionMode()
                            return false
                        }
                    }
                }
            }
            VideoGroupingType.FOLDER -> {
                val selection = ArrayList<Folder>()
                for (mediaLibraryItem in multiSelectHelper.getSelection()) {
                    selection.add(mediaLibraryItem as FolderImpl)
                }
                when (item.itemId) {
                    R.id.action_folder_play -> viewModel.playFoldersSelection(selection)
                    R.id.action_folder_append -> viewModel.appendFoldersSelection(selection)
                    R.id.action_folder_add_playlist -> lifecycleScope.launch { requireActivity().addToPlaylist(withContext(Dispatchers.Default) { selection.getAll() }) }
                    R.id.action_video_delete -> removeItems(selection.getAll())
                    R.id.action_mode_favorite_add -> lifecycleScope.launch { viewModel.changeFavorite(selection.getAll(), true)}
                    R.id.action_mode_favorite_remove -> lifecycleScope.launch { viewModel.changeFavorite(selection.getAll(), false)}
                    else -> return false
                }
            }
            VideoGroupingType.NAME -> {
                val selection = multiSelectHelper.getSelection()
                when (item.itemId) {
                    R.id.action_videogroup_play -> MediaUtils.openList(activity, selection.getAll(), 0)
                    R.id.action_videogroup_append -> MediaUtils.appendMedia(activity, selection.getAll())
                    R.id.action_videogroup_add_playlist -> lifecycleScope.launch { requireActivity().addToPlaylist(withContext(Dispatchers.Default) { selection.getAll() }) }
                    R.id.action_group_similar -> lifecycleScope.launch { viewModel.groupSimilar(selection.getAll().first()) }
                    R.id.action_ungroup -> viewModel.ungroup(selection.first() as VideoGroup)
                    R.id.action_rename -> renameGroup(selection.first() as VideoGroup)
                    R.id.action_add_to_group -> addToGroup(selection)
                    R.id.action_mode_go_to_folder -> (selection.first() as? MediaWrapper)?.let { showParentFolder(it) }
                    R.id.action_video_delete -> removeItems(selection.getAll())
                    R.id.action_mode_favorite_add -> lifecycleScope.launch { viewModel.changeFavorite(selection.getAll(), true)}
                    R.id.action_mode_favorite_remove -> lifecycleScope.launch { viewModel.changeFavorite(selection.getAll(), false)}
                    else -> return false
                }
            }
        }
        stopActionMode()
        return true
    }

    private fun addToGroup(selection: List<MediaLibraryItem>) {
        requireActivity().addToGroup(selection.getAll(), selection.size == 1)
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        setFabPlayVisibility(true)
        multiSelectHelper.clearSelection()
        multiSelectHelper.toggleActionMode(false, videoListAdapter.itemCount)
    }

    fun updateSeenMediaMarker() {
        videoListAdapter.setSeenMediaMarkerVisible(settings.getBoolean(KEY_MEDIA_SEEN, true))
        videoListAdapter.notifyItemRangeChanged(0, videoListAdapter.itemCount - 1, UPDATE_SEEN)
    }

    override fun onCtxAction(position: Int, option: ContextOption) {
        if (position >= videoListAdapter.itemCount) return
        val activity = activity ?: return
        when (val media = videoListAdapter.getItemByPosition(position)) {
            is MediaWrapper -> when (option) {
                CTX_PLAY_FROM_START -> viewModel.playVideo(activity, media, position, fromStart = true)
                CTX_PLAY_AS_AUDIO -> viewModel.playAudio(activity, media)
                CTX_PLAY_ALL -> viewModel.playVideo(activity, media, position, forceAll = true)
                CTX_PLAY -> viewModel.play(position)
                CTX_INFORMATION -> showInfoDialog(media)
                CTX_DELETE -> removeItem(media)
                CTX_APPEND -> MediaUtils.appendMedia(activity, media)
                CTX_SET_RINGTONE -> requireActivity().setRingtone(media)
                CTX_PLAY_NEXT -> MediaUtils.insertNext(requireActivity(), media.tracks)
                CTX_DOWNLOAD_SUBTITLES -> MediaUtils.getSubs(requireActivity(), media)
                CTX_ADD_TO_PLAYLIST -> requireActivity().addToPlaylist(media.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
                CTX_FIND_METADATA -> {
                    val intent = Intent().apply {
                        setClassName(requireContext().applicationContext, MOVIEPEDIA_ACTIVITY)
                        apply { putExtra(MOVIEPEDIA_MEDIA, media) }
                    }
                    startActivity(intent)
                }
                CTX_SHARE -> lifecycleScope.launch { (requireActivity() as AppCompatActivity).share(media) }
                CTX_REMOVE_GROUP -> viewModel.removeFromGroup(media)
                CTX_ADD_GROUP -> requireActivity().addToGroup(listOf(media), true)
                CTX_GROUP_SIMILAR -> lifecycleScope.launch { if (!requireActivity().showPinIfNeeded()) viewModel.groupSimilar(media) }
                CTX_MARK_AS_PLAYED -> lifecycleScope.launch { viewModel.markAsPlayed(media) }
                CTX_MARK_AS_UNPLAYED -> lifecycleScope.launch { viewModel.markAsUnplayed(media) }
                CTX_FAV_ADD, CTX_FAV_REMOVE -> lifecycleScope.launch(Dispatchers.IO) {
                    media.isFavorite = option == CTX_FAV_ADD
                    withContext(Dispatchers.Main) { videoListAdapter.notifyItemChanged(position) }
                }
                CTX_GO_TO_FOLDER -> showParentFolder(media)
                CTX_ADD_SHORTCUT -> lifecycleScope.launch { requireActivity().createShortcut(media)}
                else -> {}
            }
            is Folder -> when (option) {
                CTX_PLAY -> viewModel.play(position)
                CTX_APPEND -> viewModel.append(position)
                CTX_ADD_TO_PLAYLIST -> viewModel.addItemToPlaylist(requireActivity(), position)
                CTX_MARK_ALL_AS_PLAYED -> lifecycleScope.launch { viewModel.markAsPlayed(media) }
                CTX_MARK_ALL_AS_UNPLAYED -> lifecycleScope.launch { viewModel.markAsUnplayed(media) }
                CTX_FAV_ADD, CTX_FAV_REMOVE -> lifecycleScope.launch(Dispatchers.IO) {
                    media.isFavorite = option == CTX_FAV_ADD
                    withContext(Dispatchers.Main) { videoListAdapter.notifyItemChanged(position) }
                }
                CTX_BAN_FOLDER -> banFolder(media)
                else -> {}
            }
            is VideoGroup -> when (option) {
                CTX_PLAY_ALL -> viewModel.play(position)
                CTX_PLAY -> viewModel.play(position)
                CTX_APPEND -> viewModel.append(position)
                CTX_ADD_TO_PLAYLIST -> viewModel.addItemToPlaylist(requireActivity(), position)
                CTX_RENAME_GROUP -> renameGroup(media)
                CTX_UNGROUP -> lifecycleScope.launch { if (!requireActivity().showPinIfNeeded()) viewModel.ungroup(media) }
                CTX_MARK_ALL_AS_PLAYED -> lifecycleScope.launch { viewModel.markAsPlayed(media) }
                CTX_MARK_ALL_AS_UNPLAYED -> lifecycleScope.launch { viewModel.markAsUnplayed(media) }
                CTX_ADD_GROUP -> requireActivity().addToGroup(listOf(media).getAll(), true)
                CTX_FAV_ADD, CTX_FAV_REMOVE -> lifecycleScope.launch(Dispatchers.IO) {
                    media.isFavorite = option == CTX_FAV_ADD
                    withContext(Dispatchers.Main) { videoListAdapter.notifyItemChanged(position) }
                }
                else -> {}
            }
        }
    }

    private fun banFolder(folder: Folder) {
        val dialog = ConfirmDeleteDialog.newInstance(medias = arrayListOf(folder), title = getString(R.string.group_ban_folder), description = getString(R.string.ban_folder_explanation, getString(R.string.medialibrary_directories)), buttonText = getString(R.string.ban_folder), resultType = CONFIRM_DELETE_DIALOG_RESULT_BAN_FOLDER)
        dialog.show((activity as FragmentActivity).supportFragmentManager, RenameDialog::class.simpleName)
    }

    private fun renameGroup(media: VideoGroup) {
        val dialog = RenameDialog.newInstance(media)
        dialog.show(requireActivity().supportFragmentManager, RenameDialog::class.simpleName)
    }

    private val thumbObs = Observer<MediaWrapper> { media ->
        if (!::videoListAdapter.isInitialized || viewModel.provider !is VideosProvider) return@Observer
        val position = viewModel.provider.pagedList.value?.indexOf(media) ?: return@Observer
        val item = videoListAdapter.getItemByPosition(position) as? MediaWrapper
        item?.run {
            artworkURL = media.artworkURL
            videoListAdapter.notifyItemChanged(position)
        }
    }

    private fun VideoAction.process() {
        when (this) {
            is VideoClick -> {
                onClick(position, item)
            }
            is VideoLongClick -> {
                if ((item is VideoGroup && item.presentCount == 0)) UiTools.snackerMissing(requireActivity()) else onLongClick(position)
            }
            is VideoCtxClick -> {
                when (item) {
                    is Folder -> {
                        val flags = createCtxFolderFlags().apply {
                            if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                        }
                        showContext(requireActivity(), this@VideoGridFragment, position, item, flags)
                    }
                    is VideoGroup -> {
                        if (item.presentCount == 0) UiTools.snackerMissing(requireActivity())
                        else {
                            val flags = createCtxVideoGroupFlags().apply {
                                if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                            }
                            showContext(requireActivity(), this@VideoGridFragment, position, item, flags)
                        }
                    }
                    is MediaWrapper -> {
                        val flags = createCtxVideoFlags().apply {
                            if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                            if (item.seen > 0) add(CTX_MARK_AS_UNPLAYED) else add(CTX_MARK_AS_PLAYED)
                            if (item.time != 0L) add(CTX_PLAY_FROM_START)
                            if (viewModel.groupingType == VideoGroupingType.NAME || viewModel.group != null) {
                                if (viewModel.group != null) add(CTX_REMOVE_GROUP) else addAll(CTX_ADD_GROUP, CTX_GROUP_SIMILAR)
                            }
                            //go to folder
                            if (item.uri.retrieveParent() != null) add(CTX_GO_TO_FOLDER)
                        }
                        showContext(requireActivity(), this@VideoGridFragment, position, item, flags)
                    }
                }
            }
            is VideoImageClick -> {
                if (actionMode != null) {
                    onClick(position, item)
                } else {
                    onLongClick(position)
                }
            }
        }
    }

    private fun onLongClick(position: Int) {
        if (actionMode == null && inSearchMode()) UiTools.setKeyboardVisibility(binding.root, false)
        multiSelectHelper.toggleSelection(position, true)
        if (actionMode == null) startActionMode() else invalidateActionMode()
    }

    private fun onClick(position: Int, item: MediaLibraryItem) {
        when (item) {
            is MediaWrapper -> {
                if (actionMode != null) {
                    multiSelectHelper.toggleSelection(position)
                    invalidateActionMode()
                } else {
                    val castAsAudio = castAsAudio()
                    if (castAsAudio) {
                        item.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                        PlaylistManager.playingAsAudio = true
                    }
                    when(DefaultPlaybackActionMediaType.VIDEO.getCurrentPlaybackAction(settings)) {
                        DefaultPlaybackAction.PLAY -> viewModel.playVideo(activity, item, position, forceAudio = castAsAudio)
                        DefaultPlaybackAction.ADD_TO_QUEUE -> MediaUtils.appendMedia(activity, item)
                        DefaultPlaybackAction.INSERT_NEXT -> MediaUtils.insertNext(activity, item)
                        else  -> viewModel.playVideo(activity, item, position, forceAll = true, forceAudio = castAsAudio)
                    }
                }
            }
            is Folder -> {
                if (item.mMrl.isMissing()) return
                if (actionMode != null) {
                    multiSelectHelper.toggleSelection(position)
                    invalidateActionMode()
                } else activity?.open(item)
            }
            is VideoGroup -> when {
                actionMode != null -> {
                    multiSelectHelper.toggleSelection(position)
                    invalidateActionMode()
                }
                item.presentCount == 0 -> UiTools.snackerMissing(requireActivity())
                item.presentCount == 1 -> viewModel.play(position)
                else -> activity?.open(item)
            }
        }
    }

    private fun castAsAudio(): Boolean = PlaybackService.renderer.value != null && settings.getBoolean(KEY_CASTING_AUDIO_ONLY, false)

    companion object {
        fun newInstance() = VideoGridFragment()
    }
}

sealed class VideoAction
class VideoClick(val position: Int, val item: MediaLibraryItem) : VideoAction()
class VideoLongClick(val position: Int, val item: MediaLibraryItem) : VideoAction()
class VideoCtxClick(val position: Int, val item: MediaLibraryItem) : VideoAction()
class VideoImageClick(val position: Int, val item: MediaLibraryItem) : VideoAction()
