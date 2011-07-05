package org.videolan.vlc.android;


import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TabHost;

public class MediaLibraryActivity extends TabActivity {
	public final static String TAG = "VLC/MediaLibraryActivity";

	protected static final int HIDE_PROGRESSBAR = 0;
	protected static final int SHOW_PROGRESSBAR = 1;
	private static final int VIDEO_TAB = 0;
	private static final int AUDIO_TAB = 1;
	
	private VideoListActivity mVideoListActivity;

	
	private static MediaLibraryActivity mInstance;	
	private DatabaseManager mDBManager;	
	private final CyclicBarrier mBarrier = new CyclicBarrier(2);
	protected ArrayList<MediaItem> mItemList = new ArrayList<MediaItem>();
	private ProgressBar mProgressBar;
	private TabHost mTabHost;
	private int mCurrentState = 0;
	

	@Override   
	protected void onCreate(Bundle savedInstanceState) {	
		setContentView(R.layout.media_library);	
		super.onCreate(savedInstanceState); 

		/* Initialize variables */
		mInstance = this;	
		mDBManager = DatabaseManager.getInstance();
		mProgressBar = (ProgressBar)findViewById(R.id.ml_progress_bar);

        /* Initialize the TabView */
        mTabHost = getTabHost();
        mTabHost.addTab(mTabHost.newTabSpec("VIDEO TAB").setIndicator("VIDEO TAB")
        		.setContent(new Intent(this, VideoListActivity.class)));
        mVideoListActivity = VideoListActivity.getInstance();
        
        
        // TODO: implement the audio view
        mTabHost.addTab(mTabHost.newTabSpec("AUDIO TAB").setIndicator("AUDIO TAB")
        		.setContent(R.id.ml_audio_todo));
        
        // restore the last used tab
        mTabHost.setCurrentTab(mCurrentState);
        
        /* Load media items from database and storage */
		loadMediaItems();
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
	protected static MediaLibraryActivity getInstance() {
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

	public void loadMediaItems() {
		new Thread(mGetMediaItems).start();
	}
	
	private final Runnable mGetMediaItems = new Runnable() {
    	
    	private Stack<File> directorys = new Stack<File>();

		public void run() {
			// show progressbar in header
			mHandler.sendEmptyMessage(SHOW_PROGRESSBAR);	
			
			// get directories from database
			directorys.addAll(mDBManager.getMediaDirs());
			
			// get all paths of the existing media items
			List<File> existingFiles = mDBManager.getMediaItemPaths();
			
			// list of all added files
			List<File> addedFiles = new ArrayList<File>();
			
			// clear all old item
			mItemList.clear();
			
	    	MediaItemFilter mediaFileFilter = new MediaItemFilter();
	    	
	    	
	    	while (!directorys.isEmpty()) {
	    		File dir = directorys.pop();
	    		File[] f = null;
	    		if ((f = dir.listFiles(mediaFileFilter)) != null) {		    	  	
			    	for (int i = 0; i < f.length; i++) {
			    		if (f[i].isFile()) {
			    			if (existingFiles.contains(f[i])) {
			    				
			    				/** only add file if it is not already in the 
			    				 * list. eg. if user select an subfolder as well
			    				 */
			    				if (!addedFiles.contains(f[i])) {
				    				// get existing media item from database
				    				mItemList.add(mDBManager.getMediaItem(
				    						f[i].getPath()));	
				    				addedFiles.add( f[i] );
			    				}
			    			} else {
			    				// create new media item
			    				mItemList.add( new MediaItem( f[i] ));
			    			}
			    		} else if (f[i].isDirectory()) {
			    			directorys.push(f[i]);
			    		}
			    	}   
		    	}
	    	}
	    	
	    	
	    	// update the video and audio activities
	    	mVideoListActivity.mHandler.sendEmptyMessage(
	    			VideoListActivity.UPDATE_LIST);
			try {
				mBarrier.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (BrokenBarrierException e) {
				e.printStackTrace();
			}
	    	
	    	// remove file from database 
	    	for (int i = 0; i < existingFiles.size(); i++) {
	    		if (!addedFiles.contains(existingFiles.get(i))) {
		    		mDBManager.removeMediaItem(
		    				existingFiles.get(i).getPath());
	    		}
	    	}
	    	// hide progressbar in header
	    	mHandler.sendEmptyMessage(HIDE_PROGRESSBAR);
			
		}
    };
	
	
	
	/** 
	 * Filters all irrelevant files 
	 */
    private class MediaItemFilter implements FileFilter {
    	
    	// FIXME: save extensions in external database
    	private String[] extensions = MediaItem.EXTENTIONS; 
		public boolean accept(File f) {
			boolean accepted = false;
			if (!f.isHidden()) {
				if (f.isDirectory()) {
					accepted = true;
				} else {
					String fileName = f.getName().toLowerCase();
					int dotIndex = fileName.lastIndexOf(".");
					if (dotIndex != -1) {
						String fileExt = fileName.substring(dotIndex);
						accepted = Arrays.asList(extensions).contains(fileExt);
					}
				}
			}
			return accepted;
		}   	
    }
    
    
}
