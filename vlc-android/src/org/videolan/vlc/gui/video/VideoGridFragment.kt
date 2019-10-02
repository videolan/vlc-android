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

import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.*
import androidx.annotation.MainThread
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractFolder
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.tools.MultiSelectHelper
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VideoGridBinding
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.folders.FolderAction
import org.videolan.vlc.gui.folders.FolderClick
import org.videolan.vlc.gui.folders.FolderCtxClick
import org.videolan.vlc.gui.folders.FolderLongClick
import org.videolan.vlc.gui.helpers.ItemOffsetDecoration
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.providers.medialibrary.FoldersProvider
import org.videolan.vlc.providers.medialibrary.VideosProvider
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType
import org.videolan.vlc.viewmodels.mobile.VideosViewModel
import org.videolan.vlc.viewmodels.mobile.getFolderViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel
import java.lang.ref.WeakReference
import java.util.*

private const val TAG = "VLC/VideoListFragment"

private const val UPDATE_LIST = 14
private const val SET_REFRESHING = 15
private const val UNSET_REFRESHING = 16

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class VideoGridFragment : MediaBrowserFragment<VideosViewModel>(), SwipeRefreshLayout.OnRefreshListener, IEventsHandler, Observer<PagedList<AbstractMediaWrapper>>, CtxActionReceiver {

    private lateinit var videoListAdapter: VideoListAdapter
    private lateinit var multiSelectHelper: MultiSelectHelper<MediaLibraryItem>
    private lateinit var binding: VideoGridBinding
    private var gridItemDecoration: RecyclerView.ItemDecoration? = null

    private val actor = actor<FolderAction> {
        for (action in channel) when (action) {
            is FolderClick -> {
                if (actionMode != null) {
                    videoListAdapter.multiSelectHelper.toggleSelection(action.position)
                    invalidateActionMode()
                } else {
                    val i = Intent(activity, SecondaryActivity::class.java)
                    i.putExtra("fragment", SecondaryActivity.VIDEO_GROUP_LIST)
                    i.putExtra(KEY_FOLDER, action.folder)
                    activity?.startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
                }
            }
            is FolderLongClick -> {
                videoListAdapter.multiSelectHelper.toggleSelection(action.position, true)
                if (actionMode == null) {
                    startActionMode()
                }
            }
            is FolderCtxClick -> {
                showContext(requireActivity(), this@VideoGridFragment, action.position, action.folder.title, CTX_FOLDER_FLAGS)
            }
        }
    }

    class VideoGridFragmentHandler(private val videoGridFragment: WeakReference<VideoGridFragment>) : Handler() {
        override fun handleMessage(msg: Message?) {
            when (msg?.what) {
                UPDATE_LIST -> {
                    removeMessages(UPDATE_LIST)
                    videoGridFragment.get()?.updateList()
                }
                SET_REFRESHING -> videoGridFragment.get()?.swipeRefreshLayout?.isRefreshing = true
                UNSET_REFRESHING -> {
                    removeMessages(SET_REFRESHING)
                    videoGridFragment.get()?.swipeRefreshLayout?.isRefreshing = false
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!::videoListAdapter.isInitialized) {
            val preferences = Settings.getInstance(requireContext())
            val seenMarkVisible = preferences.getBoolean("media_seen", true)
            videoListAdapter = VideoListAdapter(this, seenMarkVisible)
            multiSelectHelper = videoListAdapter.multiSelectHelper
            val folder = if (savedInstanceState != null) savedInstanceState.getParcelable<AbstractFolder>(KEY_FOLDER)
            else arguments?.getParcelable(KEY_FOLDER)
            val group = if (savedInstanceState != null) savedInstanceState.getParcelable<AbstractVideoGroup>(KEY_GROUP)
            else arguments?.getParcelable(KEY_GROUP)
            val grouping = arguments?.getSerializable(KEY_GROUPING) ?: VideoGroupingType.NONE

            when (grouping) {
                VideoGroupingType.FOLDER -> {
                    viewModel = getFolderViewModel()
                    (viewModel.provider as FoldersProvider).pagedList.observe(requireActivity(), Observer {
                        swipeRefreshLayout.isRefreshing = false
                        videoListAdapter.showFilename(viewModel.provider.sort == AbstractMedialibrary.SORT_FILENAME)
                        if (it != null) videoListAdapter.submitList(it as PagedList<MediaLibraryItem>)
                        restoreMultiSelectHelper()
                        updateEmptyView()
                    })
                    videoListAdapter.dataType = VideoGroupingType.NONE
                }
                VideoGroupingType.NONE -> {
                    viewModel = getViewModel(folder, group)
                    (viewModel.provider as VideosProvider).pagedList.observe(this, this)
                    videoListAdapter.dataType = VideoGroupingType.FOLDER
                }
            }


            viewModel.provider.loading.observe(this, Observer { loading ->
                if (loading) handler.sendEmptyMessageDelayed(SET_REFRESHING, 300L)
                else handler.sendEmptyMessage(UNSET_REFRESHING)
                (activity as? MainActivity)?.refreshing = loading

            })
            AbstractMedialibrary.lastThumb.observe(this, thumbObs)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.ml_menu_last_playlist).isVisible = true
        menu.findItem(R.id.ml_menu_video_group).isVisible = viewModel.group == null && viewModel.folder == null
        val displayInCards = Settings.getInstance(requireActivity()).getBoolean("video_display_in_cards", true)
        menu.findItem(R.id.ml_menu_display_grid).isVisible = displayInCards
        menu.findItem(R.id.ml_menu_display_list).isVisible = !displayInCards
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ml_menu_last_playlist -> {
                MediaUtils.loadlastPlaylist(activity, PLAYLIST_TYPE_VIDEO)
                true
            }
            R.id.ml_menu_display_list, R.id.ml_menu_display_grid -> {
                val displayInCards = Settings.getInstance(requireActivity()).getBoolean("video_display_in_cards", true)
                Settings.getInstance(requireActivity()).edit().putBoolean("video_display_in_cards", !displayInCards).apply()
                (activity as ContentActivity).forceLoadVideoFragment()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = VideoGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val empty = viewModel.isEmpty()
        binding.loadingFlipper.visibility = if (empty) View.VISIBLE else View.GONE
        binding.loadingTitle.visibility = if (empty) View.VISIBLE else View.GONE
        binding.empty = empty
        binding.buttonNomedia.setOnClickListener {
            val activity = requireActivity()
            val intent = Intent(activity.applicationContext, SecondaryActivity::class.java)
            intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
            startActivity(intent)
            activity.setResult(RESULT_RESTART)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        gridItemDecoration = null
    }

    override fun onChanged(list: PagedList<AbstractMediaWrapper>?) {
        videoListAdapter.showFilename(viewModel.provider.sort == AbstractMedialibrary.SORT_FILENAME)
        if (list != null) videoListAdapter.submitList(list as PagedList<MediaLibraryItem>)
    }

    override fun getTitle() = viewModel.folder?.title ?: viewModel.group?.title
    ?: getString(R.string.video)

    override fun getMultiHelper(): MultiSelectHelper<VideosViewModel>? = if (::videoListAdapter.isInitialized) videoListAdapter.multiSelectHelper as? MultiSelectHelper<VideosViewModel> else null

    private fun updateViewMode() {
        if (view == null || activity == null) {
            Log.w(TAG, "Unable to setup the view")
            return
        }
        val res = resources
        if (gridItemDecoration == null)
            gridItemDecoration = ItemOffsetDecoration(resources, R.dimen.left_right_1610_margin, R.dimen.top_bottom_1610_margin)
        val listMode = Settings.getInstance(requireContext()).getBoolean("video_display_in_cards", false)

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

    private fun playVideo(media: AbstractMediaWrapper, fromStart: Boolean) {
        media.removeFlags(AbstractMediaWrapper.MEDIA_FORCE_AUDIO)
        if (fromStart) media.addFlags(AbstractMediaWrapper.MEDIA_FROM_START)
        MediaUtils.openMedia(requireContext(), media)
    }

    private fun playAudio(media: AbstractMediaWrapper) {
        media.addFlags(AbstractMediaWrapper.MEDIA_FORCE_AUDIO)
        MediaUtils.openMedia(activity, media)
    }

    override fun onFabPlayClick(view: View) {
        MediaUtils.playAll(context, viewModel.provider as VideosProvider, 0, false)
    }

    @MainThread
    fun updateList() {
        viewModel.refresh()
        handler.sendEmptyMessageDelayed(SET_REFRESHING, 300)
    }

    private fun updateEmptyView() {
        val empty = viewModel.isEmpty()
        val working = mediaLibrary.isWorking
        binding.loadingFlipper.visibility = if (empty && working) View.VISIBLE else View.GONE
        binding.loadingTitle.visibility = if (empty && working) View.VISIBLE else View.GONE
        binding.empty = empty && !working
    }

    override fun onRefresh() {
        val activity = activity
        activity?.reloadLibrary()
    }

    override fun clear() {
        videoListAdapter.clear()
    }

    override fun setFabPlayVisibility(enable: Boolean) {
        super.setFabPlayVisibility(!viewModel.isEmpty() && enable)
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.action_mode_video, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = multiSelectHelper.getSelectionCount()
        if (count == 0) {
            stopActionMode()
            return false
        }
        menu.findItem(R.id.action_video_info).isVisible = count == 1
        menu.findItem(R.id.action_video_append).isVisible = PlaylistManager.hasMedia()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (!isStarted()) return false
        val list = ArrayList<AbstractMediaWrapper>()
        for (mw in multiSelectHelper.getSelection()) {
            list.add(mw as MediaWrapper)
        }
        if (list.isNotEmpty()) {
            when (item.itemId) {
                R.id.action_video_play -> MediaUtils.openList(activity, list, 0)
                R.id.action_video_append -> MediaUtils.appendMedia(activity, list)
                R.id.action_video_info -> showInfoDialog(list[0])
                //            case R.id.action_video_delete:
                //                for (int position : rowsAdapter.getSelectedPositions())
                //                    removeVideo(position, rowsAdapter.getItem(position));
                //                break;
                R.id.action_video_download_subtitles -> MediaUtils.getSubs(requireActivity(), list)
                R.id.action_video_play_audio -> {
                    for (media in list) media.addFlags(AbstractMediaWrapper.MEDIA_FORCE_AUDIO)
                    MediaUtils.openList(activity, list, 0)
                }
                R.id.action_mode_audio_add_playlist -> UiTools.addToPlaylist(requireActivity(), list)
                R.id.action_video_delete -> removeItems(list)
                else -> {
                    stopActionMode()
                    return false
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

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        val media = item as AbstractMediaWrapper
        if (actionMode != null) {
            multiSelectHelper.toggleSelection(position)
            invalidateActionMode()
            return
        }
        media.removeFlags(AbstractMediaWrapper.MEDIA_FORCE_AUDIO)
        val settings = Settings.getInstance(v.context)
        if (settings.getBoolean(FORCE_PLAY_ALL, false)) {
            MediaUtils.playAll(requireContext(), viewModel.provider as VideosProvider, position, false)
        } else {
            playVideo(media, false)
        }
    }

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
        multiSelectHelper.toggleSelection(position, true)
        if (actionMode == null) startActionMode()
        return true
    }

    override fun onImageClick(v: View, position: Int, item: MediaLibraryItem) {}

    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {
        val mw = item as AbstractMediaWrapper
        val group = mw.type == AbstractMediaWrapper.TYPE_GROUP
        var flags = if (group) CTX_VIDEO_GOUP_FLAGS else CTX_VIDEO_FLAGS
        if (mw.time != 0L && !group) flags = flags or CTX_PLAY_FROM_START
        if (actionMode == null)
            showContext(requireActivity(), this, position, item.getTitle(), flags)
    }

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {}

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {
        launch {
            if (!isResumed) return@launch
            if (!mediaLibrary.isWorking) handler.sendEmptyMessage(UNSET_REFRESHING)
            updateEmptyView()
            setFabPlayVisibility(true)
            menu?.let { UiTools.updateSortTitles(it, viewModel.provider) }
        }
        restoreMultiSelectHelper()
    }

    override fun onItemFocused(v: View, item: MediaLibraryItem) {}

    fun updateSeenMediaMarker() {
        videoListAdapter.setSeenMediaMarkerVisible(Settings.getInstance(requireContext()).getBoolean("media_seen", true))
        videoListAdapter.notifyItemRangeChanged(0, videoListAdapter.itemCount - 1, UPDATE_SEEN)
    }

    override fun onCtxAction(position: Int, option: Int) {
        if (position >= videoListAdapter.itemCount) return
        val media = videoListAdapter.getItem(position) ?: return
        val activity = activity ?: return
        if (media is MediaWrapper) when (option) {
            CTX_PLAY_FROM_START -> playVideo(media, true)
            CTX_PLAY_AS_AUDIO -> playAudio(media)
            CTX_PLAY_ALL -> MediaUtils.playAll(requireContext(), viewModel.provider as VideosProvider, position, false)
            CTX_INFORMATION -> showInfoDialog(media)
            CTX_DELETE -> removeItem(media)
            CTX_APPEND -> {
                if (media.type == MediaLibraryItem.TYPE_FOLDER) {
                    launch { viewModel.append(position) }
                } else MediaUtils.appendMedia(activity, media)
            }
            CTX_PLAY_NEXT -> MediaUtils.insertNext(requireActivity(), media.tracks)
            CTX_DOWNLOAD_SUBTITLES -> MediaUtils.getSubs(requireActivity(), media)
            CTX_ADD_TO_PLAYLIST -> {
                if (media.type == MediaLibraryItem.TYPE_FOLDER) {
                    viewModel.addToPlaylist(requireActivity(), position)
                } else UiTools.addToPlaylist(requireActivity(), media.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            }
            CTX_PLAY -> launch { viewModel.play(position) }
        }
    }

    private val handler = VideoGridFragmentHandler(WeakReference(this))

    private val thumbObs = Observer<AbstractMediaWrapper> { media ->
        if (!::videoListAdapter.isInitialized) return@Observer
        val position = viewModel.provider.pagedList.value?.indexOf(media) ?: return@Observer
        val item = videoListAdapter.getItem(position) as? MediaWrapper
        item?.run {
            artworkURL = media.artworkURL
            videoListAdapter.notifyItemChanged(position)
        }
    }
}
