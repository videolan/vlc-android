package org.videolan.vlc.gui.videogroups

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.videogroups_fragment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup
import org.videolan.tools.MultiSelectHelper
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VideogroupsFragmentBinding
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.getAll
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.mobile.VideogroupsViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class VideoGroupsFragment : MediaBrowserFragment<VideogroupsViewModel>(), CtxActionReceiver {

    private lateinit var binding: VideogroupsFragmentBinding
    private lateinit var adapter: VideoGroupsAdapter

    private val actor = actor<VideoGroupAction> {
        for (action in channel) when (action) {
            is VideoGroupClick -> {
                if (actionMode != null) {
                    adapter.multiSelectHelper.toggleSelection(action.position)
                    invalidateActionMode()
                } else {
                    if (action.group.mediaCount() == 1) viewModel.play(action.position)
                    else {
                        val i = Intent(activity, SecondaryActivity::class.java)
                        i.putExtra("fragment", SecondaryActivity.VIDEO_GROUP_LIST)
                        i.putExtra(KEY_GROUP, action.group)
                        activity?.startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
                    }
                }
            }
            is VideoGroupLongClick -> {
                adapter.multiSelectHelper.toggleSelection(action.position, true)
                if (actionMode == null) {
                    startActionMode()
                }
            }
            is VideoGroupCtxClick -> {
                if (action.group.mediaCount() == 1) {
                    context?.getFromMl { action.group.media(0, false, 1, 0) }?.get(0)?.let { media ->
                        showContext(requireActivity(), this@VideoGroupsFragment, action.position, media.title, CTX_VIDEO_GROUP_TEMP_FLAGS)
                    }
                } else
                    showContext(requireActivity(), this@VideoGroupsFragment, action.position, action.group.title, CTX_FOLDER_FLAGS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = VideoGroupsAdapter(actor)
        viewModel = getViewModel()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = VideogroupsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        groups_list.layoutManager = LinearLayoutManager(view.context, RecyclerView.VERTICAL, false)
        groups_list.adapter = adapter
        swipeRefreshLayout.setOnRefreshListener { activity?.reloadLibrary() }
        viewModel.provider.pagedList.observe(requireActivity(), Observer {
            swipeRefreshLayout.isRefreshing = false
            adapter.submitList(it)
            restoreMultiSelectHelper()
            updateEmptyView()
        })
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

    private fun updateEmptyView() {
        val empty = viewModel.isEmpty()
        val working = mediaLibrary.isWorking
        binding.loadingFlipper.visibility = if (empty && working) View.VISIBLE else View.GONE
        binding.loadingTitle.visibility = if (empty && working) View.VISIBLE else View.GONE
        binding.empty = empty && !working
    }

    override fun onStart() {
        super.onStart()
        setFabPlayVisibility(true)
        fabPlay?.setImageResource(R.drawable.ic_fab_play)
    }

    override fun getTitle() = getString(R.string.video)

    override fun getMultiHelper(): MultiSelectHelper<VideogroupsViewModel>? = if (::adapter.isInitialized) adapter.multiSelectHelper as? MultiSelectHelper<VideogroupsViewModel> else null

    override fun onRefresh() = viewModel.refresh()

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.ml_menu_last_playlist)?.isVisible = true
        menu.findItem(R.id.ml_menu_video_group).isVisible = true
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem): Boolean {
        if (!isStarted()) return false
        val selection = adapter.multiSelectHelper.getSelection()
        when (item.itemId) {
            R.id.action_folder_play -> viewModel.playSelection(selection)
            R.id.action_folder_append -> viewModel.appendSelection(selection)
            R.id.action_folder_add_playlist -> launch { UiTools.addToPlaylist(requireActivity(), withContext(Dispatchers.Default) { selection.getAll() }) }
            else -> return false
        }
        stopActionMode()
        return true
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        mode?.apply { menuInflater.inflate(R.menu.action_mode_folder, menu) }
        return true
    }

    override fun onFabPlayClick(view: View) {
        MediaUtils.playAllTracks(context, viewModel.provider, 0, false)
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = adapter.multiSelectHelper.getSelectionCount()
        if (count == 0) {
            stopActionMode()
            return false
        }
        menu.findItem(R.id.action_video_append)?.isVisible = PlaylistManager.hasMedia()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        actionMode = null
        adapter.multiSelectHelper.clearSelection()
    }

    override fun onCtxAction(position: Int, option: Int) {
        val item = adapter.getItem(position)
        if (item?.mediaCount() == 1) {
            launch {
                context?.getFromMl { item.media(0, false, 1, 0) }?.get(0)?.let { media ->
                    when (option) {
                        CTX_PLAY_FROM_START -> playVideo(media, true)
                        CTX_PLAY_AS_AUDIO -> playAudio(media)
                        CTX_INFORMATION -> showInfoDialog(media)
                        CTX_DELETE -> removeItem(media)
                        CTX_APPEND -> MediaUtils.appendMedia(activity, media)
                        CTX_PLAY_NEXT -> MediaUtils.insertNext(requireActivity(), media.tracks)
                        CTX_DOWNLOAD_SUBTITLES -> MediaUtils.getSubs(requireActivity(), media)
                        CTX_ADD_TO_PLAYLIST -> UiTools.addToPlaylist(requireActivity(), media.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
                        else -> {
                        }
                    }
                }

            }
        } else when (option) {
            CTX_PLAY -> viewModel.play(position)
            CTX_APPEND -> viewModel.append(position)
            CTX_ADD_TO_PLAYLIST -> viewModel.addToPlaylist(requireActivity(), position)
        }
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
}

sealed class VideoGroupAction
class VideoGroupClick(val position: Int, val group: AbstractVideoGroup) : VideoGroupAction()
class VideoGroupLongClick(val position: Int, val group: AbstractVideoGroup) : VideoGroupAction()
class VideoGroupCtxClick(val position: Int, val group: AbstractVideoGroup) : VideoGroupAction()