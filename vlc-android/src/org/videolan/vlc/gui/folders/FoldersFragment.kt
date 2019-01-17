package org.videolan.vlc.gui.folders

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.folders_fragment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import org.videolan.medialibrary.media.Folder
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.getAll
import org.videolan.vlc.reload
import org.videolan.vlc.util.CTX_ADD_TO_PLAYLIST
import org.videolan.vlc.util.CTX_APPEND
import org.videolan.vlc.util.CTX_FOLDER_FLAGS
import org.videolan.vlc.util.CTX_PLAY
import org.videolan.vlc.viewmodels.paged.PagedFoldersModel
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class FoldersFragment : MediaBrowserFragment<PagedFoldersModel>(), CoroutineScope, CtxActionReceiver {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main.immediate

    private lateinit var adapter: FoldersAdapter

    private val actor = actor<FolderAction> {
        for (action in channel) when(action) {
            is FolderClick -> {
                if (mActionMode != null) {
                    adapter.multiSelectHelper.toggleSelection(action.position)
                    invalidateActionMode()
                } else {
                    val i = Intent(activity, SecondaryActivity::class.java)
                    i.putExtra("fragment", SecondaryActivity.VIDEO_GROUP_LIST)
                    i.putExtra("folder", action.folder)
                    activity?.startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
                }
            }
            is FolderLongClick -> {
                if (mActionMode == null) {
                    adapter.multiSelectHelper.toggleSelection(action.position)
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
            viewModel = ViewModelProviders.of(requireActivity(), PagedFoldersModel.Factory(requireContext(), Folder.TYPE_FOLDER_VIDEO)).get(PagedFoldersModel::class.java)
            viewModel.pagedList.observe(requireActivity(), Observer {
                mSwipeRefreshLayout?.isRefreshing = false
                adapter.submitList(it)
            })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.folders_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        folders_list.layoutManager = LinearLayoutManager(view.context, RecyclerView.VERTICAL, false)
        folders_list.adapter = adapter
        mSwipeRefreshLayout.setOnRefreshListener { activity?.reload() }
    }

    override fun getTitle() = getString(R.string.video)

    override fun onRefresh() {
        viewModel.refresh()
    }

    override fun onCtxAction(position: Int, option: Int) {
        when (option) {
            CTX_PLAY -> launch { viewModel.play(position) }
            CTX_APPEND -> launch { viewModel.append(position) }
            CTX_ADD_TO_PLAYLIST -> viewModel.pagedList.value?.get(position)?.let { UiTools.addToPlaylist(requireActivity(), it.getAll()) }
        }
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) : Boolean {
        mode?.apply { menuInflater.inflate(R.menu.action_mode_folder, menu) }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        val count = adapter.multiSelectHelper.getSelectionCount()
        if (count == 0) {
            stopActionMode()
            return false
        }
        menu?.findItem(R.id.action_video_append)?.isVisible = PlaylistManager.hasMedia()
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
        stopActionMode();
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        mActionMode = null
        adapter.multiSelectHelper.clearSelection()
    }

    override fun hasFAB() = false
}

sealed class FolderAction
class FolderClick(val position: Int, val folder: Folder) : FolderAction()
class FolderLongClick(val position: Int, val folder: Folder) : FolderAction()
class FolderCtxClick(val position: Int, val folder: Folder) : FolderAction()