/*
 * ************************************************************************
 *  RenameDialog.kt
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

package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.Bookmark
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.util.parcelable
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.showPinIfNeeded

const val RENAME_DIALOG_MEDIA = "RENAME_DIALOG_MEDIA"
const val RENAME_DIALOG_FILE = "RENAME_DIALOG_FILE"
const val RENAME_DIALOG_NEW_NAME = "RENAME_DIALOG_NEW_NAME"
const val CONFIRM_RENAME_DIALOG_RESULT = "CONFIRM_RENAME_DIALOG_RESULT"
const val CONFIRM_BOOKMARK_RENAME_DIALOG_RESULT = "CONFIRM_BOOKMARK_RENAME_DIALOG_RESULT"
const val CONFIRM_PLAYLIST_RENAME_DIALOG_RESULT = "CONFIRM_PLAYLIST_RENAME_DIALOG_RESULT"

class RenameDialog : VLCBottomSheetDialogFragment() {

    private lateinit var renameButton: Button
    private lateinit var newNameInputtext: TextInputEditText
    private lateinit var media: MediaLibraryItem
    private var renameFile: Boolean = false

    companion object {

        fun newInstance(media: MediaLibraryItem, isFile:Boolean = false): RenameDialog {

            return RenameDialog().apply {
                arguments = bundleOf(RENAME_DIALOG_MEDIA to media, RENAME_DIALOG_FILE to isFile)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        lifecycleScope.launch { if (requireActivity().showPinIfNeeded()) dismiss() }
        media = arguments?.parcelable(RENAME_DIALOG_MEDIA) ?: return
        renameFile = arguments?.getBoolean(RENAME_DIALOG_FILE) == true
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_rename, container)
        val name = if (renameFile && media is MediaWrapper) (media as MediaWrapper).fileName else media.title
        newNameInputtext = view.findViewById(R.id.new_name)
        renameButton = view.findViewById(R.id.rename_button)
        if (media.title.isNotEmpty()) {
            newNameInputtext.setText(name)
        }
        val extIndex = name.indexOfLast { it == '.' }
        if (extIndex != -1 && renameFile) newNameInputtext.setSelection(0, extIndex) else newNameInputtext.setSelection(0, name.length)
        renameButton.setOnClickListener {
            performRename()
        }
        newNameInputtext.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                performRename()
                true
            } else false
        }
        newNameInputtext.setOnKeyListener { _: View, keyCode: Int, keyEvent: KeyEvent ->
            if (keyCode == EditorInfo.IME_ACTION_DONE ||
                    keyCode == EditorInfo.IME_ACTION_GO ||
                    keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                performRename()
                true
            } else false
        }
        view.findViewById<TextView>(R.id.media_title).text = name
        lifecycleScope.launch(Dispatchers.IO) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(100)
                withContext(Dispatchers.Main) {
                    newNameInputtext.requestFocus()

                    UiTools.setKeyboardVisibility(newNameInputtext, true)
                }
            }
        }
        return view
    }

    private fun performRename() {
        if (newNameInputtext.text.toString().isNotEmpty()) {
            val key = when(media) {
                is Bookmark -> CONFIRM_BOOKMARK_RENAME_DIALOG_RESULT
                is Playlist -> CONFIRM_PLAYLIST_RENAME_DIALOG_RESULT
                else -> CONFIRM_RENAME_DIALOG_RESULT
            }
            setFragmentResult(key, bundleOf(RENAME_DIALOG_MEDIA to media, RENAME_DIALOG_NEW_NAME to newNameInputtext.text.toString()))
            dismiss()
        }
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun initialFocusedView(): View = newNameInputtext

    override fun needToManageOrientation(): Boolean {
        return true
    }
}