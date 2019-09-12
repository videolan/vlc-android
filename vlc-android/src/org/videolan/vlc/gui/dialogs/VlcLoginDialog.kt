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
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.videolan.libvlc.Dialog
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VlcLoginDialogBinding
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.LOGIN_STORE
import org.videolan.vlc.util.Settings

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

    fun onLogin(v: View) {
        vlcDialog.postLogin(binding.login.text.toString().trim(),
                binding.password.text.toString().trim(), binding.store.isChecked)
        settings.edit().putBoolean(LOGIN_STORE, binding.store.isChecked).apply()
        dismiss()
    }

    fun store() = settings.getBoolean(LOGIN_STORE, true)

    override fun onFocusChange(v: View, hasFocus: Boolean) {
        if (hasFocus) UiTools.setKeyboardVisibility(v, v is EditText)
    }

    // Cancel from LibVLC
    override fun onCancel(dialog: DialogInterface) {
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ACTION_DIALOG_CANCELED))
        super.onCancel(dialog)
    }

    // Cancel from UI
    override fun onCancel(v: View) {
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ACTION_DIALOG_CANCELED))
        super.onCancel(v)
    }

    companion object {
        const val ACTION_DIALOG_CANCELED = "action_dialog_canceled"
    }
}
