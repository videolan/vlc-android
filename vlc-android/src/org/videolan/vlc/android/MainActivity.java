/*****************************************************************************
 * MainActivity.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

package org.videolan.vlc.android;

import org.videolan.vlc.android.widget.AudioMiniPlayer;

import android.app.Activity;
import android.app.ActivityGroup;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;

public class MainActivity extends TabActivity {
    public final static String TAG = "VLC/MainActivity";

    protected static final int HIDE_PROGRESSBAR = 0;
    protected static final int SHOW_PROGRESSBAR = 1;
    protected static final int SHOW_TEXTINFO = 2;
    private static final int VIDEO_TAB = 0;
    private static final int AUDIO_TAB = 1;
    public static final String START_FROM_NOTIFICATION = "from_notification";
    private static final String PREF_SHOW_INFO = "show_info";

    private static MainActivity mInstance;
    private ProgressBar mProgressBar;
    private TabHost mTabHost;
    private int mCurrentState = 0;
    private ImageButton mChangeTab;
    private AudioMiniPlayer mAudioPlayer;
    private AudioServiceController mAudioController;
    private View mInfoLayout;
    private ProgressBar mInfoProgress;
    private TextView mInfoText;

    private SharedPreferences mSettings;

    private int mVersionNumber = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.main);
        super.onCreate(savedInstanceState);

        /* Get settings */
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        LibVLC.useIOMX(this);

        /* Initialize variables */
        mInstance = this;
        mProgressBar = (ProgressBar) findViewById(R.id.ml_progress_bar);
        mChangeTab = (ImageButton) findViewById(R.id.change_tab);
        mInfoLayout = (View) findViewById(R.id.info_layout);
        mInfoProgress = (ProgressBar) findViewById(R.id.info_progress);
        mInfoText = (TextView) findViewById(R.id.info_text);

        /* Initialize the TabView */
        mTabHost = getTabHost();
        mTabHost.addTab(mTabHost.newTabSpec("VIDEO TAB").setIndicator("VIDEO TAB")
                .setContent(new Intent(this, VideoActivityGroup.class)));

        mTabHost.addTab(mTabHost.newTabSpec("AUDIO TAB").setIndicator("AUDIO TAB")
                .setContent(new Intent(this, AudioActivityGroup.class)));

        // add mini audio player
        mAudioPlayer = (AudioMiniPlayer) findViewById(R.id.audio_mini_player);
        mAudioController = AudioServiceController.getInstance();
        mAudioPlayer.setAudioPlayerControl(mAudioController);
        mAudioPlayer.update();

        // Start audio player when audio is playing
        if (getIntent().hasExtra(START_FROM_NOTIFICATION)) {
            Log.d(TAG, "Started from notification.");
            showAudioTab();
        } else {
            // load the last tab-state
            int state = savedInstanceState == null ? VIDEO_TAB : savedInstanceState.getInt("mCurrentState");
            if(state == VIDEO_TAB)
                showVideoTab();
            else
                showAudioTab();
        }

        /* Show info/alpha/beta Warning */
        PackageInfo pinfo = null;
        try {
            pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "package info not found.");
        }
        if (pinfo != null) {
            mVersionNumber = pinfo.versionCode;

            if (mSettings.getInt(PREF_SHOW_INFO, -1) != mVersionNumber)
                showInfoDialog();
        }

        /* Load media items from database and storage */
        MediaLibrary.getInstance(this).loadMediaItems();
    }

    @Override
    protected void onResume() {
        mAudioController.addAudioPlayer(mAudioPlayer);
        super.onResume();
    }

    @Override
    protected void onPause() {
        mAudioController.removeAudioPlayer(mAudioPlayer);
        super.onPause();
    }

    /** Create menu from XML
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.media_library, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onSearchRequested() {
        Intent intent = new Intent(this, SearchActivity.class);
        startActivity(intent);
        return false;
    }

    /**
     * Save currently opened tab (video/audio) for above
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt("mCurrentState", mCurrentState);
        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Intent to start new Activity
        Intent intent;

        Activity activity;

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.ml_menu_sortby_name:
            case R.id.ml_menu_sortby_length:
                activity = getCurrentActivity();
                if (!(activity instanceof ActivityGroup))
                    break;
                activity = ((ActivityGroup) activity).getCurrentActivity();
                if (activity instanceof Sortable)
                    ((Sortable) activity).sortBy(item.getItemId() == R.id.ml_menu_sortby_name
                            ? VideoListAdapter.SORT_BY_TITLE
                            : VideoListAdapter.SORT_BY_LENGTH);
                break;
            // About
            case R.id.ml_menu_about:
                intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                break;
            // Preferences
            case R.id.ml_menu_preferences:
                intent = new Intent(this, PreferencesActivity.class);
                startActivity(intent);
                break;
            // Refresh
            case R.id.ml_menu_refresh:
                MediaLibrary.getInstance(this).loadMediaItems();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.play_pause:
                if (mAudioController.isPlaying()) {
                    mAudioController.pause();
                } else {
                    mAudioController.play();
                }
                break;
            case R.id.show_player:
                Intent intent = new Intent(this, AudioPlayerActivity.class);
                startActivity(intent);
                break;
            case R.id.hide_mini_player:
                hideAudioPlayer();
                break;
        }
        return super.onContextItemSelected(item);
    }

    public void hideAudioPlayer() {
        mAudioPlayer.setVisibility(AudioMiniPlayer.GONE);
        mAudioController.stop();
    }

    public void showAudioPlayer() {
        mAudioPlayer.setVisibility(AudioMiniPlayer.VISIBLE);
    }

    private void showInfoDialog() {
        final Dialog infoDialog = new Dialog(this, R.style.info_dialog);
        infoDialog.setContentView(R.layout.info_dialog);
        Button okButton = (Button) infoDialog.findViewById(R.id.ok);
        okButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckBox notShowAgain =
                        (CheckBox) infoDialog.findViewById(R.id.not_show_again);
                if (notShowAgain.isChecked() && mSettings != null) {
                    Editor editor = mSettings.edit();
                    editor.putInt(PREF_SHOW_INFO, mVersionNumber);
                    editor.commit();
                }
                infoDialog.dismiss();
            }
        });
        infoDialog.show();
    }

    /**
     * onClick event from xml
     * @param view
     */
    public void changeTabClick(View view) {
        // Toggle audio- and video-tab
        if (mCurrentState == VIDEO_TAB) {
            showAudioTab();
        } else {
            showVideoTab();
        }
    }

    private void showVideoTab() {
        mChangeTab.setImageResource(R.drawable.header_icon_audio);
        mTabHost.setCurrentTab(VIDEO_TAB);
        mCurrentState = VIDEO_TAB;
    }

    private void showAudioTab() {
        mChangeTab.setImageResource(R.drawable.header_icon_video);
        mTabHost.setCurrentTab(AUDIO_TAB);
        mCurrentState = AUDIO_TAB;
    }

    /**
     * onClick event from xml
     * @param view
     */
    public void searchClick(View view) {
        onSearchRequested();
    }

    /**
     * Get instance e.g. for Context or Handler
     * @return this ;)
     */
    public static MainActivity getInstance() {
        return mInstance;
    }

    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_PROGRESSBAR:
                mProgressBar.setVisibility(View.VISIBLE);
                break;
            case HIDE_PROGRESSBAR:
                mProgressBar.setVisibility(View.INVISIBLE);
                break;
            case SHOW_TEXTINFO:
                Bundle b = msg.getData();
                String info = b.getString("info");
                mInfoText.setText(info);
                mInfoProgress.setMax(b.getInt("max"));
                mInfoProgress.setProgress(b.getInt("progress"));
                mInfoLayout.setVisibility(info != null ? View.VISIBLE : View.GONE);
                break;
        }
    };
    };

    public static void clearTextInfo(Handler handler)
    {
        Message m = handler.obtainMessage(MainActivity.SHOW_TEXTINFO);
        Bundle b = m.getData();
        b.putString("info", null);
        b.putInt("progress", 0);
        b.putInt("max", 100);
        handler.sendMessage(m);
    }

    public static void sendTextInfo(Handler handler, String info, int progress, int max) {
        Message m = handler.obtainMessage(MainActivity.SHOW_TEXTINFO);
        Bundle b = m.getData();
        b.putString("info", info);
        b.putInt("progress", progress);
        b.putInt("max", max);
        handler.sendMessage(m);
    }
}
