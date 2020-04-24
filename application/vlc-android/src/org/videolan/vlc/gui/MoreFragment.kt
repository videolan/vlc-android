/*
 * ************************************************************************
 *  MoreFragment.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui

import android.content.Intent
import android.os.Bundle
import android.util.SparseBooleanArray
import android.view.*
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.android.synthetic.main.more_fragment.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.*
import org.videolan.vlc.gui.network.IStreamsFragmentDelegate
import org.videolan.vlc.gui.network.KeyboardListener
import org.videolan.vlc.gui.network.MRLAdapter
import org.videolan.vlc.gui.network.StreamsFragmentDelegate
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.gui.view.TitleListView
import org.videolan.vlc.interfaces.IHistory
import org.videolan.vlc.interfaces.IRefreshable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.launchWhenStarted
import org.videolan.vlc.viewmodels.HistoryModel
import org.videolan.vlc.viewmodels.StreamsModel

private const val TAG = "VLC/HistoryFragment"
private const val KEY_SELECTION = "key_selection"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class MoreFragment : BaseFragment(), IRefreshable, IHistory, SwipeRefreshLayout.OnRefreshListener,
        IStreamsFragmentDelegate by StreamsFragmentDelegate() {

    private lateinit var streamsAdapter: MRLAdapter
    private lateinit var historyEntry: TitleListView
    private lateinit var streamsEntry: TitleListView
    private lateinit var viewModel: HistoryModel
    private lateinit var streamsViewModel: StreamsModel
    private lateinit var multiSelectHelper: MultiSelectHelper<MediaWrapper>
    private val historyAdapter: HistoryAdapter = HistoryAdapter(true)
    override fun hasFAB() = false
    fun getMultiHelper(): MultiSelectHelper<HistoryModel>? = historyAdapter.multiSelectHelper as? MultiSelectHelper<HistoryModel>
    private var savedSelection = SparseBooleanArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (savedInstanceState?.getParcelable<SparseBooleanArrayParcelable>(KEY_SELECTION))?.let { savedSelection = it.data }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.more_fragment, container, false)
    }

    override fun getTitle() = getString(R.string.history)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyEntry = view.findViewById(R.id.history_entry)
        viewModel = ViewModelProvider(requireActivity(), HistoryModel.Factory(requireContext())).get(HistoryModel::class.java)
        viewModel.dataset.observe(viewLifecycleOwner, Observer<List<MediaWrapper>> { list ->
            list?.let {
                historyAdapter.update(it)
                if (list.isEmpty()) historyEntry.setGone() else {
                    historyEntry.setVisible()
                    historyEntry.loading.state = EmptyLoadingState.NONE
                }
                if (list.isNotEmpty()) historyEntry.actionButton.setVisible() else historyEntry.actionButton.setGone()
            }
            restoreMultiSelectHelper()
        })
        viewModel.loading.observe(viewLifecycleOwner) {
            lifecycleScope.launchWhenStarted {
                if (it) delay(300L)
                (activity as? MainActivity)?.refreshing = it
                if (it) historyEntry.loading.state = EmptyLoadingState.LOADING
            }
        }
        historyAdapter.updateEvt.observe(viewLifecycleOwner) {
            swipeRefreshLayout.isRefreshing = false
        }
        historyAdapter.events.onEach { it.process() }.launchWhenStarted(lifecycleScope)

        streamsEntry = view.findViewById(R.id.streams_entry)
        streamsViewModel = ViewModelProvider(requireActivity(), StreamsModel.Factory(requireContext(), showDummy = true)).get(StreamsModel::class.java)
        setup(this, streamsViewModel, object : KeyboardListener {
            override fun hideKeyboard() {}
        })

        streamsAdapter = MRLAdapter(getlistEventActor(), inCards = true)
        streamsAdapter.setOnDummyClickListener {
            val i = Intent(activity, SecondaryActivity::class.java)
            i.putExtra("fragment", SecondaryActivity.STREAMS)
            requireActivity().startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
        }
        streamsViewModel.dataset.observe(requireActivity(), Observer {
            streamsAdapter.update(it)
            streamsEntry.loading.state = EmptyLoadingState.NONE

        })
        streamsViewModel.loading.observe(requireActivity(), Observer {
            lifecycleScope.launchWhenStarted {
                if (it) delay(300L)
                (activity as? MainActivity)?.refreshing = it
                if (it) streamsEntry.loading.state = EmptyLoadingState.LOADING
            }
        })

        settingsButton.setOnClickListener {
            requireActivity().startActivityForResult(Intent(activity, PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
        }
        aboutButton.setOnClickListener {
            val i = Intent(activity, SecondaryActivity::class.java)
            i.putExtra("fragment", SecondaryActivity.ABOUT)
            requireActivity().startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
        (activity as? ContentActivity)?.setTabLayoutVisibility(false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        historyEntry.list.adapter = historyAdapter
        historyEntry.list.nextFocusUpId = R.id.ml_menu_search
        historyEntry.list.nextFocusLeftId = android.R.id.list
        historyEntry.list.nextFocusRightId = android.R.id.list
        historyEntry.list.nextFocusForwardId = android.R.id.list

        streamsEntry.list.adapter = streamsAdapter

        historyEntry.setOnActionClickListener {
            val i = Intent(activity, SecondaryActivity::class.java)
            i.putExtra("fragment", SecondaryActivity.HISTORY)
            requireActivity().startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
        }

        multiSelectHelper = historyAdapter.multiSelectHelper
        historyEntry.list.requestFocus()
        registerForContextMenu(historyEntry.list)
        swipeRefreshLayout.setOnRefreshListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        getMultiHelper()?.let {
            outState.putParcelable(KEY_SELECTION, SparseBooleanArrayParcelable(it.selectionMap))
        }
        super.onSaveInstanceState(outState)
    }

    override fun refresh() = viewModel.refresh()

    override fun onRefresh() {
        refresh()
    }

    override fun isEmpty(): Boolean {
        return historyAdapter.isEmpty()
    }

    override fun clearHistory() {
        viewModel.clearHistory()
        Medialibrary.getInstance().clearHistory()
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return false
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val selectionCount = multiSelectHelper.getSelectionCount()
        if (selectionCount == 0) {
            stopActionMode()
            return false
        }
        menu.findItem(R.id.action_history_info).isVisible = selectionCount == 1
        menu.findItem(R.id.action_history_append).isVisible = PlaylistManager.hasMedia()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        if (!isStarted()) return false
        val selection = multiSelectHelper.getSelection()
        if (selection.isNotEmpty()) {
            when (item?.itemId) {
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

    override fun onDestroyActionMode(mode: ActionMode?) {
        actionMode = null
        multiSelectHelper.clearSelection()
    }

    private fun restoreMultiSelectHelper() {
        getMultiHelper()?.let {

            if (savedSelection.size() > 0) {
                var hasOneSelected = false
                for (i in 0 until savedSelection.size()) {

                    it.selectionMap.append(savedSelection.keyAt(i), savedSelection.valueAt(i))
                    if (savedSelection.valueAt(i)) hasOneSelected = true
                }
                if (hasOneSelected) startActionMode()
                savedSelection.clear()
            }
        }
    }

    private fun Click.process() {
        val item = viewModel.dataset.get(position)
        when (this) {
            is SimpleClick -> onClick(position, item)
            is LongClick -> onLongClick(position, item)
            is ImageClick -> {
                if (actionMode != null) onClick(position, item)
                else onLongClick(position, item)
            }
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
}
