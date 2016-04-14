/*
 * *************************************************************************
 *  SlidingPaneActivity.java
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

package org.videolan.vlc.gui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.widget.SlidingPaneLayout;

import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioPlayer;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.WeakHandler;

public class AudioPlayerContainerActivity extends AppCompatActivity implements PlaybackService.Client.Callback  {

    public static final String TAG = "VLC/AudioPlayerContainerActivity";
    public static final String ACTION_SHOW_PLAYER = Strings.buildPkgString("gui.ShowPlayer");

    protected static final String ID_VIDEO = "video";
    protected static final String ID_AUDIO = "audio";
    protected static final String ID_NETWORK = "network";
    protected static final String ID_DIRECTORIES = "directories";
    protected static final String ID_HISTORY = "history";
    protected static final String ID_MRL = "mrl";
    protected static final String ID_PREFERENCES = "preferences";
    protected static final String ID_ABOUT = "about";

    protected ActionBar mActionBar;
    protected Toolbar mToolbar;
    protected AudioPlayer mAudioPlayer;
    protected SlidingPaneLayout mSlidingPane;
    protected View mAudioPlayerFilling;
    protected SharedPreferences mSettings;
    private final PlaybackServiceActivity.Helper mHelper = new PlaybackServiceActivity.Helper(this, this);
    protected PlaybackService mService;

    protected boolean mPreventRescan = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* Get settings */
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        /* Theme must be applied before super.onCreate */
        applyTheme();

        /* Set up the audio player */
        mAudioPlayer = new AudioPlayer();

        MediaUtils.updateSubsDownloaderActivity(this);

        super.onCreate(savedInstanceState);
    }

    protected void initAudioPlayerContainerActivity(){

        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(mToolbar);
        mActionBar = getSupportActionBar();

        mSlidingPane = (SlidingPaneLayout) findViewById(R.id.pane);
        mSlidingPane.setPanelSlideListener(mPanelSlideListener);
        mAudioPlayerFilling = findViewById(R.id.audio_player_filling);

        mAudioPlayer.setUserVisibleHint(false);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.audio_player, mAudioPlayer)
                .commit();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mHelper.onStart();

        //Handle external storage state
        IntentFilter storageFilter = new IntentFilter();
        storageFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        storageFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        storageFilter.addDataScheme("file");
        registerReceiver(storageReceiver, storageFilter);

        /* Prepare the progressBar */
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW_PLAYER);
        registerReceiver(messageReceiver, filter);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mPreventRescan = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(storageReceiver);
        try {
            unregisterReceiver(messageReceiver);
        } catch (IllegalArgumentException e) {}
        mHelper.onStop();
    }

    private void applyTheme() {
        boolean enableBlackTheme = mSettings.getBoolean("enable_black_theme", false);
        if (VLCApplication.showTvUi() || enableBlackTheme) {
            setTheme(R.style.Theme_VLC_Black);
        }
    }

    public void updateLib() {
        if (mPreventRescan){
            mPreventRescan = false;
            return;
        }
        FragmentManager fm = getSupportFragmentManager();
        Fragment current = fm.findFragmentById(R.id.fragment_placeholder);
        if (current != null && current instanceof IRefreshable)
            ((IRefreshable) current).refresh();
        else
            MediaLibrary.getInstance().scanMediaItems();
        Fragment fragment = fm.findFragmentByTag(ID_AUDIO);
        if (fragment != null && !fragment.equals(current)) {
            ((MediaBrowserFragment)fragment).clear();
        }
        fragment = fm.findFragmentByTag(ID_VIDEO);
        if (fragment != null && !fragment.equals(current)) {
            ((MediaBrowserFragment)fragment).clear();
        }
    }

    /**
     * Show a tip view.
     * @param layoutId the layout of the tip view
     * @param settingKey the setting key to check if the view must be displayed or not.
     */
    public void showTipViewIfNeeded(final int layoutId, final String settingKey) {
        if (BuildConfig.DEBUG)
            return;
        if (!mSettings.getBoolean(settingKey, false) && !VLCApplication.showTvUi()) {
            removeTipViewIfDisplayed();
            View v = LayoutInflater.from(this).inflate(layoutId, null);
            ViewGroup root = (ViewGroup) findViewById(R.id.pane).getParent();
            root.addView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeTipViewIfDisplayed();
                }
            });

            TextView okGotIt = (TextView) v.findViewById(R.id.okgotit_button);
            okGotIt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeTipViewIfDisplayed();
                    SharedPreferences.Editor editor = mSettings.edit();
                    editor.putBoolean(settingKey, true);
                    Util.commitPreferences(editor);
                }
            });
        }
    }

    /**
     * Remove the current tip view if there is one displayed.
     */
    public void removeTipViewIfDisplayed() {
            ViewGroup root = (ViewGroup) findViewById(R.id.pane).getParent();
        if (root.getChildCount() > 2){
            for (int i = 0 ; i< root.getChildCount() ; ++i){
                if (root.getChildAt(i).getId() == R.id.audio_tips)
                    root.removeViewAt(i);
            }
        }
    }
    /**
     * Show the audio player.
     */
    public void showAudioPlayer() {
        mActivityHandler.post(new Runnable() {
            @Override
            public void run() {
                mActionBar.collapseActionView();
                // Open the pane only if is entirely opened.
                if (mSlidingPane.getState() == mSlidingPane.STATE_OPENED_ENTIRELY)
                    mSlidingPane.openPane();
                mAudioPlayerFilling.setVisibility(View.VISIBLE);
            }
        });
    }

    public int  getSlidingPaneState() {
        return mSlidingPane.getState();
    }

    /**
     * Slide down the audio player.
     * @return true on success else false.
     */
    public boolean slideDownAudioPlayer() {
        if (mSlidingPane.getState() == mSlidingPane.STATE_CLOSED) {
            mSlidingPane.openPane();
            return true;
        }
        return false;
    }

    /**
     * Slide up and down the audio player depending on its current state.
     */
    public void slideUpOrDownAudioPlayer() {
        if (mSlidingPane.getState() == mSlidingPane.STATE_CLOSED){
            mActionBar.show();
            mSlidingPane.openPane();
        } else if (mSlidingPane.getState() == mSlidingPane.STATE_OPENED){
            mActionBar.hide();
            mSlidingPane.closePane();
        }
    }

    /**
     * Hide the audio player.
     */
    public void hideAudioPlayer() {
        mSlidingPane.openPaneEntirely();
        mAudioPlayerFilling.setVisibility(View.GONE);
    }

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(ACTION_SHOW_PLAYER)) {
                showAudioPlayer();
            }
        }
    };

    private final SlidingPaneLayout.PanelSlideListener mPanelSlideListener
            = new SlidingPaneLayout.PanelSlideListener() {
        float previousOffset =  1.0f;
        @Override
        public void onPanelSlide(float slideOffset) {
            if (slideOffset >= 0.1 && slideOffset > previousOffset && !mActionBar.isShowing())
                mActionBar.show();
            else if (slideOffset <= 0.1 && slideOffset < previousOffset && mActionBar.isShowing())
                mActionBar.hide();
            previousOffset = slideOffset;
        }

        @Override
        public void onPanelOpened() {
            int resId = UiTools.getResourceFromAttribute(AudioPlayerContainerActivity.this, R.attr.shadow_bottom_9patch);
            if (resId != 0)
                mSlidingPane.setShadowResource(resId);
            mAudioPlayer.setHeaderVisibilities(false, false, true, true, true, false);
            mAudioPlayer.setUserVisibleHint(false);
            onPanelOpenedUiSet();
            mAudioPlayer.showAudioPlayerTips();
        }

        @Override
        public void onPanelOpenedEntirely() {
            mAudioPlayer.setUserVisibleHint(false);
            mSlidingPane.setShadowDrawable(null);
            onPanelOpenedEntirelyUiSet();
        }

        @Override
        public void onPanelClosed() {
            mAudioPlayer.setUserVisibleHint(true);
            mAudioPlayer.setHeaderVisibilities(true, true, false, false, false, true);
            onPanelClosedUiSet();
            mAudioPlayer.showPlaylistTips();
        }

    };

    protected void onPanelClosedUiSet() {}

    protected void onPanelOpenedEntirelyUiSet() {}

    protected void onPanelOpenedUiSet() {}

    private void stopBackgroundTasks() {
        MediaLibrary ml = MediaLibrary.getInstance();
        if (ml.isWorking())
            ml.stop();
    }

    private final BroadcastReceiver storageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_MOUNTED)) {
                mActivityHandler.sendEmptyMessage(ACTION_MEDIA_MOUNTED);
            } else if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_UNMOUNTED)) {
                mActivityHandler.sendEmptyMessageDelayed(ACTION_MEDIA_UNMOUNTED, 100);
            }
        }
    };

    Handler mActivityHandler = new StorageHandler(this);

    private static final int ACTION_MEDIA_MOUNTED = 1337;
    private static final int ACTION_MEDIA_UNMOUNTED = 1338;

    private static class StorageHandler extends WeakHandler<AudioPlayerContainerActivity> {

        public StorageHandler(AudioPlayerContainerActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what){
                case ACTION_MEDIA_MOUNTED:
                    removeMessages(ACTION_MEDIA_UNMOUNTED);
                    getOwner().updateLib();
                    break;
                case ACTION_MEDIA_UNMOUNTED:
                    getOwner().stopBackgroundTasks();
                    getOwner().updateLib();
                    break;
            }
        }
    }

    public PlaybackServiceActivity.Helper getHelper() {
        return mHelper;
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }
}
