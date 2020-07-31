/*
 * ************************************************************************
 *  VlcProgressDialog.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
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
 *
 *  *************************************************************************
 */

package org.videolan.vlc.gui.dialogs

import android.view.View
import org.videolan.libvlc.Dialog
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VlcProgressDialogBinding

class VlcProgressDialog : VlcDialog<Dialog.ProgressDialog, VlcProgressDialogBinding>() {

    override val layout: Int
        get() = R.layout.vlc_progress_dialog

    fun updateProgress() {
        binding.progress.progress = (vlcDialog.position * 100f).toInt()
        binding.cancel.text = vlcDialog.cancelText
        binding.cancel.visibility = if (vlcDialog.cancelText.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}
