/*
 * *************************************************************************
 *  BaseTvActivity.java
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

package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.KeyEvent;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.ExternalMonitor;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.PlaybackServiceActivity;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.tv.SearchActivity;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Permissions;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public abstract class BaseTvActivity extends PlaybackServiceActivity implements ExternalMonitor.NetworkObserver {

    private static final String TAG = "VLC/BaseTvActivity";

    protected Medialibrary mMediaLibrary;
    protected SharedPreferences mSettings;
    boolean mRegistering = false;
    private volatile boolean mIsVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Init Medialibrary if KO
        if (savedInstanceState != null && !VLCApplication.getMLInstance().isInitiated() && Permissions.canReadStorage(this))
            startService(new Intent(Constants.ACTION_INIT, null, this, MediaParsingService.class));
        super.onCreate(savedInstanceState);
        mMediaLibrary = VLCApplication.getMLInstance();
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    protected void onStart() {
        ExternalMonitor.subscribeStorageCb(this);

        final IntentFilter parsingServiceFilter = new IntentFilter(Constants.ACTION_SERVICE_ENDED);
        parsingServiceFilter.addAction(Constants.ACTION_SERVICE_STARTED);
        parsingServiceFilter.addAction(Constants.ACTION_PROGRESS);
        parsingServiceFilter.addAction(Constants.ACTION_NEW_STORAGE);

        mRegistering = true;
        LocalBroadcastManager.getInstance(this).registerReceiver(mParsingServiceReceiver, parsingServiceFilter);
        ExternalMonitor.subscribeNetworkCb(this);
        // super.onStart must be called after receiver registration
        super.onStart();
        mIsVisible = true;
    }

    @Override
    protected void onStop() {
        mIsVisible = false;
        ExternalMonitor.unsubscribeStorageCb(this);
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mParsingServiceReceiver);
        ExternalMonitor.unsubscribeNetworkCb(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH){
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    protected abstract void refresh();
    public void onNetworkConnectionChanged(boolean connected) {}

    protected final BroadcastReceiver mParsingServiceReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case Constants.ACTION_SERVICE_ENDED:
                    onParsingServiceFinished();
                    break;
                case Constants.ACTION_SERVICE_STARTED:
                    onParsingServiceStarted();
                    break;
                case Constants.ACTION_PROGRESS:
                    onParsingServiceProgress();
                    break;
                case Constants.ACTION_NEW_STORAGE:
                    UiTools.newStorageDetected(BaseTvActivity.this, intent.getStringExtra(Constants.EXTRA_PATH));
                    break;
            }
        }
    };

    protected boolean isVisible() {
        return mIsVisible;
    }

    protected void onParsingServiceStarted() {}
    protected void onParsingServiceProgress() {}
    protected void onParsingServiceFinished() {}
}
