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
import android.view.*
import android.widget.Button
import androidx.appcompat.view.ActionMode
import androidx.cardview.widget.CardView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import org.videolan.libvlc.Dialog
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.Click
import org.videolan.vlc.gui.helpers.ImageClick
import org.videolan.vlc.gui.helpers.LongClick
import org.videolan.vlc.gui.helpers.SimpleClick
import org.videolan.vlc.gui.helpers.UiTools.showDonations
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
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.IDialogManager
import org.videolan.vlc.util.launchWhenStarted
import org.videolan.vlc.viewmodels.HistoryModel
import org.videolan.vlc.viewmodels.StreamsModel

private const val TAG = "VLC/HistoryFragment"
private const val KEY_SELECTION = "key_selection"

class MoreFragment : BaseFragment(), IRefreshable, IHistory, IDialogManager,
        IStreamsFragmentDelegate by StreamsFragmentDelegate() {

    private lateinit var streamsAdapter: MRLAdapter
    private lateinit var historyEntry: TitleListView
    private lateinit var streamsEntry: TitleListView
    private lateinit var settingsButton: Button
    private lateinit var aboutButton: Button
    private lateinit var donationsButton: CardView
    private lateinit var viewModel: HistoryModel
    private lateinit var streamsViewModel: StreamsModel
    private lateinit var multiSelectHelper: MultiSelectHelper<MediaWrapper>
    private val historyAdapter: HistoryAdapter = HistoryAdapter(true)
    override fun hasFAB() = false
    @Suppress("UNCHECKED_CAST")
    private fun getMultiHelper(): MultiSelectHelper<HistoryModel>? = historyAdapter.multiSelectHelper as? MultiSelectHelper<HistoryModel>
    private var savedSelection = ArrayList<Int>()
    private val dialogsDelegate = DialogDelegate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (savedInstanceState?.getIntegerArrayList(KEY_SELECTION))?.let { savedSelection = it }
        dialogsDelegate.observeDialogs(this, this)
        viewModel = ViewModelProvider(requireActivity(), HistoryModel.Factory(requireContext()))[HistoryModel::class.java]
        streamsViewModel = ViewModelProvider(requireActivity(), StreamsModel.Factory(requireContext(), showDummy = true))[StreamsModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.more_fragment, container, false)
    }

    override fun getTitle() = getString(R.string.history)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyEntry = view.findViewById(R.id.history_entry)
        settingsButton = view.findViewById(R.id.settingsButton)
        aboutButton = view.findViewById(R.id.aboutButton)
        donationsButton = view.findViewById(R.id.donationsButton)
        if (!Settings.getInstance(requireActivity()).getBoolean(PLAYBACK_HISTORY, true)) historyEntry.setGone()
        viewModel.dataset.observe(viewLifecycleOwner) { list ->
            list?.let {
                historyAdapter.update(it)
                if (list.isEmpty()) historyEntry.setGone() else {
                    historyEntry.setVisible()
                    historyEntry.loading.state = EmptyLoadingState.NONE
                }
                if (list.isNotEmpty()) historyEntry.actionButton.setVisible() else historyEntry.actionButton.setGone()
            }
            restoreMultiSelectHelper()
        }
        viewModel.loading.observe(viewLifecycleOwner) {
            lifecycleScope.launchWhenStarted {
                if (it) delay(300L)
                (activity as? MainActivity)?.refreshing = it
                if (it) historyEntry.loading.state = EmptyLoadingState.LOADING
            }
        }
        historyAdapter.events.onEach { it.process() }.launchWhenStarted(lifecycleScope)

        streamsEntry = view.findViewById(R.id.streams_entry)
        setup(this, streamsViewModel, object : KeyboardListener {
            override fun hideKeyboard() {}
        })

        streamsAdapter = MRLAdapter(getlistEventActor(), inCards = true)
        streamsAdapter.setOnDummyClickListener {
            val i = Intent(activity, SecondaryActivity::class.java)
            i.putExtra("fragment", SecondaryActivity.STREAMS)
            requireActivity().startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
        }
        streamsViewModel.dataset.observe(viewLifecycleOwner) {
            streamsAdapter.update(it)
            streamsEntry.loading.state = EmptyLoadingState.NONE

        }
        streamsViewModel.loading.observe(viewLifecycleOwner) {
            lifecycleScope.launchWhenStarted {
                if (it) delay(300L)
                (activity as? MainActivity)?.refreshing = it
                if (it) streamsEntry.loading.state = EmptyLoadingState.LOADING
            }
        }
        streamsEntry.actionButton.setVisible()
        streamsEntry.setOnActionClickListener {
            val i = Intent(requireActivity(), SecondaryActivity::class.java)
            i.putExtra("fragment", SecondaryActivity.STREAMS)
            requireActivity().startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
        }

        settingsButton.setOnClickListener {
            requireActivity().startActivityForResult(Intent(requireActivity(), PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
        }
        aboutButton.setOnClickListener {
            val i = Intent(requireActivity(), SecondaryActivity::class.java)
            i.putExtra("fragment", SecondaryActivity.ABOUT)
            requireActivity().startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
        }
//        VLCBilling.getInstance(requireActivity().application).addStatusListener {
//            manageDonationVisibility()
//        }
        manageDonationVisibility()
        donationsButton.setOnClickListener {
            requireActivity().showDonations()
        }

        historyEntry.list.adapter = historyAdapter
        historyEntry.list.nextFocusUpId = R.id.ml_menu_search
        historyEntry.list.nextFocusLeftId = android.R.id.list
        historyEntry.list.nextFocusRightId = android.R.id.list
        historyEntry.list.nextFocusForwardId = android.R.id.list

        streamsEntry.list.adapter = streamsAdapter

        historyEntry.setOnActionClickListener {
            val i = Intent(requireActivity(), SecondaryActivity::class.java)
            i.putExtra("fragment", SecondaryActivity.HISTORY)
            requireActivity().startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
        }

        multiSelectHelper = historyAdapter.multiSelectHelper
        historyEntry.list.requestFocus()
        registerForContextMenu(historyEntry.list)
    }

    private fun manageDonationVisibility() {
        if (activity == null) return
//         if (VLCBilling.getInstance(requireActivity().application).status == BillingStatus.FAILURE ||  VLCBilling.getInstance(requireActivity().application).skuDetails.isEmpty()) donationsButton.setGone() else donationsButton.setVisible()
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
        (activity as? ContentActivity)?.setTabLayoutVisibility(false)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        getMultiHelper()?.let {
            outState.putIntegerArrayList(KEY_SELECTION, it.selectionMap)
        }
        super.onSaveInstanceState(outState)
    }

    override fun refresh() = viewModel.refresh()


    override fun isEmpty(): Boolean {
        return historyAdapter.isEmpty()
    }

    override fun clearHistory() {
        viewModel.clearHistory()
        Medialibrary.getInstance().clearHistory(Medialibrary.HISTORY_TYPE_GLOBAL)
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
        mode.title = requireActivity().getString(R.string.selection_count, selectionCount)
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

            if (savedSelection.size > 0) {
                var hasOneSelected = false
                for (i in 0 until savedSelection.size) {

                    it.selectionMap.addAll(savedSelection)
                    hasOneSelected = savedSelection.isNotEmpty()
                }
                if (hasOneSelected) startActionMode()
                savedSelection.clear()
            }
        }
    }

    private fun Click.process() {
        if (position >= 0) {
            val item = viewModel.dataset.get(position)
            when (this) {
                is SimpleClick -> onClick(position, item)
                is LongClick -> onLongClick(position, item)
                is ImageClick -> {
                    if (actionMode != null) onClick(position, item)
                    else onLongClick(position, item)
                }
                else -> {}
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

    override fun fireDialog(dialog: Dialog) {
        DialogActivity.dialog = dialog
        startActivity(Intent(DialogActivity.KEY_DIALOG, null, requireActivity(), DialogActivity::class.java))
    }

    override fun dialogCanceled(dialog: Dialog?) { }
}
