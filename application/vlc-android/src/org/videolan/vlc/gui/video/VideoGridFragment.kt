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
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onEach
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.FolderImpl
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VideoGridBinding
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.dialogs.*
import org.videolan.vlc.gui.helpers.ItemOffsetDecoration
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToGroup
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.getAll
import org.videolan.vlc.providers.medialibrary.VideosProvider
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.launchWhenStarted
import org.videolan.vlc.util.onAnyChange
import org.videolan.vlc.util.share
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType
import org.videolan.vlc.viewmodels.mobile.VideosViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel
import java.util.*

private const val TAG = "VLC/VideoListFragment"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class VideoGridFragment : MediaBrowserFragment<VideosViewModel>(), SwipeRefreshLayout.OnRefreshListener, CtxActionReceiver {

    private lateinit var dataObserver: RecyclerView.AdapterDataObserver
    private lateinit var videoListAdapter: VideoListAdapter
    private lateinit var multiSelectHelper: MultiSelectHelper<MediaLibraryItem>
    private lateinit var binding: VideoGridBinding
    private var gridItemDecoration: RecyclerView.ItemDecoration? = null
    private lateinit var settings: SharedPreferences

    private fun FragmentActivity.open(item: MediaLibraryItem) {
        val i = Intent(activity, SecondaryActivity::class.java)
        i.putExtra("fragment", SecondaryActivity.VIDEO_GROUP_LIST)
        if (item is Folder) i.putExtra(KEY_FOLDER, item)
        else if (item is VideoGroup) i.putExtra(KEY_GROUP, item)
        startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!::settings.isInitialized) settings = Settings.getInstance(requireContext())
        if (!::videoListAdapter.isInitialized) {
            val seenMarkVisible = settings.getBoolean("media_seen", true)
            videoListAdapter = VideoListAdapter(seenMarkVisible)
            dataObserver = videoListAdapter.onAnyChange { updateEmptyView() }
            multiSelectHelper = videoListAdapter.multiSelectHelper
            val folder = if (savedInstanceState != null) savedInstanceState.getParcelable<Folder>(KEY_FOLDER)
            else arguments?.getParcelable(KEY_FOLDER)
            val parentGroup = if (savedInstanceState != null) savedInstanceState.getParcelable<VideoGroup>(KEY_GROUP)
                    else arguments?.getParcelable(KEY_GROUP)
            val grouping = if (parentGroup != null || folder != null) VideoGroupingType.NONE else when (Settings.getInstance(requireContext()).getString(KEY_GROUP_VIDEOS, null) ?: GROUP_VIDEOS_NAME) {
                GROUP_VIDEOS_NONE -> VideoGroupingType.NONE
                GROUP_VIDEOS_FOLDER -> VideoGroupingType.FOLDER
                else -> VideoGroupingType.NAME
            }
            viewModel = getViewModel(grouping, folder, parentGroup)
            setDataObservers()
            Medialibrary.lastThumb.observe(this, thumbObs)
            videoListAdapter.events.onEach { it.process() }.launchWhenStarted(lifecycleScope)
        }
    }

    private fun setDataObservers() {
        videoListAdapter.dataType = viewModel.groupingType
        viewModel.provider.pagedList.observe(requireActivity(), Observer {
            (it as? PagedList<MediaLibraryItem>)?.let { videoListAdapter.submitList(it) }
            updateEmptyView()
            restoreMultiSelectHelper()
            if (activity?.isFinishing == false && viewModel.group != null && it.size < 2) requireActivity().finish()
        })

        viewModel.provider.loading.observe(this, Observer { loading ->
            setRefreshing(loading) { refresh ->
                if (!refresh) {
                    setFabPlayVisibility(true)
                    menu?.let { UiTools.updateSortTitles(it, viewModel.provider) }
                    restoreMultiSelectHelper()
                }
            }
        })
        videoListAdapter.showFilename.set(viewModel.groupingType == VideoGroupingType.NONE && viewModel.provider.sort == Medialibrary.SORT_FILENAME)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.ml_menu_last_playlist).isVisible = settings.contains(KEY_MEDIA_LAST_PLAYLIST)
        menu.findItem(R.id.ml_menu_video_group).isVisible = viewModel.group == null && viewModel.folder == null
        val displayInCards = settings.getBoolean("video_display_in_cards", true)
        menu.findItem(R.id.ml_menu_display_grid).isVisible = !displayInCards
        menu.findItem(R.id.ml_menu_display_list).isVisible = displayInCards
        menu.findItem(R.id.rename_group).isVisible = viewModel.group != null
        menu.findItem(R.id.ungroup).isVisible = viewModel.group != null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ml_menu_last_playlist -> {
                MediaUtils.loadlastPlaylist(activity, PLAYLIST_TYPE_VIDEO)
            }
            R.id.ml_menu_display_list, R.id.ml_menu_display_grid -> {
                val displayInCards = settings.getBoolean(KEY_VIDEOS_CARDS, true)
                settings.putSingle(KEY_VIDEOS_CARDS, !displayInCards)
                (activity as ContentActivity).forceLoadVideoFragment()
            }
            R.id.video_min_group_length_disable -> {
                settings.putSingle(KEY_GROUP_VIDEOS, GROUP_VIDEOS_NONE)
                changeGroupingType(VideoGroupingType.NONE)
            }
            R.id.video_min_group_length_folder -> {
                settings.putSingle(KEY_GROUP_VIDEOS, GROUP_VIDEOS_FOLDER)
                changeGroupingType(VideoGroupingType.FOLDER)
            }
            R.id.video_min_group_length_name -> {
                settings.putSingle(KEY_GROUP_VIDEOS, GROUP_VIDEOS_NAME)
                changeGroupingType(VideoGroupingType.NAME)
            }
            R.id.rename_group -> {
                viewModel.group?.let { renameGroup(it) }
            }
            R.id.ungroup -> {
                viewModel.group?.let { viewModel.ungroup(it) }
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        swipeRefreshLayout.setOnRefreshListener(this)
        binding.videoGrid.adapter = videoListAdapter
    }

    override fun onStart() {
        super.onStart()
        registerForContextMenu(binding.videoGrid)
        updateViewMode()
        setFabPlayVisibility(true)
        fabPlay?.setImageResource(R.drawable.ic_fab_play)
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
        videoListAdapter.release()
        gridItemDecoration = null
        if (::dataObserver.isInitialized) videoListAdapter.unregisterAdapterDataObserver(dataObserver)
    }

    override fun getTitle() = when(viewModel.groupingType) {
        VideoGroupingType.NONE -> viewModel.folder?.displayTitle ?: viewModel.group?.displayTitle
        ?: getString(R.string.videos)
        VideoGroupingType.FOLDER -> getString(R.string.videos_folders_title)
        VideoGroupingType.NAME -> getString(R.string.videos_groups_title)
    }

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
            videoListAdapter.setGridCardWidth(binding.videoGrid.columnWidth)
            binding.videoGrid.addItemDecoration(gridItemDecoration!!)
        }
        binding.videoGrid.setNumColumns(if (listMode) 1 else -1)
        if (videoListAdapter.isListMode != listMode) videoListAdapter.isListMode = listMode
    }

    override fun onFabPlayClick(view: View) {
        viewModel.playAll(activity)
    }

    private fun updateEmptyView() {
        if (!::binding.isInitialized) return
        val empty = viewModel.isEmpty() && videoListAdapter.currentList.isNullOrEmpty()
        val working = viewModel.provider.loading.value != false
        binding.emptyLoading.state = when {
            empty && working -> EmptyLoadingState.LOADING
            empty && !working -> EmptyLoadingState.EMPTY
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
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = multiSelectHelper.getSelectionCount()
        if (count == 0) {
            stopActionMode()
            return false
        }
        when (viewModel.groupingType) {
            VideoGroupingType.NONE -> {
                menu.findItem(R.id.action_video_append).isVisible = PlaylistManager.hasMedia()
                menu.findItem(R.id.action_video_info).isVisible = count == 1
                menu.findItem(R.id.action_remove_from_group).isVisible = viewModel.group != null
            }
            VideoGroupingType.NAME -> {
                menu.findItem(R.id.action_ungroup).isVisible = !multiSelectHelper.getSelection().any { it !is VideoGroup }
                menu.findItem(R.id.action_rename).isVisible = count == 1 && !multiSelectHelper.getSelection().any { it !is VideoGroup }
                menu.findItem(R.id.action_group_similar).isVisible = count == 1 && multiSelectHelper.getSelection().filterIsInstance<VideoGroup>().isEmpty()
                menu.findItem(R.id.action_add_to_group).isVisible = multiSelectHelper.getSelection().filterIsInstance<VideoGroup>().isEmpty()
            }
            else -> {}
        }
        return true
    }

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
                        R.id.action_video_info -> showInfoDialog(list[0])
                        //            case R.id.action_video_delete:
                        //                for (int position : rowsAdapter.getSelectedPositions())
                        //                    removeVideo(position, rowsAdapter.getItem(position));
                        //                break;
                        R.id.action_video_download_subtitles -> MediaUtils.getSubs(requireActivity(), list)
                        R.id.action_video_play_audio -> {
                            for (media in list) media.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                            MediaUtils.openList(activity, list, 0)
                        }
                        R.id.action_mode_audio_add_playlist -> requireActivity().addToPlaylist(list)
                        R.id.action_video_delete -> removeItems(list)
                        R.id.action_remove_from_group -> viewModel.removeFromGroup(list)
                        R.id.action_ungroup -> viewModel.ungroup(list)
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
                    R.id.action_add_to_group -> lifecycleScope.launch {
                        if (selection.size > 1) {
                            viewModel.createGroup(selection.getAll())?.let {
                                activity?.open(it)
                            }
                        } else requireActivity().addToGroup(selection.getAll())
                    }
                    else -> return false
                }
            }
        }
        stopActionMode()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        setFabPlayVisibility(true)
        multiSelectHelper.clearSelection()
    }

    fun updateSeenMediaMarker() {
        videoListAdapter.setSeenMediaMarkerVisible(settings.getBoolean("media_seen", true))
        videoListAdapter.notifyItemRangeChanged(0, videoListAdapter.itemCount - 1, UPDATE_SEEN)
    }

    override fun onCtxAction(position: Int, option: Long) {
        if (position >= videoListAdapter.itemCount) return
        val activity = activity ?: return
        when (val media = videoListAdapter.getItem(position)) {
            is MediaWrapper -> when (option) {
                CTX_PLAY_FROM_START -> viewModel.playVideo(activity, media, position, true)
                CTX_PLAY_AS_AUDIO -> viewModel.playAudio(activity, media)
                CTX_PLAY_ALL -> viewModel.play(position)
                CTX_INFORMATION -> showInfoDialog(media)
                CTX_DELETE -> removeItem(media)
                CTX_APPEND -> MediaUtils.appendMedia(activity, media)
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
                CTX_ADD_GROUP -> requireActivity().addToGroup(listOf(media))
                CTX_GROUP_SIMILAR -> lifecycleScope.launch { viewModel.groupSimilar(media) }
                CTX_MARK_AS_PLAYED -> lifecycleScope.launch { viewModel.markAsPlayed(media) }
            }
            is Folder -> when (option) {
                CTX_PLAY -> viewModel.play(position)
                CTX_APPEND -> viewModel.append(position)
                CTX_ADD_TO_PLAYLIST -> viewModel.addItemToPlaylist(requireActivity(), position)
                CTX_MARK_ALL_AS_PLAYED -> lifecycleScope.launch { viewModel.markAsPlayed(media) }
            }
            is VideoGroup -> when (option) {
                CTX_PLAY_ALL -> viewModel.play(position)
                CTX_PLAY -> viewModel.play(position)
                CTX_APPEND -> viewModel.append(position)
                CTX_ADD_TO_PLAYLIST -> viewModel.addItemToPlaylist(requireActivity(), position)
                CTX_RENAME_GROUP -> renameGroup(media)
                CTX_UNGROUP -> viewModel.ungroup(media)
                CTX_MARK_ALL_AS_PLAYED -> lifecycleScope.launch { viewModel.markAsPlayed(media) }
            }
        }
    }

    private fun renameGroup(media: VideoGroup) {
        val dialog = RenameDialog.newInstance(media)
        dialog.setListener { media, name ->
            viewModel.renameGroup(media as VideoGroup, name)
                (activity as? AppCompatActivity)?.run {
                    supportActionBar?.title = name
                }
        }
        dialog.show(requireActivity().supportFragmentManager, RenameDialog::class.simpleName)
    }

    private val thumbObs = Observer<MediaWrapper> { media ->
        if (!::videoListAdapter.isInitialized || viewModel.provider !is VideosProvider) return@Observer
        val position = viewModel.provider.pagedList.value?.indexOf(media) ?: return@Observer
        val item = videoListAdapter.getItem(position) as? MediaWrapper
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
                onLongClick(position)
            }
            is VideoCtxClick -> {
                when (item) {
                    is Folder -> showContext(requireActivity(), this@VideoGridFragment, position, item.title, CTX_FOLDER_FLAGS)
                    is VideoGroup -> showContext(requireActivity(), this@VideoGridFragment, position, item.title, CTX_FOLDER_FLAGS or CTX_RENAME_GROUP or CTX_UNGROUP or CTX_PLAY_ALL)
                    is MediaWrapper -> {
                        val group = item.type == MediaWrapper.TYPE_GROUP
                        var flags = if (group) CTX_VIDEO_GROUP_FLAGS else CTX_VIDEO_FLAGS
                        if (item.time != 0L && !group) flags = flags or CTX_PLAY_FROM_START
                        if (viewModel.groupingType == VideoGroupingType.NAME || viewModel.group != null) flags = flags or if (viewModel.group != null) CTX_REMOVE_GROUP else flags or CTX_ADD_GROUP or CTX_GROUP_SIMILAR
                        showContext(requireActivity(), this@VideoGridFragment, position, item.getTitle(), flags)
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
        multiSelectHelper.toggleSelection(position, true)
        if (actionMode == null) startActionMode()
    }

    private fun onClick(position: Int, item: MediaLibraryItem) {
        when (item) {
            is MediaWrapper -> {
                if (actionMode != null) {
                    multiSelectHelper.toggleSelection(position)
                    invalidateActionMode()
                } else {
                    viewModel.playVideo(activity, item, position)
                }
            }
            is Folder -> {
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
                item.mediaCount() == 1 -> viewModel.play(position)
                else -> activity?.open(item)
            }
        }
    }
}

sealed class VideoAction
class VideoClick(val position: Int, val item: MediaLibraryItem) : VideoAction()
class VideoLongClick(val position: Int, val item: MediaLibraryItem) : VideoAction()
class VideoCtxClick(val position: Int, val item: MediaLibraryItem) : VideoAction()
class VideoImageClick(val position: Int, val item: MediaLibraryItem) : VideoAction()
