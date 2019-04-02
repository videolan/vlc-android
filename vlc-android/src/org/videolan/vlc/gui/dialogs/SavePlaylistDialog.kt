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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.textfield.TextInputLayout
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.medialibrary.media.Playlist
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.SimpleAdapter
import org.videolan.vlc.util.runIO
import java.util.*

class SavePlaylistDialog : VLCBottomSheetDialogFragment(), View.OnClickListener, TextView.OnEditorActionListener, SimpleAdapter.ClickHandler {

    private var mEditText: EditText? = null
    private lateinit var mListView: RecyclerView
    private lateinit var mEmptyView: TextView
    private lateinit var mSaveButton: Button
    private lateinit var mAdapter: SimpleAdapter
    private lateinit var mTracks: Array<MediaWrapper>
    private lateinit var mNewTrack: Array<MediaWrapper>
    private lateinit var mMedialibrary: Medialibrary
    private var mPlaylistId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        defaultState = BottomSheetBehavior.STATE_EXPANDED
        mMedialibrary = VLCApplication.getMLInstance()
        mAdapter = SimpleAdapter(this)
        mTracks = try {
            @Suppress("UNCHECKED_CAST")
            arguments!!.getParcelableArray(KEY_TRACKS) as Array<MediaWrapper>
        } catch (e: Exception) {
            emptyArray()
        }
        mNewTrack = try {
            @Suppress("UNCHECKED_CAST")
            arguments!!.getParcelableArray(KEY_NEW_TRACKS) as Array<MediaWrapper>
        } catch (e: Exception) {
            emptyArray()
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_playlist, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mListView = view.findViewById(android.R.id.list)
        mSaveButton = view.findViewById(R.id.dialog_playlist_save)
        mEmptyView = view.findViewById(android.R.id.empty)
        val mLayout = view.findViewById<TextInputLayout>(R.id.dialog_playlist_name)
        mEditText = mLayout.editText
        mSaveButton.setOnClickListener(this)

        mEditText!!.setOnEditorActionListener(this)
        mListView.layoutManager = LinearLayoutManager(view.context)
        mListView.adapter = mAdapter
        mAdapter.submitList(Arrays.asList<MediaLibraryItem>(*mMedialibrary.playlists))
        updateEmptyView()

    }

    private fun updateEmptyView() {
        mEmptyView.visibility = if (mAdapter.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onClick(v: View) {
        savePlaylist()
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean {
        if (actionId == EditorInfo.IME_ACTION_SEND)
            savePlaylist()
        return false
    }

    private fun savePlaylist() {
        runIO(Runnable {
            val name = mEditText!!.text.toString().trim { it <= ' ' }
            val addTracks = !Tools.isArrayEmpty(mNewTrack)
            var playlist: Playlist? = mMedialibrary.getPlaylist(mPlaylistId)
            val exists = playlist != null
            val tracks: Array<MediaWrapper>?
            if (!exists) playlist = mMedialibrary.createPlaylist(name)
            if (playlist == null) return@Runnable
            tracks = if (addTracks) {
                mNewTrack
            } else {//Save a playlist
                for (index in 0 until playlist.tracks.size) {
                    playlist.remove(index)
                }
                mTracks
            }
            if (tracks.isEmpty()) return@Runnable
            val ids = LinkedList<Long>()
            for (mw in tracks) {
                val id = mw.id
                if (id == 0L) {
                    var media = mMedialibrary.getMedia(mw.uri)
                    if (media != null)
                        ids.add(media.id)
                    else {
                        media = mMedialibrary.addMedia(mw.location)
                        if (media != null) ids.add(media.id)
                    }
                } else
                    ids.add(id)
            }
            playlist.append(ids)
        })
        dismiss()
    }


    override fun onClick(item: MediaLibraryItem) {
        mPlaylistId = item.id
        mEditText!!.setText(item.title)
    }

    companion object {

        val TAG = "VLC/SavePlaylistDialog"

        const val KEY_TRACKS = "PLAYLIST_TRACKS"
        const val KEY_NEW_TRACKS = "PLAYLIST_NEW_TRACKS"
    }
}
