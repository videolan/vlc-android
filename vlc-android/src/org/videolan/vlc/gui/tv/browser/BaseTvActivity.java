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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.widget.TextView;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.ExternalMonitor;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.MediaParsingServiceKt;
import org.videolan.vlc.R;
import org.videolan.vlc.ScanProgress;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.tv.SearchActivity;
import org.videolan.vlc.gui.tv.TimeUpdaterKt;
import org.videolan.vlc.util.Settings;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public abstract class BaseTvActivity extends FragmentActivity {

    private static final String TAG = "VLC/BaseTvActivity";

    protected Medialibrary mMediaLibrary;
    protected SharedPreferences mSettings;
    private volatile boolean mIsVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Init Medialibrary if KO
        if (savedInstanceState != null) MediaParsingServiceKt.startMedialibrary(this, false, false, true);
        super.onCreate(savedInstanceState);
        mMediaLibrary = VLCApplication.getMLInstance();
        mSettings = Settings.INSTANCE.getInstance(this);
        registerLiveData();
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                TimeUpdaterKt.registerTimeView(BaseTvActivity.this, (TextView) findViewById(R.id.tv_time));
            }
        });
    }

    @Override
    protected void onStart() {
        ExternalMonitor.INSTANCE.subscribeStorageCb(this);

        // super.onStart must be called after receiver registration
        super.onStart();
        mIsVisible = true;
    }

    @Override
    protected void onStop() {
        mIsVisible = false;
        ExternalMonitor.INSTANCE.unsubscribeStorageCb(this);
        super.onStop();
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

    protected boolean isVisible() {
        return mIsVisible;
    }

    protected void onParsingServiceStarted() {}
    protected void onParsingServiceProgress() {}
    protected void onParsingServiceFinished() {}

    private void registerLiveData() {
        MediaParsingService.Companion.getProgress().observe(this, new Observer<ScanProgress>() {
            @Override
            public void onChanged(@Nullable ScanProgress scanProgress) {
                if (scanProgress != null) onParsingServiceProgress();
            }
        });
        Medialibrary.getState().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean started) {
                if (started == null) return;
                if (started) onParsingServiceStarted();
                else onParsingServiceFinished();
            }
        });
        MediaParsingService.Companion.getNewStorages().observe(this, new Observer<List<String>>() {
            @Override
            public void onChanged(@Nullable List<String> devices) {
                if (devices == null) return;
                for (String device : devices) UiTools.newStorageDetected(BaseTvActivity.this, device);
                MediaParsingService.Companion.getNewStorages().setValue(null);
            }
        });
    }
}
