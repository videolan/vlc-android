// *************************************************************************
//  VlcDialog.java
// **************************************************************************
//  Copyright © 2016 VLC authors and VideoLAN
//  Author: Geoffrey Métais
//
//  This program is free software; you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation; either version 2 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program; if not, write to the Free Software
//  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
//
//  *************************************************************************

package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialog
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.DialogFragment
import org.videolan.libvlc.Dialog
import org.videolan.vlc.BR
import org.videolan.vlc.gui.DialogActivity

abstract class VlcDialog<T : Dialog, B : ViewDataBinding> : DialogFragment() {

    lateinit var vlcDialog: T
    protected lateinit var binding: B

    protected abstract val layout: Int

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, layout, container, false)
        binding.setVariable(BR.dialog, vlcDialog)
        binding.setVariable(BR.handler, this)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        retainInstance = true
        val dialog = AppCompatDialog(requireActivity(), theme)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        if (::vlcDialog.isInitialized) {
            vlcDialog.context = this
            dialog.setTitle(vlcDialog.title)
        }
        return dialog
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::vlcDialog.isInitialized) vlcDialog.dismiss()
        (activity as? DialogActivity)?.finish()
    }

    open fun onCancel(v: View) {
        dismiss()
    }

    override fun dismiss() {
        if (isResumed) super.dismiss()
    }
}
