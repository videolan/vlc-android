/*****************************************************************************
 * MainTvActivity.java
 *****************************************************************************
 * Copyright © 2014-2018 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc.gui.tv;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;

import org.videolan.vlc.MediaParsingServiceKt;
import org.videolan.vlc.R;
import org.videolan.vlc.StartActivity;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.tv.browser.BaseTvActivity;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.Util;

import androidx.fragment.app.FragmentManager;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class MainTvActivity extends BaseTvActivity {

    public static final int ACTIVITY_RESULT_PREFERENCES = 1;

    public static final String BROWSER_TYPE = "browser_type";

    public static final String TAG = "VLC/MainTvActivity";

    protected MainTvFragment mBrowseFragment;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Util.checkCpuCompatibility(this);

        // Delay access permission dialog prompt to avoid background corruption
        if (!Permissions.canReadStorage(this))
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Permissions.checkReadStoragePermission(MainTvActivity.this, false);
                }
            }, 1000);

        setContentView(R.layout.tv_main);

        final FragmentManager fragmentManager = getSupportFragmentManager();
        mBrowseFragment = (MainTvFragment) fragmentManager.findFragmentById(R.id.browse_fragment);
        mProgressBar = findViewById(R.id.tv_main_progress);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            switch (resultCode) {
                case PreferencesActivity.RESULT_RESCAN:
                    MediaParsingServiceKt.reload(this);
                    break;
                case PreferencesActivity.RESULT_RESTART:
                case PreferencesActivity.RESULT_RESTART_APP:
                    Intent intent = getIntent();
                    intent.setClass(this, resultCode == PreferencesActivity.RESULT_RESTART_APP ? StartActivity.class : MainTvActivity.class);
                    finish();
                    startActivity(intent);
                    break;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_BUTTON_Y)) {
            return mBrowseFragment.showDetails();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onParsingServiceStarted() {
        mHandler.sendEmptyMessageDelayed(SHOW_LOADING, 300);
    }

    @Override
    protected void onParsingServiceProgress() {
        if (mProgressBar.getVisibility() == View.GONE) mHandler.sendEmptyMessage(SHOW_LOADING);
    }

    @Override
    protected void onParsingServiceFinished() {
        mHandler.sendEmptyMessage(HIDE_LOADING);
    }

    public void hideLoading() {
        mHandler.sendEmptyMessage(HIDE_LOADING);
    }
    private static final int SHOW_LOADING = 0;
    private static final int HIDE_LOADING = 1;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_LOADING:
                    mProgressBar.setVisibility(View.VISIBLE);
                    break;
                case HIDE_LOADING:
                    removeMessages(SHOW_LOADING);
                    mProgressBar.setVisibility(View.GONE);
                default:
                    super.handleMessage(msg);
            }
        }
    };

    protected void refresh() {
        mMediaLibrary.reload();
    }
}
