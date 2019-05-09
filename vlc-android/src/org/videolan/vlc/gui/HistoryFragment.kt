/*****************************************************************************
 * HistoryFragment.java
 *
 * Copyright © 2012-2015 VLC authors and VideoLAN
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

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.android.synthetic.main.history_list.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.interfaces.IHistory
import org.videolan.vlc.interfaces.IRefreshable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.viewmodels.HistoryModel

private const val TAG = "VLC/HistoryFragment"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class HistoryFragment : MediaBrowserFragment<HistoryModel>(), IRefreshable, IHistory, SwipeRefreshLayout.OnRefreshListener, IEventsHandler {

    private val historyAdapter: HistoryAdapter = HistoryAdapter(this)

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.history_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity(), HistoryModel.Factory(requireContext())).get(HistoryModel::class.java)
        viewModel.dataset.observe(this, Observer<List<MediaWrapper>> { list ->  list?.let {
            historyAdapter.update(it)
            updateEmptyView()
        } })
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        list.layoutManager = LinearLayoutManager(activity)
        list.adapter = historyAdapter
        list.nextFocusUpId = R.id.ml_menu_search
        list.nextFocusLeftId = android.R.id.list
        list.nextFocusRightId = android.R.id.list
        list.nextFocusForwardId = android.R.id.list
        list.requestFocus()
        registerForContextMenu(list)
        swipeRefreshLayout!!.setOnRefreshListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater!!.inflate(R.menu.fragment_option_history, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.ml_menu_clean).isVisible = !isEmpty()
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ml_menu_clean -> {
                clearHistory()
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

    override fun clear() {}

    private fun updateEmptyView() {
        if (viewModel.isEmpty()) {
            swipeRefreshLayout?.visibility = View.GONE
            empty.visibility = View.VISIBLE
        } else {
            empty.visibility = View.GONE
            swipeRefreshLayout?.visibility = View.VISIBLE
        }
    }

    override fun isEmpty(): Boolean {
        return historyAdapter.isEmpty()
    }

    override fun clearHistory() {
        mediaLibrary.clearHistory()
        viewModel.clear()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.action_mode_history, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val selectionCount = historyAdapter.selection.size
        if (selectionCount == 0) {
            stopActionMode()
            return false
        }
        menu.findItem(R.id.action_history_info).isVisible = selectionCount == 1
        menu.findItem(R.id.action_history_append).isVisible = PlaylistManager.hasMedia()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val selection = historyAdapter.selection
        if (selection.isNotEmpty()) {
            when (item.itemId) {
                R.id.action_history_play -> MediaUtils.openList(activity, selection, 0)
                R.id.action_history_append -> MediaUtils.appendMedia(activity, selection)
                R.id.action_history_info -> showInfoDialog(selection[0])
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
        var index = -1
        for (media in viewModel.dataset.value) {
            ++index
            if (media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                media.removeStateFlags(MediaLibraryItem.FLAG_SELECTED)
                historyAdapter.notifyItemChanged(index, media)
            }
        }
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED)
            historyAdapter.notifyItemChanged(position, item)
            invalidateActionMode()
            return
        }
        if (position != 0) viewModel.moveUp(item as MediaWrapper)
        MediaUtils.openMedia(v.context, item as MediaWrapper)
    }

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
        if (actionMode != null) return false
        item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED)
        historyAdapter.notifyItemChanged(position, item)
        startActionMode()
        return true
    }

    override fun onImageClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            onClick(v, position, item)
            return
        }
        onLongClick(v, position, item)
    }

    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {}

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {}

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {
        UiTools.updateSortTitles(this)
        swipeRefreshLayout!!.isRefreshing = false
    }

    override fun onItemFocused(v: View, item: MediaLibraryItem) {}
}
