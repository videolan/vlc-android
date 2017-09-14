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
import android.support.v4.content.LocalBroadcastManager;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.dialogs.VlcLoginDialog;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class NetworkBrowserFragment extends MediaSortedFragment {

    public static final String TAG = "VLC/NetworkBrowserFragment";
    boolean goBack = false;

    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(VLCApplication.getAppContext()).registerReceiver(mLocalReceiver, new IntentFilter(VlcLoginDialog.ACTION_DIALOG_CANCELED));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (goBack)
            getActivity().finish();
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(VLCApplication.getAppContext()).unregisterReceiver(mLocalReceiver);
    }

    protected void browseRoot() {
        runOnBrowserThread(new Runnable() {
            @Override
            public void run() {
                mMediaBrowser.discoverNetworkShares();
            }
        });
    }

    protected void addMedia(MediaWrapper mw){
        if (mUri == null)
            mw.setDescription(mw.getUri().getScheme());
        String letter = mw.getTitle().substring(0, 1).toUpperCase();
        if (mMediaItemMap.containsKey(letter)) {
            mMediaItemMap.get(letter).mediaList.add(mw);
        } else {
            ListItem item = new ListItem(letter, mw);
            mMediaItemMap.put(letter, item);
        }
    }

    private BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isResumed())
                goBack = true;
        }
    };
}
