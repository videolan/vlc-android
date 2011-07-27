package org.videolan.vlc.android;


import org.videolan.vlc.android.widget.AudioMiniPlayer;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TabHost;

public class MainActivity extends TabActivity {
	public final static String TAG = "VLC/MainActivity";

	protected static final int HIDE_PROGRESSBAR = 0;
	protected static final int SHOW_PROGRESSBAR = 1;
	private static final int VIDEO_TAB = 0;
	private static final int AUDIO_TAB = 1;
	public static final String START_FROM_NOTIFICATION = "from_notification";
	
	private VideoListActivity mVideoListActivity;
	
	private static MainActivity mInstance;	
	private ProgressBar mProgressBar;
	private TabHost mTabHost;
	private int mCurrentState = 0;
	private ImageButton mChangeTab;
	private AudioMiniPlayer mAudioPlayer;
	private AudioServiceController mAudioController;
	

	@Override   
	protected void onCreate(Bundle savedInstanceState) {	
		setContentView(R.layout.main);	
		super.onCreate(savedInstanceState); 

		/* Initialize variables */
		mInstance = this;	
		mProgressBar = (ProgressBar)findViewById(R.id.ml_progress_bar);
		mChangeTab = (ImageButton) findViewById(R.id.change_tab);

        /* Initialize the TabView */
        mTabHost = getTabHost();
        mTabHost.addTab(mTabHost.newTabSpec("VIDEO TAB").setIndicator("VIDEO TAB")
        		.setContent(new Intent(this, VideoActivityGroup.class)));
        
        mTabHost.addTab(mTabHost.newTabSpec("AUDIO TAB").setIndicator("AUDIO TAB")
        		.setContent(new Intent(this, AudioActivityGroup.class)));
        
        // Get video list instance to sort the list.
        mVideoListActivity = VideoListActivity.getInstance();
        
        // add mini audio player
        mAudioPlayer = (AudioMiniPlayer) findViewById(R.id.audio_mini_player);
		mAudioController = AudioServiceController.getInstance();
		mAudioController.addAudioPlayer(mAudioPlayer);
		mAudioPlayer.setAudioPlayerControl(mAudioController);
		
		
		
        // Start audio player when audio is playing
        if (getIntent().hasExtra(START_FROM_NOTIFICATION)) {
        	Log.d(TAG, "Started from notification.");
        	showAudioTab();
        } else {
        	// TODO: load the last tab-state
        	showVideoTab();
        }
        
        /* Load media items from database and storage */
        MediaLibrary.getInstance().loadMediaItems();
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
	
	@Override
	public void onBackPressed() {
		Log.e(TAG, TAG + " onBackPressed()");
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
			} 
			break;
			// Sort by length
		case R.id.ml_menu_sortby_length:
			if (mCurrentState == VIDEO_TAB) {
				mVideoListActivity.sortBy(
						VideoListAdapter.SORT_BY_LENGTH);
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
			}
		};
	};
    
}
