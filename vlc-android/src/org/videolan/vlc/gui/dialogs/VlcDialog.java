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

import androidx.databinding.DataBindingUtil;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AppCompatDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.libvlc.Dialog;
import org.videolan.vlc.BR;
import org.videolan.vlc.VLCApplication;

public abstract class VlcDialog<T extends Dialog, B extends androidx.databinding.ViewDataBinding> extends DialogFragment {

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
        AppCompatDialog dialog = new AppCompatDialog(getActivity(), getTheme());
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        if (mVlcDialog != null) {
            mVlcDialog.setContext(this);
            dialog.setTitle(mVlcDialog.getTitle());
        }
        return dialog;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mVlcDialog != null)
            mVlcDialog.dismiss();
        getActivity().finish();
    }

    public void onCancel(View v) {
        dismiss();
    }

    @Override
    public void dismiss() {
        if (isResumed())
            super.dismiss();
    }
}
