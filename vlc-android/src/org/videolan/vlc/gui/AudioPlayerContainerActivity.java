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
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.gui.audio.AudioPlayer;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.widget.HackyDrawerLayout;
import org.videolan.vlc.widget.SlidingPaneLayout;

public class AudioPlayerContainerActivity extends AppCompatActivity {

    public static final String ACTION_SHOW_PLAYER = "org.videolan.vlc.gui.ShowPlayer";

    protected ActionBar mActionBar;
    protected Toolbar mToolbar;
    protected AudioPlayer mAudioPlayer;
    protected AudioServiceController mAudioController;
    protected SlidingPaneLayout mSlidingPane;
    protected View mAudioPlayerFilling;
    protected SharedPreferences mSettings;
    protected ViewGroup mRootContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* Get settings */
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        /* Theme must be applied before super.onCreate */
        applyTheme();
        super.onCreate(savedInstanceState);
    }

    protected void initAudioPlayerContainerActivity(){

        mRootContainer = (ViewGroup) ((ViewGroup) this
                .findViewById(android.R.id.content)).getChildAt(0);
        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(mToolbar);
        mActionBar = getSupportActionBar();

        mSlidingPane = (SlidingPaneLayout) findViewById(R.id.pane);
        mSlidingPane.setPanelSlideListener(mPanelSlideListener);
        mAudioPlayerFilling = findViewById(R.id.audio_player_filling);

        /* Set up the audio player */
        mAudioPlayer = new AudioPlayer();
        mAudioPlayer.setUserVisibleHint(false);
        mAudioController = AudioServiceController.getInstance();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.audio_player, mAudioPlayer)
                .commit();
    }

    @Override
    protected void onStart() {
        super.onStart();

        /* Prepare the progressBar */
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW_PLAYER);
        registerReceiver(messageReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(messageReceiver);
        } catch (IllegalArgumentException e) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAudioController.addAudioPlayer(mAudioPlayer);
        AudioServiceController.getInstance().bindAudioService(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAudioController.removeAudioPlayer(mAudioPlayer);
        AudioServiceController.getInstance().unbindAudioService(this);
    }

    private void applyTheme() {
        boolean enableBlackTheme = mSettings.getBoolean("enable_black_theme", false);
        if (enableBlackTheme) {
            setTheme(R.style.Theme_VLC_Black);
        }
    }

    /**
     * Show a tip view.
     * @param layoutId the layout of the tip view
     * @param settingKey the setting key to check if the view must be displayed or not.
     */
    public void showTipViewIfNeeded(final int layoutId, final String settingKey) {
        if (!mSettings.getBoolean(settingKey, false) && !BuildConfig.tv) {
            removeTipViewIfDisplayed();
            View v = LayoutInflater.from(this).inflate(layoutId, null);
            mRootContainer.addView(v,
                    new HackyDrawerLayout.LayoutParams(HackyDrawerLayout.LayoutParams.MATCH_PARENT,
                            HackyDrawerLayout.LayoutParams.MATCH_PARENT));

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
        if (mRootContainer.getChildCount() > 2){
            for (int i = 0 ; i< mRootContainer.getChildCount() ; ++i){
                if (mRootContainer.getChildAt(i).getId() == R.id.audio_tips)
                    mRootContainer.removeViewAt(i);
            }
        }
    }
    /**
     * Show the audio player.
     */
    public void showAudioPlayer() {
        mActionBar.collapseActionView();
        // Open the pane only if is entirely opened.
        if (mSlidingPane.getState() == mSlidingPane.STATE_OPENED_ENTIRELY)
            mSlidingPane.openPane();
        mAudioPlayerFilling.setVisibility(View.VISIBLE);
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
            int resId = Util.getResourceFromAttribute(AudioPlayerContainerActivity.this, R.attr.shadow_bottom_9patch);
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

}
