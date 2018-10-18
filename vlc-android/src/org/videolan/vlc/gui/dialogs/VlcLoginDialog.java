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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.View;
import android.widget.EditText;

import org.videolan.libvlc.Dialog;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.VlcLoginDialogBinding;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Settings;

public class VlcLoginDialog extends VlcDialog<Dialog.LoginDialog, VlcLoginDialogBinding> implements View.OnFocusChangeListener {

    SharedPreferences mSettings;
    public final static String ACTION_DIALOG_CANCELED = "action_dialog_canceled";

    @Override
    int getLayout() {
        return R.layout.vlc_login_dialog;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (AndroidDevices.showTvUi(view.getContext()) && !AndroidDevices.hasPlayServices) {
            mBinding.login.setOnFocusChangeListener(this);
            mBinding.password.setOnFocusChangeListener(this);
        }
        mBinding.store.setOnFocusChangeListener(this);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mSettings = Settings.INSTANCE.getInstance(context);
    }

    public void onLogin(View v) {
        mVlcDialog.postLogin(mBinding.login.getText().toString().trim(),
                mBinding.password.getText().toString().trim(), mBinding.store.isChecked());
        mSettings.edit().putBoolean(PreferencesActivity.LOGIN_STORE, mBinding.store.isChecked()).apply();
        dismiss();
    }

    public boolean store() {
        return mSettings.getBoolean(PreferencesActivity.LOGIN_STORE, true);
    }

    @Override
    public void onFocusChange(final View v, boolean hasFocus) {
        if (hasFocus) UiTools.setKeyboardVisibility(v, v instanceof EditText);
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(new Intent(ACTION_DIALOG_CANCELED));
        super.onDestroy();
    }
}
