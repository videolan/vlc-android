/*
 * *************************************************************************
 *  FileBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v7.internal.widget.AdapterViewCompat;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.View;

import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Util;

import java.io.File;

public class FileBrowserFragment extends BaseBrowserFragment {

    public FileBrowserFragment() {
        super();
        ROOT = Environment.getExternalStorageDirectory().getPath();
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mRoot = mMrl == null;
    }

    @Override
    protected Fragment createFragment() {
        return new FileBrowserFragment();
    }

    @Override
    protected String getCategoryTitle() {
        return getString(R.string.directories);
    }

    @Override
    protected void browseRoot() {
        String storages[] = AndroidDevices.getMediaDirectories();
        MediaWrapper mw;
        for (String storage : storages) {
            mw = new MediaWrapper(storage);
            mw.setTitle(AndroidDevices.getStorageTitle(storage));
            mw.setType(MediaWrapper.TYPE_DIR);
            mAdapter.addItem(mw, false, false);
        }
        mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
        if (mReadyToDisplay) {
            updateEmptyView();
            mAdapter.notifyDataSetChanged();
            parseSubDirectories();
        }
    }

    public void onStart(){
        super.onStart();

        //Handle network connection state
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        getActivity().registerReceiver(storageReceiver, filter);
        if (mReadyToDisplay)
            update();
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unregisterReceiver(storageReceiver);
    }

    private final BroadcastReceiver storageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_MOUNTED) ||
                    action.equalsIgnoreCase(Intent.ACTION_MEDIA_UNMOUNTED) ||
                    action.equalsIgnoreCase(Intent.ACTION_MEDIA_REMOVED) ||
                    action.equalsIgnoreCase(Intent.ACTION_MEDIA_EJECT)) {
                if (mReadyToDisplay)
                    update();
            }
        }
    };
}
