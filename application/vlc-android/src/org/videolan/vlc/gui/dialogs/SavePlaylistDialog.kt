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
import org.videolan.tools.AppScope
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.DependencyProvider
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogPlaylistBinding
import org.videolan.vlc.gui.SimpleAdapter
import org.videolan.vlc.providers.FileBrowserProvider
import org.videolan.vlc.viewmodels.browser.TYPE_FILE
import org.videolan.vlc.viewmodels.browser.getBrowserModel
import java.util.*

class SavePlaylistDialog : VLCBottomSheetDialogFragment(), View.OnClickListener,
        TextView.OnEditorActionListener, SimpleAdapter.ClickHandler {
    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = false

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
    private lateinit var newTrack: Array<MediaWrapper>
    private lateinit var medialibrary: Medialibrary

    private val coroutineContextProvider: CoroutineContextProvider

    override fun initialFocusedView(): View = binding.list

    init {
        SavePlaylistDialog.registerCreator { CoroutineContextProvider() }
        coroutineContextProvider = SavePlaylistDialog.get(0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        medialibrary = Medialibrary.getInstance()
        adapter = SimpleAdapter(this)
        newTrack = try {
            @Suppress("UNCHECKED_CAST")
            val tracks = arguments!!.getParcelableArray(KEY_NEW_TRACKS) as Array<MediaWrapper>
            filesText = resources.getQuantityString(R.plurals.media_quantity, tracks.size, tracks.size)
            tracks
        } catch (e: Exception) {
            try {
                arguments!!.getString(KEY_FOLDER)?.let { folder ->

                    isLoading = true
                    val viewModel = getBrowserModel(category = TYPE_FILE, url = folder, showHiddenFiles = false)
                    if (arguments!!.getBoolean(KEY_SUB_FOLDERS, false)) lifecycleScope.launchWhenStarted {
                        withContext(Dispatchers.IO) {
                            newTrack = (viewModel.provider as FileBrowserProvider).browseByUrl(folder).toTypedArray()
                            isLoading = false
                            filesText = resources.getQuantityString(R.plurals.media_quantity, newTrack.size, newTrack.size)
                        }
                    } else {
                        viewModel.dataset.observe(this, { mediaLibraryItems ->
                            newTrack = mediaLibraryItems.asSequence().map { it as MediaWrapper }.filter { it.type != MediaWrapper.TYPE_DIR }.toList().toTypedArray()
                            isLoading = false
                            filesText = resources.getQuantityString(R.plurals.media_quantity, newTrack.size, newTrack.size)
                        })
                    }
                }
                emptyArray()
            } catch (e: Exception) {
                emptyArray()
            }
        }
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

        binding.dialogPlaylistName.editText!!.setOnEditorActionListener(this)
        binding.list.layoutManager = LinearLayoutManager(view.context)
        binding.list.adapter = adapter
        adapter.submitList(listOf<MediaLibraryItem>(*medialibrary.playlists.apply { forEach { it.description = resources.getQuantityString(R.plurals.media_quantity, it.tracksCount, it.tracksCount) } }))
        if (!Tools.isArrayEmpty(newTrack)) binding.dialogPlaylistSave.setText(R.string.save)
        updateEmptyView()
    }

    private fun updateEmptyView() {
        binding.empty.visibility = if (adapter.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onClick(v: View) {
        addToNewPlaylist()
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_SEND) addToNewPlaylist()
        return false
    }

    private fun addToNewPlaylist() {
        val name = binding.dialogPlaylistName.editText?.text?.toString()?.trim { it <= ' ' }
                ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { medialibrary.getPlaylistByName(name) }?.let {
                binding.dialogPlaylistName.error = getString(R.string.playlist_existing, it.title)
                return@launch
            }
            dismiss()
            savePlaylist(medialibrary.createPlaylist(name) ?: return@launch)
        }
    }

    private fun savePlaylist(playlist: Playlist) {
        AppScope.launch(coroutineContextProvider.IO) {
            if (newTrack.isEmpty()) return@launch
            val ids = LinkedList<Long>()
            for (mw in newTrack) {
                val id = mw.id
                if (id == 0L) {
                    var media = medialibrary.getMedia(mw.uri)
                    if (media != null)
                        ids.add(media.id)
                    else {
                        media = medialibrary.addMedia(mw.location, -1L)
                        if (media != null) ids.add(media.id)
                    }
                } else
                    ids.add(id)
            }
            playlist.append(ids)
        }
        dismiss()
    }

    override fun onClick(item: MediaLibraryItem) {
        savePlaylist(item as Playlist)
    }

    companion object : DependencyProvider<Any>() {

        val TAG = "VLC/SavePlaylistDialog"

        const val KEY_NEW_TRACKS = "PLAYLIST_NEW_TRACKS"
        const val KEY_FOLDER = "PLAYLIST_FROM_FOLDER"
        const val KEY_SUB_FOLDERS = "PLAYLIST_FOLDER_ADD_SUBFOLDERS"
    }
}

fun Medialibrary.getPlaylistByName(name: String) = playlists.firstOrNull { it.title == name }
