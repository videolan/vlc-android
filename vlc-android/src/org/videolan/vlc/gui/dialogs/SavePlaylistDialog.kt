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
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.textfield.TextInputLayout
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.interfaces.media.AbstractPlaylist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.SimpleAdapter
import org.videolan.vlc.util.runIO
import java.util.*

class SavePlaylistDialog : VLCBottomSheetDialogFragment(), View.OnClickListener, TextView.OnEditorActionListener, SimpleAdapter.ClickHandler {
    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = false

    private var editText: EditText? = null
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var saveButton: Button
    private lateinit var adapter: SimpleAdapter
    private lateinit var tracks: Array<AbstractMediaWrapper>
    private lateinit var newTrack: Array<AbstractMediaWrapper>
    private lateinit var medialibrary: AbstractMedialibrary
    private var playlistId: Long = 0

    override fun initialFocusedView(): View = listView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        medialibrary = AbstractMedialibrary.getInstance()
        adapter = SimpleAdapter(this)
        tracks = try {
            @Suppress("UNCHECKED_CAST")
            arguments!!.getParcelableArray(KEY_TRACKS) as Array<AbstractMediaWrapper>
        } catch (e: Exception) {
            emptyArray()
        }
        newTrack = try {
            @Suppress("UNCHECKED_CAST")
            arguments!!.getParcelableArray(KEY_NEW_TRACKS) as Array<AbstractMediaWrapper>
        } catch (e: Exception) {
            emptyArray()
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflate(inflater, container, R.layout.dialog_playlist)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView = view.findViewById(android.R.id.list)
        saveButton = view.findViewById(R.id.dialog_playlist_save)
        emptyView = view.findViewById(android.R.id.empty)
        val mLayout = view.findViewById<TextInputLayout>(R.id.dialog_playlist_name)
        editText = mLayout.editText
        saveButton.setOnClickListener(this)

        editText!!.setOnEditorActionListener(this)
        listView.layoutManager = LinearLayoutManager(view.context)
        listView.adapter = adapter
        adapter.submitList(listOf<MediaLibraryItem>(*medialibrary.playlists))
        if (!Tools.isArrayEmpty(newTrack)) saveButton.setText(R.string.save)
        updateEmptyView()

    }

    private fun updateEmptyView() {
        emptyView.visibility = if (adapter.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onClick(v: View) {
        savePlaylist()
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_SEND)
            savePlaylist()
        return false
    }

    private fun savePlaylist() {
        runIO(Runnable {
            val name = editText!!.text.toString().trim { it <= ' ' }
            val addTracks = !Tools.isArrayEmpty(newTrack)
            var playlist: AbstractPlaylist? = medialibrary.getPlaylist(playlistId)
            val exists = playlist != null
            val playlistTracks: Array<AbstractMediaWrapper>?
            if (!exists) playlist = medialibrary.createPlaylist(name)
            if (playlist == null) return@Runnable
            playlistTracks = if (addTracks) {
                newTrack
            } else {//Save a playlist
                for (index in 0 until playlist.tracks.size) {
                    playlist.remove(index)
                }
                tracks
            }
            if (playlistTracks.isEmpty()) return@Runnable
            val ids = LinkedList<Long>()
            for (mw in playlistTracks) {
                val id = mw.id
                if (id == 0L) {
                    var media = medialibrary.getMedia(mw.uri)
                    if (media != null)
                        ids.add(media.id)
                    else {
                        media = medialibrary.addMedia(mw.location)
                        if (media != null) ids.add(media.id)
                    }
                } else
                    ids.add(id)
            }

            if (!addTracks) {
                for (i in 0 until playlist.tracks.size) {
                    playlist.remove(0)
                }
            }

            playlist.append(ids)
        })
        dismiss()
    }


    override fun onClick(item: MediaLibraryItem) {
        playlistId = item.id
        editText!!.setText(item.title)
    }

    companion object {

        val TAG = "VLC/SavePlaylistDialog"

        const val KEY_TRACKS = "PLAYLIST_TRACKS"
        const val KEY_NEW_TRACKS = "PLAYLIST_NEW_TRACKS"
    }
}
