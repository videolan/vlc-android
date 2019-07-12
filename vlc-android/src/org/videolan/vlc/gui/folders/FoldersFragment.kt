package org.videolan.vlc.gui.folders

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.folders_fragment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import org.videolan.medialibrary.interfaces.media.AbstractFolder
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.getAll
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.mobile.FoldersViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class FoldersFragment : MediaBrowserFragment<FoldersViewModel>(), CtxActionReceiver {

    private lateinit var adapter: FoldersAdapter

    private val actor = actor<FolderAction> {
        for (action in channel) when(action) {
            is FolderClick -> {
                if (actionMode != null) {
                    adapter.multiSelectHelper.toggleSelection(action.position)
                    invalidateActionMode()
                } else {
                    val i = Intent(activity, SecondaryActivity::class.java)
                    i.putExtra("fragment", SecondaryActivity.VIDEO_GROUP_LIST)
                    i.putExtra(KEY_FOLDER, action.folder)
                    activity?.startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
                }
            }
            is FolderLongClick -> {
                adapter.multiSelectHelper.toggleSelection(action.position, true)
                if (actionMode == null) {
                    startActionMode()
                }
            }
            is FolderCtxClick -> {
                showContext(requireActivity(), this@FoldersFragment, action.position, action.folder.title, CTX_FOLDER_FLAGS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!this::adapter.isInitialized) {
            adapter = FoldersAdapter(actor)
            viewModel = getViewModel()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.folders_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        folders_list.layoutManager = LinearLayoutManager(view.context, RecyclerView.VERTICAL, false)
        folders_list.adapter = adapter
        swipeRefreshLayout.setOnRefreshListener { activity?.reloadLibrary() }
        viewModel.provider.pagedList.observe(requireActivity(), Observer {
            swipeRefreshLayout.isRefreshing = false
            adapter.submitList(it)
        })
    }

    override fun onStart() {
        super.onStart()
        setFabPlayVisibility(true)
        fabPlay?.setImageResource(R.drawable.ic_fab_play)
    }


    override fun getTitle(): String = getString(R.string.video)

    override fun onRefresh() = viewModel.refresh()

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.ml_menu_last_playlist)?.isVisible = true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.ml_menu_last_playlist -> {
            MediaUtils.loadlastPlaylist(activity, PLAYLIST_TYPE_VIDEO)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onCtxAction(position: Int, option: Int) {
        when (option) {
            CTX_PLAY -> launch { viewModel.play(position) }
            CTX_APPEND -> launch { viewModel.append(position) }
            CTX_ADD_TO_PLAYLIST -> viewModel.provider.pagedList.value?.get(position)?.let { UiTools.addToPlaylist(requireActivity(), it.getAll()) }
        }
    }

    override fun onFabPlayClick(view: View) {
        MediaUtils.playAllTracks(context, viewModel.provider, 0, false)
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) : Boolean {
        mode?.apply { menuInflater.inflate(R.menu.action_mode_folder, menu) }
        return true
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

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem) : Boolean {
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

    override fun onDestroyActionMode(mode: ActionMode?) {
        actionMode = null
        adapter.multiSelectHelper.clearSelection()
    }
}

sealed class FolderAction
class FolderClick(val position: Int, val folder: AbstractFolder) : FolderAction()
class FolderLongClick(val position: Int, val folder: AbstractFolder) : FolderAction()
class FolderCtxClick(val position: Int, val folder: AbstractFolder) : FolderAction()