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
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import org.videolan.vlc.R


class ConfirmAudioPlayQueueDialog : VLCBottomSheetDialogFragment() {

    private lateinit var listener: () -> Unit
    private lateinit var title: TextView
    private lateinit var acceptButton: Button
    private lateinit var cancelButton: Button


    fun setListener(listener: () -> Unit) {
        this.listener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_confirm_audio_playqueue, container)
        title = view.findViewById(R.id.title)
        acceptButton = view.findViewById(R.id.accept_button)
        cancelButton = view.findViewById(R.id.cancel_button)
        acceptButton.setOnClickListener {
            listener.invoke()
            dismiss()
        }
        cancelButton.setOnClickListener { dismiss() }
        return view
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun initialFocusedView(): View = title

    override fun needToManageOrientation(): Boolean {
        return true
    }
}