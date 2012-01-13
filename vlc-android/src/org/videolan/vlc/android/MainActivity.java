package org.videolan.vlc.android;

import org.videolan.vlc.android.widget.AudioMiniPlayer;

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

    private VideoListActivity mVideoListActivity = null;
    private AudioBrowserActivity mAudioBrowserActivity = null;

    private static MainActivity mInstance;
    private ProgressBar mProgressBar;
    private TabHost mTabHost;
    private int mCurrentState = 0;
    private ImageButton mChangeTab;
    private AudioMiniPlayer mAudioPlayer;
    private AudioServiceController mAudioController;
    private TextView mTextInfo;

    private SharedPreferences mSettings;

    private int mVersionNumber = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.main);
        super.onCreate(savedInstanceState);

        /* Get settings */
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        /* Initialize variables */
        mInstance = this;
        mProgressBar = (ProgressBar) findViewById(R.id.ml_progress_bar);
        mChangeTab = (ImageButton) findViewById(R.id.change_tab);
        mTextInfo = (TextView) findViewById(R.id.text_info);

        /* Initialize the TabView */
        mTabHost = getTabHost();
        mTabHost.addTab(mTabHost.newTabSpec("VIDEO TAB").setIndicator("VIDEO TAB")
                .setContent(new Intent(this, VideoActivityGroup.class)));

        mTabHost.addTab(mTabHost.newTabSpec("AUDIO TAB").setIndicator("AUDIO TAB")
                .setContent(new Intent(this, AudioActivityGroup.class)));

        // Get video & audio list instances to sort the list.
        mVideoListActivity = VideoListActivity.getInstance();
        mAudioBrowserActivity = AudioBrowserActivity.getInstance();

        // add mini audio player
        mAudioPlayer = (AudioMiniPlayer) findViewById(R.id.audio_mini_player);
        mAudioController = AudioServiceController.getInstance();
        mAudioController.addAudioPlayer(mAudioPlayer);
        mAudioPlayer.setAudioPlayerControl(mAudioController);
        mAudioPlayer.update();

        // Start audio player when audio is playing
        if (getIntent().hasExtra(START_FROM_NOTIFICATION)) {
            Log.d(TAG, "Started from notification.");
            showAudioTab();
        } else {
            // TODO: load the last tab-state
            showVideoTab();
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
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Intent to start new Activity
        Intent intent;

        // Handle item selection
        switch (item.getItemId()) {
            // Sort by name
            case R.id.ml_menu_sortby_name:
                if (mCurrentState == VIDEO_TAB) {
                    mVideoListActivity.sortBy(
                            VideoListAdapter.SORT_BY_TITLE);
                } else if(mCurrentState == AUDIO_TAB) {
                    mAudioBrowserActivity.sortBy(
                            AudioBrowserActivity.SORT_BY_TITLE);
                }
                break;
            // Sort by length
            case R.id.ml_menu_sortby_length:
                if (mCurrentState == VIDEO_TAB) {
                    mVideoListActivity.sortBy(
                            VideoListAdapter.SORT_BY_LENGTH);
                } else if(mCurrentState == AUDIO_TAB) {
                    mAudioBrowserActivity.sortBy(
                            AudioBrowserActivity.SORT_BY_LENGTH);
                }
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
            case R.id.show_player:
                // TODO: start audio player activity
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
        if(mAudioBrowserActivity == null)
            mAudioBrowserActivity = AudioBrowserActivity.getInstance();
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
                mTextInfo.setText(info);
                mTextInfo.setVisibility(info != null ? View.VISIBLE : View.GONE);
                break;
        }
    };
    };

    public static void sendTextInfo(Handler handler, String info) {
        Message m = handler.obtainMessage(MainActivity.SHOW_TEXTINFO);
        Bundle b = m.getData();
        b.putString("info", info);
        handler.sendMessage(m);
    }
}
