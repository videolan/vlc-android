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

package org.videolan.vlc.gui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;
import android.view.View;

import org.videolan.libvlc.Dialog;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.VlcLoginDialogBinding;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.util.Util;

public class VlcLoginDialog extends VlcDialog<Dialog.LoginDialog, VlcLoginDialogBinding> {

    SharedPreferences mSettings;

    @Override
    int getLayout() {
        return R.layout.vlc_login_dialog;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mSettings = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void onLogin(View v) {
        mVlcDialog.postLogin(mBinding.login.getText().toString().trim(),
                mBinding.password.getText().toString().trim(), mBinding.store.isChecked());
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(PreferencesActivity.LOGIN_STORE, mBinding.store.isChecked());
        Util.commitPreferences(editor);
        dismiss();
    }

    public boolean store() {
        return mSettings.getBoolean(PreferencesActivity.LOGIN_STORE, true);
    }
}
