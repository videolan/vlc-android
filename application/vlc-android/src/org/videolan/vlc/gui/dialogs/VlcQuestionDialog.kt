/*
 * ***************************************************************************
 * VlcQuestionDialog.java
 * ***************************************************************************
 * Copyright © 2016 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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

import android.view.View
import org.videolan.libvlc.Dialog
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VlcQuestionDialogBinding

class VlcQuestionDialog : VlcDialog<Dialog.QuestionDialog, VlcQuestionDialogBinding>() {

    override val layout: Int
        get() = R.layout.vlc_question_dialog

    fun onAction1(@Suppress("UNUSED_PARAMETER") v: View) {
        vlcDialog.postAction(1)
        dismiss()
    }

    fun onAction2(@Suppress("UNUSED_PARAMETER") v: View) {
        vlcDialog.postAction(2)
        dismiss()
    }
}
