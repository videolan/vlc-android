package org.videolan.vlc.android;


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
	

	@Override   
	protected void onCreate(Bundle savedInstanceState) {	
		setContentView(R.layout.main);	
		super.onCreate(savedInstanceState); 

		/* Initialize variables */
		mInstance = this;	
		mProgressBar = (ProgressBar)findViewById(R.id.ml_progress_bar);

        /* Initialize the TabView */
        mTabHost = getTabHost();
        mTabHost.addTab(mTabHost.newTabSpec("VIDEO TAB").setIndicator("VIDEO TAB")
        		.setContent(new Intent(this, VideoActivityGroup.class)));
       
        
        mTabHost.addTab(mTabHost.newTabSpec("AUDIO TAB").setIndicator("AUDIO TAB")
        		.setContent(new Intent(this, AudioActivityGroup.class)));
	
        // Start audio player when audio is playing
        if (getIntent().hasExtra(START_FROM_NOTIFICATION)) {
        	mCurrentState = AUDIO_TAB;
        }
        
        // restore the last used tab
        mTabHost.setCurrentTab(mCurrentState);
        
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
						VideoListAdapter.SORT_BY_NAME);
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

	/**
	 * onClick event from xml
	 * @param view
	 */
	public void changeTabClick(View view) {
		
		// TODO: change the icon
		if (mCurrentState == VIDEO_TAB) {
			mCurrentState = AUDIO_TAB;
		} else {
			mCurrentState = VIDEO_TAB;
		}
		
		mTabHost.setCurrentTab(mCurrentState);
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
	protected static MainActivity getInstance() {
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
