/*
 * ************************************************************************
 *  NetworkLoginDialog.java
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

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EditText
import org.videolan.libvlc.Dialog
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VlcLoginDialogBinding
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.resources.AndroidDevices
import org.videolan.tools.LOGIN_STORE
import org.videolan.tools.Settings
import org.videolan.tools.putSingle

class VlcLoginDialog : VlcDialog<Dialog.LoginDialog, VlcLoginDialogBinding>(), View.OnFocusChangeListener {

    private lateinit var settings: SharedPreferences

    override val layout= R.layout.vlc_login_dialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Settings.showTvUi && !AndroidDevices.hasPlayServices) {
            binding.login.onFocusChangeListener = this
            binding.password.onFocusChangeListener = this
        }
        binding.store.onFocusChangeListener = this
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        settings = Settings.getInstance(requireActivity())
    }

    fun onLogin(@Suppress("UNUSED_PARAMETER") v: View) {
        vlcDialog.postLogin(binding.login.text.toString().trim(),
                binding.password.text.toString().trim(), binding.store.isChecked)
        settings.putSingle(LOGIN_STORE, binding.store.isChecked)
        dismiss()
    }

    fun store() = settings.getBoolean(LOGIN_STORE, true)

    override fun onFocusChange(v: View, hasFocus: Boolean) {
        if (hasFocus) UiTools.setKeyboardVisibility(v, v is EditText)
    }
}
