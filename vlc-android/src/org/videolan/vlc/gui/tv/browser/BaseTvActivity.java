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
import android.text.TextUtils;
import android.view.KeyEvent;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.NetworkMonitor;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.PlaybackServiceActivity;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.tv.SearchActivity;
import org.videolan.vlc.util.Permissions;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public abstract class BaseTvActivity extends PlaybackServiceActivity implements NetworkMonitor.NetworkObserver {

    private static final String TAG = "VLC/BaseTvActivity";

    protected Medialibrary mMediaLibrary;
    protected SharedPreferences mSettings;
    boolean mRegistering = false;
    private volatile boolean mIsVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Init Medialibrary if KO
        if (savedInstanceState != null && !VLCApplication.getMLInstance().isInitiated() && Permissions.canReadStorage())
            startService(new Intent(MediaParsingService.ACTION_INIT, null, this, MediaParsingService.class));
        super.onCreate(savedInstanceState);
        mMediaLibrary = VLCApplication.getMLInstance();
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mIsVisible = true;
        //Handle network connection state

        final IntentFilter storageFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        storageFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        storageFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        storageFilter.addDataScheme("file");
        final IntentFilter parsingServiceFilter = new IntentFilter(MediaParsingService.ACTION_SERVICE_ENDED);
        parsingServiceFilter.addAction(MediaParsingService.ACTION_SERVICE_STARTED);
        parsingServiceFilter.addAction(MediaParsingService.ACTION_PROGRESS);
        parsingServiceFilter.addAction(MediaParsingService.ACTION_NEW_STORAGE);

        mRegistering = true;
        LocalBroadcastManager.getInstance(this).registerReceiver(mParsingServiceReceiver, parsingServiceFilter);
        registerReceiver(mExternalDevicesReceiver, storageFilter);
        NetworkMonitor.subscribe(this);
    }

    @Override
    protected void onStop() {
        mIsVisible = false;
        super.onStop();
        unregisterReceiver(mExternalDevicesReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mParsingServiceReceiver);
        NetworkMonitor.unsubscribe(this);
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
                case MediaParsingService.ACTION_SERVICE_ENDED:
                    onParsingServiceFinished();
                    break;
                case MediaParsingService.ACTION_SERVICE_STARTED:
                    onParsingServiceStarted();
                    break;
                case MediaParsingService.ACTION_PROGRESS:
                    onParsingServiceProgress();
                    break;
                case MediaParsingService.ACTION_NEW_STORAGE:
                    UiTools.newStorageDetected(BaseTvActivity.this, intent.getStringExtra(MediaParsingService.EXTRA_PATH));
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

    protected final BroadcastReceiver mExternalDevicesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mRegistering) {
                mRegistering = false;
                return;
            }
            final String action = intent.getAction();
            if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_MOUNTED)) {
                String path = intent.getData().getPath();
                String uuid = intent.getData().getLastPathSegment();
                if (TextUtils.isEmpty(uuid))
                    return;
                final boolean isIgnored = mSettings.getBoolean("ignore_"+ uuid, false);
                if (!isIgnored && mMediaLibrary.addDevice(uuid, path, true, true)) {
                    UiTools.newStorageDetected(BaseTvActivity.this, path);
                } else
                    startService(new Intent(MediaParsingService.ACTION_RELOAD, null, BaseTvActivity.this, MediaParsingService.class)
                            .putExtra(MediaParsingService.EXTRA_PATH, path));
            } else if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_EJECT) || action.equalsIgnoreCase(Intent.ACTION_MEDIA_REMOVED)) {
                mMediaLibrary.removeDevice(intent.getData().getLastPathSegment());
                onParsingServiceFinished();
            }
        }
    };
}
