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

package org.videolan.vlc.gui.dialogs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.View;

import org.videolan.libvlc.Dialog;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.VlcProgressDialogBinding;

public class VlcProgressDialog extends VlcDialog<Dialog.ProgressDialog, VlcProgressDialogBinding> {

    public static final String ACTION_PROGRESS = "vlc_progress";
    LocalBroadcastManager mLocalBroadcastManager;

    @Override
    int getLayout() {
        return R.layout.vlc_progress_dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(getContext());
        mLocalBroadcastManager.registerReceiver(mProgressReceiver, new IntentFilter(ACTION_PROGRESS));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLocalBroadcastManager.unregisterReceiver(mProgressReceiver);
    }

    private final BroadcastReceiver mProgressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mBinding.progress.setProgress((int) (mVlcDialog.getPosition()*100));
            mBinding.cancel.setText(mVlcDialog.getCancelText());
            mBinding.cancel.setVisibility(
                    TextUtils.isEmpty(mVlcDialog.getCancelText()) ? View.GONE : View.VISIBLE);
        }
    };
}
