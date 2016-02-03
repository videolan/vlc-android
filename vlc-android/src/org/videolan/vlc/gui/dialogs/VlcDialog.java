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

package org.videolan.vlc.gui.dialogs;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.libvlc.Dialog;
import org.videolan.vlc.BR;
import org.videolan.vlc.VLCApplication;

public abstract class VlcDialog<T extends Dialog, B extends android.databinding.ViewDataBinding> extends DialogFragment {

    T mVlcDialog;
    B mBinding;

    abstract int getLayout();

    public void init(String key) {
        mVlcDialog = (T) VLCApplication.getData(key);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding =  DataBindingUtil.inflate(inflater, getLayout(), container, false);
        mBinding.setVariable(BR.dialog, mVlcDialog);
        mBinding.setVariable(BR.handler, this);
        return mBinding.getRoot();
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(Bundle savedInstanceState) {
        mVlcDialog.setContext(this);
        AppCompatDialog dialog = new AppCompatDialog(getActivity(), getTheme());
        dialog.setTitle(mVlcDialog.getTitle());

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mVlcDialog.dismiss();
        getActivity().finish();
    }

    public void onCancel(View v) {
        dismiss();
    }
}
