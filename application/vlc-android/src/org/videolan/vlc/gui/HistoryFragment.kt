/*****************************************************************************
 * HistoryFragment.kt
 *
 * Copyright © 2012-2019 VLC authors and VideoLAN
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
package org.videolan.vlc.gui

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.KEY_AUDIO_LAST_PLAYLIST
import org.videolan.resources.KEY_MEDIA_LAST_PLAYLIST
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.helpers.*
import org.videolan.vlc.interfaces.IHistory
import org.videolan.vlc.interfaces.IListEventsHandler
import org.videolan.vlc.interfaces.IRefreshable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.launchWhenStarted
import org.videolan.vlc.util.showParentFolder
import org.videolan.vlc.viewmodels.HistoryModel

private const val TAG = "VLC/HistoryFragment"

class HistoryFragment : MediaBrowserFragment<HistoryModel>(), IRefreshable, IHistory, SwipeRefreshLayout.OnRefreshListener, IListEventsHandler {

    private lateinit var cleanMenuItem: MenuItem
    private lateinit var multiSelectHelper: MultiSelectHelper<MediaWrapper>
    private val historyAdapter: HistoryAdapter = HistoryAdapter(listEventsHandler = this).apply { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), HistoryModel.Factory(requireContext()))[HistoryModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.history_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        list = view.findViewById(R.id.list)
        empty = view.findViewById(R.id.empty)
        viewModel.dataset.observe(viewLifecycleOwner) { list ->
            list?.let {
                historyAdapter.update(it)
                updateEmptyView()
                if (::cleanMenuItem.isInitialized) {
                    cleanMenuItem.isVisible = list.isNotEmpty()
                }
            }
        }
        viewModel.loading.observe(viewLifecycleOwner) {
            (activity as? MainActivity)?.refreshing = it
        }
        historyAdapter.updateEvt.observe(viewLifecycleOwner) {
            UiTools.updateSortTitles(this)
            swipeRefreshLayout.isRefreshing = false
            restoreMultiSelectHelper()
        }
        historyAdapter.events.onEach { it.process() }.launchWhenStarted(lifecycleScope)
        list.layoutManager = LinearLayoutManager(activity)
        list.adapter = historyAdapter
        list.nextFocusUpId = R.id.ml_menu_search
        list.nextFocusLeftId = android.R.id.list
        list.nextFocusRightId = android.R.id.list
        list.nextFocusForwardId = android.R.id.list

        itemTouchHelper = ItemTouchHelper(SwipeDragItemTouchHelperCallback(historyAdapter))
        itemTouchHelper.attachToRecyclerView(list)

        multiSelectHelper = historyAdapter.multiSelectHelper
        list.requestFocus()
        registerForContextMenu(list)
        swipeRefreshLayout.setOnRefreshListener(this)
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_option_history, menu)
        super.onCreateOptionsMenu(menu, inflater)
        cleanMenuItem = menu.findItem(R.id.ml_menu_clean)
        cleanMenuItem.isVisible = !isEmpty()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.ml_menu_clean).isVisible = Settings.getInstance(requireActivity()).getBoolean(PLAYBACK_HISTORY, true)
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ml_menu_clean -> {

                val dialog = ConfirmDeleteDialog.newInstance(title = getString(R.string.clear_playback_history), description = getString(R.string.clear_history_message), buttonText = getString(R.string.clear_history))
                dialog.show((activity as FragmentActivity).supportFragmentManager, RenameDialog::class.simpleName)
                dialog.setListener {
                    clearHistory()
                    requireActivity().finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun setFabPlayVisibility(enable: Boolean) {
        if (fabPlay != null) fabPlay!!.hide()
    }

    override fun refresh() = viewModel.refresh()

    override fun onRefresh() {
        refresh()
    }

    override fun getTitle(): String {
        return getString(R.string.history)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getMultiHelper(): MultiSelectHelper<HistoryModel>? = historyAdapter.multiSelectHelper as? MultiSelectHelper<HistoryModel>

    override fun clear() {}

    private fun updateEmptyView() {
        if (viewModel.isEmpty()) {
            swipeRefreshLayout.visibility = View.GONE
            empty.visibility = View.VISIBLE
        } else {
            empty.visibility = View.GONE
            swipeRefreshLayout.visibility = View.VISIBLE
        }
    }

    override fun isEmpty(): Boolean {
        return historyAdapter.isEmpty()
    }

    override fun clearHistory() {
        mediaLibrary.clearHistory(Medialibrary.HISTORY_TYPE_GLOBAL)
        viewModel.clearHistory()
        Settings.getInstance(requireActivity()).edit().remove(KEY_AUDIO_LAST_PLAYLIST).remove(KEY_MEDIA_LAST_PLAYLIST).apply()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        multiSelectHelper.toggleActionMode(true, historyAdapter.itemCount)
        mode.menuInflater.inflate(R.menu.action_mode_history, menu)
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val selectionCount = multiSelectHelper.getSelectionCount()
        if (selectionCount == 0) {
            stopActionMode()
            return false
        }
        lifecycleScope.launch { fillActionMode(requireActivity(), mode, multiSelectHelper as MultiSelectHelper<MediaLibraryItem>) }
        menu.findItem(R.id.action_history_info).isVisible = selectionCount == 1
        menu.findItem(R.id.action_history_append).isVisible = PlaylistManager.hasMedia()
        menu.findItem(R.id.action_go_to_folder).isVisible = selectionCount == 1 && multiSelectHelper.getSelection().first().uri.retrieveParent() != null
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (!isStarted()) return false
        val selection = multiSelectHelper.getSelection()
        if (selection.isNotEmpty()) {
            when (item.itemId) {
                R.id.action_history_play -> MediaUtils.openList(activity, selection, 0)
                R.id.action_history_append -> MediaUtils.appendMedia(activity, selection)
                R.id.action_history_info -> showInfoDialog(selection.first())
                R.id.action_go_to_folder -> showParentFolder(selection.first())
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
        multiSelectHelper.toggleActionMode(false, historyAdapter.itemCount)
        actionMode = null
        multiSelectHelper.clearSelection()
    }

    private fun Click.process() {
        val item = viewModel.dataset.get(position)
        when(this) {
            is SimpleClick -> onClick(position, item)
            is LongClick -> onLongClick(position, item)
            is ImageClick -> {
                if (actionMode != null) onClick(position, item)
                else onLongClick(position, item)
            }
            else -> {}
        }
    }

    fun onClick(position: Int, item: MediaWrapper) {
        if (KeyHelper.isShiftPressed && actionMode == null) {
            onLongClick(position, item)
            return
        }
        if (actionMode != null) {
            multiSelectHelper.toggleSelection(position)
            historyAdapter.notifyItemChanged(position, item)
            invalidateActionMode()
            return
        }
        if (position != 0) viewModel.moveUp(item)
        MediaUtils.openMedia(requireContext(), item)
    }

    fun onLongClick(position: Int, item: MediaWrapper) {
        multiSelectHelper.toggleSelection(position, true)
        historyAdapter.notifyItemChanged(position, item)
        if (actionMode == null) startActionMode()
        invalidateActionMode()
    }

    override fun onRemove(position: Int, item: MediaLibraryItem) {
        viewModel.removeFromHistory(item as MediaWrapper)
    }

    override fun onMove(oldPosition: Int, newPosition: Int) {
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
    }
}
