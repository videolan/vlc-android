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

package org.videolan.vlc.gui;

import java.io.IOException;
import java.util.ArrayList;

import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.LibVLC;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCCallbackTask;
import org.videolan.vlc.gui.audio.AudioBrowserActivity;
import org.videolan.vlc.gui.video.VideoListActivity;
import org.videolan.vlc.gui.video.VideoListAdapter;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.widget.AudioMiniPlayer;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;

public class MainActivity extends SherlockFragmentActivity {
    public final static String TAG = "VLC/MainActivity";

    protected static final String ACTION_SHOW_PROGRESSBAR = "org.videolan.vlc.gui.ShowProgressBar";
    protected static final String ACTION_HIDE_PROGRESSBAR = "org.videolan.vlc.gui.HideProgressBar";
    protected static final String ACTION_SHOW_TEXTINFO = "org.videolan.vlc.gui.ShowTextInfo";

    private static final int VIDEO_TAB = 0;
    private static final int AUDIO_TAB = 1;
    public static final String START_FROM_NOTIFICATION = "from_notification";
    private static final String PREF_SHOW_INFO = "show_info";

    private ActionBar mActionBar;
    private AudioMiniPlayer mAudioPlayer;
    private AudioServiceController mAudioController;
    private View mInfoLayout;
    private ProgressBar mInfoProgress;
    private TextView mInfoText;

    private SharedPreferences mSettings;

    private int mVersionNumber = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);

        super.onCreate(savedInstanceState);

        /* Get settings */
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        LibVLC.useIOMX(this);

        /* Initialize variables */
        mInfoLayout = (View) findViewById(R.id.info_layout);
        mInfoProgress = (ProgressBar) findViewById(R.id.info_progress);
        mInfoText = (TextView) findViewById(R.id.info_text);
        
        /* Initialize the tabs */
        
        mActionBar = getSupportActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        mActionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        
        mActionBar.addTab(mActionBar.newTab()
                .setText("Video")
                .setIcon(R.drawable.header_icon_video)
                .setTabListener(new TabListener<VideoListActivity>(
                        this, "video", VideoListActivity.class)));
        
        mActionBar.addTab(mActionBar.newTab()
                .setText("Audio")
                .setIcon(R.drawable.header_icon_audio)
                .setTabListener(new TabListener<AudioBrowserActivity>(
                        this, "audio", AudioBrowserActivity.class)));
        
        if (savedInstanceState != null) {
            mActionBar.setSelectedNavigationItem(savedInstanceState.getInt("tab", 0));
        }

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

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW_PROGRESSBAR);
        filter.addAction(ACTION_HIDE_PROGRESSBAR);
        filter.addAction(ACTION_SHOW_TEXTINFO);
        registerReceiver(messageReceiver, filter);

        /* Load media items from database and storage */
        MediaLibrary.getInstance(this).loadMediaItems(this);
    }

    @Override
    protected void onResume() {
        mAudioController.addAudioPlayer(mAudioPlayer);
        AudioServiceController.getInstance().bindAudioService(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        mAudioController.removeAudioPlayer(mAudioPlayer);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(messageReceiver);
        super.onDestroy();
    }

    /** Create menu from XML
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
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
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt("tab", mActionBar.getSelectedNavigationIndex());
    }

    /**
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Intent to start new Activity
        Intent intent;

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.ml_menu_sortby_name:
            case R.id.ml_menu_sortby_length:
                Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);
                if (current == null)
                    break;
                if (current instanceof ISortable)
                    ((ISortable) current).sortBy(item.getItemId() == R.id.ml_menu_sortby_name
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
                MediaLibrary.getInstance(this).loadMediaItems(this);
                break;
            case R.id.ml_menu_open_mrl:
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                final EditText input = new EditText(this);
                b.setTitle(R.string.open_mrl_dialog_title);
                b.setMessage(R.string.open_mrl_dialog_msg);
                b.setView(input);
                b.setPositiveButton("Open", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int button) {
                        ProgressDialog pd = ProgressDialog.show(
                                MainActivity.this,
                                getApplicationContext().getString(R.string.loading),
                                "Please wait...", true);
                        pd.setCancelable(true);

                        VLCCallbackTask t = new VLCCallbackTask(
                            /* Task to run */
                            new VLCCallbackTask.CallbackListener() {
                                @Override
                                public void callback() {
                                    AudioServiceController c = AudioServiceController.getInstance();
                                    String s = input.getText().toString();

                                    try {
                                        if(!LibVLC.getExistingInstance().hasVideoTrack(s)) {
                                            Log.d(TAG, "Auto-detected audio for " + s);
                                            ArrayList<String> media = new ArrayList<String>();
                                            media.add(input.getText().toString());
                                            c.append(media);
                                        } else {
                                            Log.d(TAG, "Auto-detected Video for " + s);
                                            Intent intent = new Intent(getApplicationContext(),
                                                                       VideoPlayerActivity.class);
                                            intent.putExtra("itemLocation", s);
                                            startActivity(intent);
                                        }
                                    } catch(IOException e) {
                                        /* VLC is unable to open the MRL */
                                        return;
                                    }
                                }

                                @Override
                                public void callback_object(Object o) {
                                    ProgressDialog pd = (ProgressDialog)o;
                                    pd.dismiss();
                                }
                            }, pd);

                        /* Start this in a new friend as to not block the UI thread */
                        new Thread(t).start();
                    }
                }
                );
                b.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        return;
                        }});
                b.show();
                break;
            case R.id.ml_menu_search:
            	onSearchRequested();
            	break;
        }
        return super.onOptionsItemSelected(item);
    }

    /*@Override
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
    }*/

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
        if (mActionBar.getSelectedNavigationIndex() == VIDEO_TAB) {
            showAudioTab();
        } else {
            showVideoTab();
        }
    }

    private void showVideoTab() {
        mActionBar.setSelectedNavigationItem(VIDEO_TAB);
    }

    private void showAudioTab() {
        mActionBar.setSelectedNavigationItem(AUDIO_TAB);
    }

    /**
     * onClick event from xml
     * @param view
     */
    public void searchClick(View view) {
        onSearchRequested();
    }

    private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(ACTION_SHOW_PROGRESSBAR)) {
                setProgressBarIndeterminateVisibility(Boolean.TRUE);
            }
            else if (action.equalsIgnoreCase(ACTION_HIDE_PROGRESSBAR)) {
                setProgressBarIndeterminateVisibility(Boolean.FALSE);
            }
            else if (action.equalsIgnoreCase(ACTION_SHOW_TEXTINFO)) {
                String info = intent.getStringExtra("info");
                int max = intent.getIntExtra("max", 0);
                int progress = intent.getIntExtra("progress", 100);
                mInfoText.setText(info);
                mInfoProgress.setMax(max);
                mInfoProgress.setProgress(progress);
                mInfoLayout.setVisibility(info != null ? View.VISIBLE : View.GONE);
            }
        }
    };

    public static void showProgressBar(Context context) {
        Intent intent = new Intent();
        intent.setAction(ACTION_SHOW_PROGRESSBAR);
        context.getApplicationContext().sendBroadcast(intent);
    }

    public static void hideProgressBar(Context context) {
        Intent intent = new Intent();
        intent.setAction(ACTION_HIDE_PROGRESSBAR);
        context.getApplicationContext().sendBroadcast(intent);
    }


    public static void clearTextInfo(Context context) {
        sendTextInfo(context, null, 0, 100);
    }

    public static void sendTextInfo(Context context, String info, int progress, int max) {
        Intent intent = new Intent();
        intent.setAction(ACTION_SHOW_TEXTINFO);
        intent.putExtra("info", info);
        intent.putExtra("progress", progress);
        intent.putExtra("max", max);
        context.getApplicationContext().sendBroadcast(intent);
    }

    
    
    
    
    
    
    public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private final SherlockFragmentActivity mActivity;
        private final String mTag;
        private final Class<T> mClass;
        private final Bundle mArgs;
        private Fragment mFragment;

        public TabListener(SherlockFragmentActivity activity, String tag, Class<T> clz) {
            this(activity, tag, clz, null);
        }

        public TabListener(SherlockFragmentActivity activity, String tag, Class<T> clz, Bundle args) {
            mActivity = activity;
            mTag = tag;
            mClass = clz;
            mArgs = args;

            // Check to see if we already have a fragment for this tab, probably
            // from a previously saved state.  If so, deactivate it, because our
            // initial state is that a tab isn't shown.
            mFragment = mActivity.getSupportFragmentManager().findFragmentByTag(mTag);
            if (mFragment != null && !mFragment.isDetached()) {
                FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
                ft.detach(mFragment);
                ft.commit();
            }
        }

        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            if (mTag.equalsIgnoreCase("video"))
                ft.setCustomAnimations(R.anim.anim_enter_left, R.anim.anim_leave_left);
            else if (mTag.equalsIgnoreCase("audio"))
                ft.setCustomAnimations(R.anim.anim_enter_right, R.anim.anim_leave_right);
            
            if (mFragment == null) {
                mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
                ft.add(R.id.fragment_placeholder, mFragment, mTag);
            } else {
                ft.attach(mFragment);
            }
        }

        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            if (mTag.equalsIgnoreCase("video"))
                ft.setCustomAnimations(R.anim.anim_enter_left, R.anim.anim_leave_left);
            else if (mTag.equalsIgnoreCase("audio"))
                ft.setCustomAnimations(R.anim.anim_enter_right, R.anim.anim_leave_right);
            
            if (mFragment != null) {
                ft.detach(mFragment);
            }
        }

        public void onTabReselected(Tab tab, FragmentTransaction ft) {
            
        }
    }
}
