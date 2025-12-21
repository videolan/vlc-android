/**
 * **************************************************************************
 * ResetSleepTimerDialog.kt
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
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import org.videolan.vlc.databinding.DialogResetSleepTimerBinding
import org.videolan.vlc.viewmodels.PlaylistModel
import java.util.Calendar


class ResetSleepTimerDialog : VLCBottomSheetDialogFragment() {

    lateinit var binding: DialogResetSleepTimerBinding
    private val playlistModel by lazy { PlaylistModel.get(this) }
    companion object {
        fun newInstance(): ResetSleepTimerDialog {
            return ResetSleepTimerDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun getDefaultState(): Int {
       return STATE_EXPANDED
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogResetSleepTimerBinding.inflate(inflater,container,false)
        binding.resetButton.setOnClickListener {
            val sleepTime = Calendar.getInstance()
            sleepTime.timeInMillis += playlistModel.service?.sleepTimerInterval?:0L
            sleepTime.set(Calendar.SECOND, 0)
            playlistModel.service?.setSleepTimer(sleepTime)
            playlistModel.service?.play()
            dismiss()
            activity?.finish()
        }
        binding.closeButton.setOnClickListener {
            playlistModel.service?.stop()
            dismiss()
            activity?.finish()
        }
        return binding.root
    }

    override fun needToManageOrientation(): Boolean {
        return true
    }

    override fun initialFocusedView(): View {
        return binding.message
    }
}