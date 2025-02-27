/**
 * **************************************************************************
 * ConfirmAudioPlayQueueDialog.kt
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import org.videolan.vlc.R

const val CONFIRM_PREFERENCE_CHANGE_DIALOG_RESULT = "confirm_preference_change_dialog_result"
const val PREFERENCE_KEY = "preference_key"
const val WARNING_TITLE = "warning_title"
const val WARNING_TEXT = "warning_text"

class ConfirmPreferenceChangeDialog : VLCBottomSheetDialogFragment() {

    private lateinit var listener: () -> Unit
    private lateinit var title: TextView
    private lateinit var warning: TextView
    private lateinit var acceptButton: Button
    private lateinit var cancelButton: Button

    private lateinit var preferenceKey: String
    private lateinit var titleText: String
    private lateinit var warningText: String


    fun setListener(listener: () -> Unit) {
        this.listener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceKey = arguments?.getString(PREFERENCE_KEY)!!
        titleText = arguments?.getString(WARNING_TITLE)!!
        warningText = arguments?.getString(WARNING_TEXT)!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_confirm_audio_playqueue, container)
        title = view.findViewById(R.id.title)
        warning = view.findViewById(R.id.message)
        acceptButton = view.findViewById(R.id.accept_button)
        cancelButton = view.findViewById(R.id.cancel_button)
        acceptButton.setOnClickListener {
            if (::listener.isInitialized) listener.invoke()
            setFragmentResult(CONFIRM_PREFERENCE_CHANGE_DIALOG_RESULT, bundleOf(PREFERENCE_KEY to preferenceKey))
            dismiss()
        }
        cancelButton.setOnClickListener { dismiss() }
        title.text = titleText
        warning.text = warningText
        return view
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun initialFocusedView(): View = title

    override fun needToManageOrientation(): Boolean {
        return true
    }
    companion object {
        fun newInstance(preferenceKey:String, title: String, warning: String) : ConfirmPreferenceChangeDialog {
            return ConfirmPreferenceChangeDialog().apply {
                val args = Bundle()
                args.putString(PREFERENCE_KEY, preferenceKey)
                args.putString(WARNING_TITLE, title)
                args.putString(WARNING_TEXT, warning)
                arguments = args
            }
        }
    }
}