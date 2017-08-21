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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.ViewStubCompat;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioPlayer;
import org.videolan.vlc.gui.audio.EqualizerFragment;
import org.videolan.vlc.gui.browser.StorageBrowserFragment;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.WeakHandler;

public class AudioPlayerContainerActivity extends BaseActivity implements PlaybackService.Client.Callback {

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
    protected AppBarLayout mAppBarLayout;
    protected Toolbar mToolbar;
    protected AudioPlayer mAudioPlayer;
    private FrameLayout mAudioPlayerContainer;
    private final PlaybackServiceActivity.Helper mHelper = new PlaybackServiceActivity.Helper(this, this);
    protected PlaybackService mService;
    protected BottomSheetBehavior mBottomSheetBehavior;
    protected View mFragmentContainer;
    private int mOriginalBottomPadding;
    private View mScanProgressLayout;
    private TextView mScanProgressText;
    private ProgressBar mScanProgressBar;

    protected boolean mPreventRescan = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Init Medialibrary if KO
        if (savedInstanceState != null) {
            VLCApplication.setLocale();
            if (!VLCApplication.getMLInstance().isInitiated() && Permissions.canReadStorage())
                startService(new Intent(MediaParsingService.ACTION_INIT, null, this, MediaParsingService.class));
        }
        MediaUtils.updateSubsDownloaderActivity(this);
        super.onCreate(savedInstanceState);
    }

    protected void initAudioPlayerContainerActivity() {
        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(mToolbar);
        mAppBarLayout = (AppBarLayout) findViewById(R.id.appbar);
        mAppBarLayout.setExpanded(true);
        mAudioPlayerContainer = (FrameLayout) findViewById(R.id.audio_player_container);
    }

    private void initAudioPlayer() {
        ((ViewStubCompat)findViewById(R.id.audio_player_stub)).inflate();
        mAudioPlayer = (AudioPlayer) getSupportFragmentManager().findFragmentById(R.id.audio_player);
        mAudioPlayer.setUserVisibleHint(false);
        mBottomSheetBehavior = BottomSheetBehavior.from(mAudioPlayerContainer);
        mBottomSheetBehavior.setPeekHeight(getResources().getDimensionPixelSize(R.dimen.player_peek_height));
        mBottomSheetBehavior.setBottomSheetCallback(mAudioPlayerBottomSheetCallback);
        showTipViewIfNeeded(R.id.audio_player_tips, AudioPlayer.PREF_AUDIOPLAYER_TIPS_SHOWN);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (mFragmentContainer == null)
            mFragmentContainer = findViewById(R.id.fragment_placeholder);
        mOriginalBottomPadding = mFragmentContainer.getPaddingBottom();
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
        IntentFilter playerFilter = new IntentFilter();
        playerFilter.addAction(ACTION_SHOW_PLAYER);
        registerReceiver(messageReceiver, playerFilter);
        IntentFilter progressFilter = new IntentFilter(MediaParsingService.ACTION_SERVICE_STARTED);
        progressFilter.addAction(MediaParsingService.ACTION_SERVICE_ENDED);
        progressFilter.addAction(MediaParsingService.ACTION_PROGRESS);
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, progressFilter);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mPreventRescan = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBottomSheetBehavior != null && mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            updateContainerPadding(true);
            applyMarginToProgressBar(mBottomSheetBehavior.getPeekHeight());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(storageReceiver);
        unregisterReceiver(messageReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        mHelper.onStop();
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
        if (service.hasMedia() && !mService.isVideoPlaying())
            showAudioPlayer();
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }

    @Override
    public void onBackPressed() {
        if (slideDownAudioPlayer())
            return;
        super.onBackPressed();
    }

    protected Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
                // Current fragment loaded
                Fragment current = getCurrentFragment();
                if (current instanceof StorageBrowserFragment)
                    ((StorageBrowserFragment) current).goBack();
                else
                    finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void updateLib() {
        if (mPreventRescan) {
            mPreventRescan = false;
            return;
        }
        FragmentManager fm = getSupportFragmentManager();
        Fragment current = fm.findFragmentById(R.id.fragment_placeholder);
        if (current != null && current instanceof IRefreshable)
            ((IRefreshable) current).refresh();
    }

    /**
     * Show a tip view.
     * @param stubId the stub of the tip view
     * @param settingKey the setting key to check if the view must be displayed or not.
     */
    public void showTipViewIfNeeded(final int stubId, final String settingKey) {
        if (BuildConfig.DEBUG)
            return;
        View vsc = findViewById(stubId);
        if (vsc != null && !mSettings.getBoolean(settingKey, false) && !VLCApplication.showTvUi()) {
            View v = ((ViewStubCompat)vsc).inflate();
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
                    editor.apply();
                }
            });
        }
    }

    /**
     * Remove the current tip view if there is one displayed.
     */
    public void removeTipViewIfDisplayed() {
        View tips = findViewById(R.id.audio_tips);
        if (tips != null)
            ((ViewGroup) tips.getParent()).removeView(tips);
    }
    /**
     * Show the audio player.
     */
    public synchronized void showAudioPlayer() {
        if (!isAudioPlayerReady())
            initAudioPlayer();
        if (mAudioPlayerContainer.getVisibility() == View.GONE) {
            mAudioPlayerContainer.setVisibility(View.VISIBLE);
            updateContainerPadding(true);
            applyMarginToProgressBar(mBottomSheetBehavior.getPeekHeight());
        }
        if (mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    /**
     * Slide down the audio player.
     * @return true on success else false.
     */
    public boolean slideDownAudioPlayer() {
        if (!isAudioPlayerReady())
            return false;
        if (mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            return true;
        }
        return false;
    }

    /**
     * Slide up and down the audio player depending on its current state.
     */
    public void slideUpOrDownAudioPlayer() {
        if (!isAudioPlayerReady())
            return;
        if (mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN)
            return;
        mBottomSheetBehavior.setState(mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED?
                BottomSheetBehavior.STATE_COLLAPSED : BottomSheetBehavior.STATE_EXPANDED);
    }

    /**
     * Hide the audio player.
     */
    public void hideAudioPlayer() {
        if (!isAudioPlayerReady())
            return;
        mBottomSheetBehavior.setHideable(true);
        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void updateProgressVisibility(int visibility) {
        boolean show = visibility == View.VISIBLE;
        if ((mScanProgressLayout == null && !show) ||
                (mScanProgressLayout != null && mScanProgressLayout.getVisibility() == visibility))
            return;
        if (show)
            mActivityHandler.sendEmptyMessageDelayed(ACTION_DISPLAY_PROGRESSBAR, 1000);
        else if (mScanProgressLayout != null)
            mScanProgressLayout.setVisibility(visibility);
    }

    private void showProgressBar() {
        View vsc = findViewById(R.id.scan_viewstub);
        if (vsc != null) {
            vsc.setVisibility(View.VISIBLE);
            mScanProgressLayout = findViewById(R.id.scan_progress_layout);
            mScanProgressText = (TextView) findViewById(R.id.scan_progress_text);
            mScanProgressBar = (ProgressBar) findViewById(R.id.scan_progress_bar);
            if (mBottomSheetBehavior != null && mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                updateContainerPadding(true);
                applyMarginToProgressBar(mBottomSheetBehavior.getPeekHeight());
            }
        } else if (mScanProgressLayout != null)
            mScanProgressLayout.setVisibility(View.VISIBLE);
    }

    private void updateContainerPadding(boolean show) {
        int factor = show ? 1 : 0;
        mFragmentContainer.setPadding(mFragmentContainer.getPaddingLeft(),
                mFragmentContainer.getPaddingTop(), mFragmentContainer.getPaddingRight(),
                mOriginalBottomPadding+factor*mBottomSheetBehavior.getPeekHeight());
    }

    private void applyMarginToProgressBar(int marginValue) {
        if (mScanProgressLayout != null && mScanProgressLayout.getVisibility() == View.VISIBLE) {
            CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) mScanProgressLayout.getLayoutParams();
            lp.bottomMargin = marginValue;
            mScanProgressLayout.setLayoutParams(lp);
        }
    }

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_SHOW_PLAYER.equals(action))
                showAudioPlayer();
            else if (MediaParsingService.ACTION_SERVICE_STARTED.equals(action))
                updateProgressVisibility(View.VISIBLE);
            else if (MediaParsingService.ACTION_SERVICE_ENDED.equals(action)) {
                mActivityHandler.removeMessages(ACTION_DISPLAY_PROGRESSBAR);
                updateProgressVisibility(View.GONE);
            } else if (MediaParsingService.ACTION_PROGRESS.equals(action)) {
                updateProgressVisibility(View.VISIBLE);
                if (mScanProgressText != null)
                    mScanProgressText.setText(intent.getStringExtra(MediaParsingService.ACTION_PROGRESS_TEXT));
                if (mScanProgressBar != null)
                    mScanProgressBar.setProgress(intent.getIntExtra(MediaParsingService.ACTION_PROGRESS_VALUE, 0));
            }
        }
    };

    private final BroadcastReceiver storageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_MOUNTED))
                mActivityHandler.obtainMessage(ACTION_MEDIA_MOUNTED, intent.getData()).sendToTarget();
            else if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_UNMOUNTED))
                mActivityHandler.sendMessageDelayed(mActivityHandler.obtainMessage(ACTION_MEDIA_UNMOUNTED, intent.getData()), 100);
        }
    };

    Handler mActivityHandler = new StorageHandler(this);
    AudioPlayerBottomSheetCallback mAudioPlayerBottomSheetCallback = new AudioPlayerBottomSheetCallback();

    private static final int ACTION_MEDIA_MOUNTED = 1337;
    private static final int ACTION_MEDIA_UNMOUNTED = 1338;
    private static final int ACTION_DISPLAY_PROGRESSBAR = 1339;

    public boolean isAudioPlayerReady() {
        return mAudioPlayer != null;
    }

    public boolean isAudioPlayerExpanded() {
        return isAudioPlayerReady() && mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED;
    }

    private class AudioPlayerBottomSheetCallback extends BottomSheetBehavior.BottomSheetCallback {
        @Override
        public void onStateChanged(@NonNull View bottomSheet, int newState) {
            AudioPlayerContainerActivity.this.onPlayerStateChanged(bottomSheet, newState);
            mAudioPlayer.onStateChanged(newState);
            switch (newState) {
                case BottomSheetBehavior.STATE_COLLAPSED:
                    removeTipViewIfDisplayed();
                    updateContainerPadding(true);
                    applyMarginToProgressBar(mBottomSheetBehavior.getPeekHeight());
                    break;
                case BottomSheetBehavior.STATE_HIDDEN:
                    removeTipViewIfDisplayed();
                    updateContainerPadding(false);
                    applyMarginToProgressBar(0);
                    break;
            }
        }

        @Override
        public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
    }

    protected void onPlayerStateChanged(View bottomSheet, int newState) {}

    private static class StorageHandler extends WeakHandler<AudioPlayerContainerActivity> {

        StorageHandler(AudioPlayerContainerActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            AudioPlayerContainerActivity owner = getOwner();
            if (owner == null)
                return;
            String uuid;
            switch (msg.what){
                case ACTION_MEDIA_MOUNTED:
                    uuid = ((Uri) msg.obj).getLastPathSegment();
                    String path = ((Uri) msg.obj).getPath();
                    removeMessages(ACTION_MEDIA_UNMOUNTED);
                    if (!TextUtils.isEmpty(uuid)
                            && !PreferenceManager.getDefaultSharedPreferences(owner).getBoolean("ignore_"+ uuid, false)) {
                        if (VLCApplication.getMLInstance().addDevice(uuid, path, true, true)) {
                            owner.startActivity(new Intent(owner, DialogActivity.class)
                                    .setAction(DialogActivity.KEY_STORAGE)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra(MediaParsingService.EXTRA_PATH, path));
                        } else
                            owner.startService(new Intent(MediaParsingService.ACTION_RELOAD, null, owner, MediaParsingService.class)
                                    .putExtra(MediaParsingService.EXTRA_PATH, path));
                    }
                    break;
                case ACTION_MEDIA_UNMOUNTED:
                    uuid = ((Uri) msg.obj).getLastPathSegment();
                    VLCApplication.getMLInstance().removeDevice(uuid);
                    LocalBroadcastManager.getInstance(owner).sendBroadcast(new Intent(MediaParsingService.ACTION_SERVICE_ENDED));
                    break;
                case ACTION_DISPLAY_PROGRESSBAR:
                    removeMessages(ACTION_DISPLAY_PROGRESSBAR);
                    owner.showProgressBar();
                    break;
            }
        }
    }

    public PlaybackServiceActivity.Helper getHelper() {
        return mHelper;
    }
}
