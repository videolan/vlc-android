/*
 * *************************************************************************
 *  SavePlaylist.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.util.parcelable
import org.videolan.resources.util.parcelableArray
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogPlaylistBinding
import org.videolan.vlc.gui.SimpleAdapter
import org.videolan.vlc.gui.dialogs.DuplicationWarningDialog.Companion.ADD_ALL
import org.videolan.vlc.gui.dialogs.DuplicationWarningDialog.Companion.ADD_NEW
import org.videolan.vlc.gui.dialogs.DuplicationWarningDialog.Companion.CANCEL
import org.videolan.vlc.gui.dialogs.DuplicationWarningDialog.Companion.NO_OPTION
import org.videolan.vlc.gui.dialogs.DuplicationWarningDialog.Companion.OPTION_KEY
import org.videolan.vlc.gui.dialogs.DuplicationWarningDialog.Companion.REQUEST_KEY
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.showPinIfNeeded
import org.videolan.vlc.providers.FileBrowserProvider
import org.videolan.vlc.util.isSchemeStreaming
import org.videolan.vlc.viewmodels.browser.TYPE_FILE
import org.videolan.vlc.viewmodels.browser.getBrowserModel
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList

class SavePlaylistDialog : VLCBottomSheetDialogFragment(), View.OnClickListener,
        TextView.OnEditorActionListener, SimpleAdapter.ClickHandler {
    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = false

    var selectedPlaylist: ArrayList<Playlist>? = null
    var nonDuplicateTracks: Array<MediaWrapper>? = null

    private var isLoading: Boolean = false
        set(value) {
            field = value
            if (::binding.isInitialized) binding.isLoading = value
        }
    private var filesText: String = ""
        set(value) {
            field = value
            if (::binding.isInitialized) binding.filesText = value
        }
    private lateinit var binding: DialogPlaylistBinding
    private lateinit var adapter: SimpleAdapter
    private lateinit var newTracks: Array<MediaWrapper>
    private lateinit var medialibrary: Medialibrary

    private var playlistIterator: Iterator<MediaLibraryItem>? = null
    private var currentItem: MediaLibraryItem? = null

    private val coroutineContextProvider: CoroutineContextProvider
    private val alreadyAdding = AtomicBoolean(false)

    override fun initialFocusedView(): View = binding.dialogPlaylistName.editText ?: binding.dialogPlaylistName

    init {
        SavePlaylistDialog.registerCreator { CoroutineContextProvider() }
        coroutineContextProvider = SavePlaylistDialog.get(0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { if (requireActivity().showPinIfNeeded()) dismiss() }
        medialibrary = Medialibrary.getInstance()
        adapter = SimpleAdapter(this)
        newTracks = try {
            @Suppress("UNCHECKED_CAST")
            val tracks = requireArguments().parcelableArray<MediaWrapper>(KEY_NEW_TRACKS) as Array<MediaWrapper>
            filesText = resources.getQuantityString(R.plurals.media_quantity, tracks.size, tracks.size)
            tracks
        } catch (e: Exception) {
            try {
                requireArguments().getString(KEY_FOLDER)?.let { folder ->

                    isLoading = true
                    val viewModel = getBrowserModel(category = TYPE_FILE, url = folder)
                    if (requireArguments().getBoolean(KEY_SUB_FOLDERS, false)) lifecycleScope.launchWhenStarted {
                        withContext(Dispatchers.IO) {
                            newTracks = (viewModel.provider as FileBrowserProvider).browseByUrl(folder).toTypedArray()
                            isLoading = false
                            filesText = resources.getQuantityString(R.plurals.media_quantity, newTracks.size, newTracks.size)
                        }
                    } else {
                        viewModel.dataset.observe(this) { mediaLibraryItems ->
                            newTracks = mediaLibraryItems.asSequence().map { it as MediaWrapper }.filter { it.type != MediaWrapper.TYPE_DIR }.toList().toTypedArray()
                            isLoading = false
                            filesText = resources.getQuantityString(R.plurals.media_quantity, newTracks.size, newTracks.size)
                        }
                    }
                }
                emptyArray()
            } catch (e: Exception) {
                emptyArray()
            }
        }
        selectedPlaylist = savedInstanceState?.getSerializable(SELECTED_PLAYLIST) as ArrayList<Playlist>?
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogPlaylistBinding.inflate(layoutInflater, container, false)
        binding.isLoading = isLoading
        binding.filesText = filesText
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.dialogPlaylistSave.setOnClickListener(this)
        binding.dialogPlaylistCreate.setOnClickListener(this)

        binding.dialogPlaylistName.editText!!.setOnEditorActionListener(this)
        binding.dialogPlaylistName.editText!!.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                addNewPlaylist()
                true
            } else false
        }
        binding.list.layoutManager = LinearLayoutManager(view.context)
        binding.list.adapter = adapter
        adapter.submitList(listOf<MediaLibraryItem>(*medialibrary.getPlaylists(Playlist.Type.All, false).apply { forEach { it.description = resources.getQuantityString(R.plurals.media_quantity, it.tracksCount, it.tracksCount) } }))
        if (!Tools.isArrayEmpty(newTracks)) binding.dialogPlaylistSave.setText(R.string.save)
        updateEmptyView()
        parentFragmentManager.setFragmentResultListener(
                REQUEST_KEY,
                viewLifecycleOwner) { _: String, bundle: Bundle ->
            when (bundle.getInt(OPTION_KEY)) {
                ADD_ALL -> {
                    processNextItem(newTracks)
                }
                ADD_NEW -> {
                    processNextItem(nonDuplicateTracks!!)
                }
                CANCEL, NO_OPTION -> {
                    // do nothing
                }
            }
        }
        binding.replaceSwitch.isChecked = Settings.getInstance(requireActivity()).getBoolean(PLAYLIST_REPLACE, false)
        binding.replaceSwitch.setOnCheckedChangeListener { _, isChecked ->
            Settings.getInstance(requireActivity()).putSingle(PLAYLIST_REPLACE, isChecked)
        }
        selectedPlaylist?.let {
            it.forEachIndexed { index, playlist ->
                adapter.multiSelectHelper.toggleSelection(index)
            }
            if (it.size > 0){
                binding.selectedPlaylistCount.text = resources.getString(R.string.selection_count, it.size)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedPlaylist = adapter.multiSelectHelper.getSelection() as ArrayList<Playlist>
        outState.putSerializable(SELECTED_PLAYLIST, selectedPlaylist)
    }

    private fun updateEmptyView() {
        binding.empty.visibility = if (adapter.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.dialog_playlist_save -> {
                if (adapter.multiSelectHelper.getSelection().isNotEmpty()) {
                    val selectedItems = adapter.multiSelectHelper.getSelection()
                    playlistIterator = selectedItems.iterator()
                    saveToExistingPlaylists(selectedItems)
                }
            }

            R.id.dialog_playlist_create -> addNewPlaylist()
        }
    }

    private fun processNextItem(tracks: Array<MediaWrapper>) {
        if (playlistIterator != null && playlistIterator!!.hasNext()) {
            val item = playlistIterator!!.next()
            currentItem = item
            savePlaylist(item as Playlist, tracks)
        } else {
            playlistIterator = null
            currentItem = null
            dismiss()
        }
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_SEND) addNewPlaylist()
        return false
    }

    private fun addNewPlaylist() {
        if (alreadyAdding.getAndSet(true)) return
        val name = binding.dialogPlaylistName.editText?.text?.toString()?.trim { it <= ' ' }
                ?: return
        UiTools.setKeyboardVisibility(binding.dialogPlaylistName, false)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { medialibrary.getPlaylistByName(name) }?.let {
                binding.dialogPlaylistName.error = getString(R.string.playlist_existing, it.title)
                alreadyAdding.set(false)
                return@launch
            }
            medialibrary.createPlaylist(name, Settings.includeMissing, false)
            binding.dialogPlaylistName.editText?.text?.clear()
            adapter.submitList(listOf<MediaLibraryItem>(*medialibrary.getPlaylists(Playlist.Type.All, false).apply { forEach { it.description = resources.getQuantityString(R.plurals.media_quantity, it.tracksCount, it.tracksCount) } }))
            alreadyAdding.set(false)
            binding.dialogPlaylistName.error = null

        }
    }

    private fun savePlaylist(playlist: Playlist, tracks: Array<MediaWrapper>) {
        AppScope.launch(coroutineContextProvider.IO) {
            if (tracks.isEmpty()) return@launch
            val ids = LinkedList<Long>()
            for (mw in tracks) {
                val id = mw.id
                if (id == 0L) {
                    var media = medialibrary.getMedia(mw.uri)
                    if (media != null) {
                        ids.add(media.id)
                        media.title = mw.title
                    } else {
                        media = if (isSchemeStreaming(mw.location))
                            medialibrary.addStream(mw.location, mw.title)
                        else
                            medialibrary.addMedia(mw.location, -1L)
                        if (media != null) ids.add(media.id)
                    }
                } else
                    ids.add(id)
            }
            if (binding.replaceSwitch.isChecked) {
                val name = playlist.title
                playlist.delete()
                val newPlaylist = medialibrary.createPlaylist(name, Settings.includeMissing, false)
                newPlaylist.append(ids)
            } else playlist.append(ids)
            withContext(coroutineContextProvider.Main) {
                if (playlistIterator == null) dismiss()
                else processNextItem(tracks)
            }
        }
    }

    private fun saveToExistingPlaylists(items: List<MediaLibraryItem>) {
        val highlightedItemsCounts = newTracks.size
        var shouldShowThreeOptions = false
        val titles : ArrayList<String> = ArrayList()
        val duplicationMessages = items.filter { playlist ->
            nonDuplicateTracks = getNonDuplicateTracks(playlist.tracks, newTracks)
            val duplicateItemsCount = newTracks.size - nonDuplicateTracks!!.size
            duplicateItemsCount != 0
        }.mapIndexed { index, playlist ->
            nonDuplicateTracks = getNonDuplicateTracks(playlist.tracks, newTracks)
            val duplicateItemsCount = newTracks.size - nonDuplicateTracks!!.size
            val pluralSecondary: Int
            val secondaryMessage: String
            titles.add(playlist.title)
            if(duplicateItemsCount < highlightedItemsCounts){
                shouldShowThreeOptions = true
                pluralSecondary = R.plurals.duplicate_three_options_secondary
                secondaryMessage = if (duplicateItemsCount == 1)
                    resources.getQuantityString(pluralSecondary, duplicateItemsCount,playlist.title)
                else
                    resources.getQuantityString(pluralSecondary, duplicateItemsCount, duplicateItemsCount, playlist.title)
                secondaryMessage
            }else{
                pluralSecondary = R.plurals.duplicate_two_options_secondary
                secondaryMessage = resources.getQuantityString(pluralSecondary, duplicateItemsCount, playlist.title)
                secondaryMessage
            }
        }

        if (duplicationMessages.isEmpty() || binding.replaceSwitch.isChecked) {
            processNextItem(newTracks)
        } else {
            val warningDialog = DuplicationWarningDialog.newInstance(shouldShowThreeOptions,titles, ArrayList(duplicationMessages))
            warningDialog.show(requireActivity().supportFragmentManager, "duplicationWarningDialog")
        }
    }

    override fun onClick(item: MediaLibraryItem, position: Int) {
        adapter.multiSelectHelper.toggleSelection(position)
        binding.selectedPlaylistCount.text = resources.getString(R.string.selection_count, adapter.multiSelectHelper.getSelection().size)
    }

    private fun getNonDuplicateTracks(currentTracks: Array<MediaWrapper>, newTracks: Array<MediaWrapper>): Array<MediaWrapper> {
        return newTracks.filter { newItem ->
            currentTracks.all { currentItem ->
                !currentItem.equals(newItem)
            }
        }.toTypedArray()
    }

    companion object : DependencyProvider<Any>() {

        const val TAG = "VLC/SavePlaylistDialog"

        const val KEY_NEW_TRACKS = "PLAYLIST_NEW_TRACKS"
        const val KEY_FOLDER = "PLAYLIST_FROM_FOLDER"
        const val KEY_SUB_FOLDERS = "PLAYLIST_FOLDER_ADD_SUBFOLDERS"

        const val SELECTED_PLAYLIST = "SELECTED_PLAYLIST"
    }
}

fun Medialibrary.getPlaylistByName(name: String) = getPlaylists(Playlist.Type.All, false).firstOrNull { it.title == name }
