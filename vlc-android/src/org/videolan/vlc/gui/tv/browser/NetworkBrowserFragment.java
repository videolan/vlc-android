/*
 * *************************************************************************
 *  NetworkBrowseFragment.java
 * **************************************************************************
 *  Copyright © 2015-2017 VLC authors and VideoLAN
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
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.ExternalMonitor;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.dialogs.VlcLoginDialog;
import org.videolan.vlc.viewmodels.browser.NetworkModel;

import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class NetworkBrowserFragment extends MediaSortedFragment<NetworkModel> {

    public static final String TAG = "VLC/NetworkBrowserFragment";
    boolean goBack = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = ViewModelProviders.of(this, new NetworkModel.Factory(requireContext(), mUri.toString(), mShowHiddenFiles)).get(NetworkModel.class);
        viewModel.getCategories().observe(this, new Observer<Map<String, List<MediaLibraryItem>>>() {
            @Override
            public void onChanged(@Nullable Map<String, List<MediaLibraryItem>> stringListMap) {
                if (stringListMap != null) update(stringListMap);
            }
        });
        ExternalMonitor.INSTANCE.getConnected().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean connected) {
                refresh(connected);
            }
        });
    }

    public void refresh(boolean connected) {
        if (connected) refresh();
        //TODO Disconnected view
    }

    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(VLCApplication.getAppContext()).registerReceiver(mLocalReceiver, new IntentFilter(VlcLoginDialog.ACTION_DIALOG_CANCELED));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (goBack) requireActivity().finish();
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(VLCApplication.getAppContext()).unregisterReceiver(mLocalReceiver);
    }

    private BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isResumed()) goBack = true;
        }
    };
}
