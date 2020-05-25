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

/**
 * **************************************************************************
 * PickTimeFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.dialogs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R

const val RENAME_DIALOG_MEDIA = "RENAME_DIALOG_MEDIA"
const val RENAME_DIALOG_NEW_NAME = "RENAME_DIALOG_NEW_NAME"
const val RENAME_DIALOG_REQUEST_CODE = 1

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class RenameDialog : VLCBottomSheetDialogFragment() {

    private lateinit var renameButton: Button
    private lateinit var newNameInputtext: TextInputEditText
    private lateinit var media: MediaLibraryItem

    companion object {

        fun newInstance(media: MediaLibraryItem): RenameDialog {

            return RenameDialog().apply {
                val args = Bundle()
                args.putParcelable(RENAME_DIALOG_MEDIA, media)
                arguments = args
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        media = arguments?.getParcelable(RENAME_DIALOG_MEDIA) ?: return
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_rename, container)
        newNameInputtext = view.findViewById(R.id.new_name)
        renameButton = view.findViewById(R.id.rename_button)
        if (media.title.isNotEmpty()) newNameInputtext.setText(media.title)
        renameButton.setOnClickListener {
            performRename()
        }
        newNameInputtext.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                performRename()
                true
            }
            false
        }
        newNameInputtext.setOnKeyListener { view: View, keyCode: Int, keyEvent: KeyEvent ->
            if (keyCode == EditorInfo.IME_ACTION_DONE ||
                    keyCode == EditorInfo.IME_ACTION_GO ||
                    keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                performRename()
                true
            }
            false
        }
        view.findViewById<TextView>(R.id.media_title).text = media.title
        return view
    }

    private fun performRename() {
        if (newNameInputtext.text.toString().isNotEmpty()) {
            val intent = Intent()
            intent.putExtra(RENAME_DIALOG_MEDIA,media)
            intent.putExtra(RENAME_DIALOG_NEW_NAME,newNameInputtext.text.toString())
            targetFragment?.onActivityResult(RENAME_DIALOG_REQUEST_CODE, Activity.RESULT_OK, intent)
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