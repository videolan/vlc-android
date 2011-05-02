package vlc.android;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.DropBoxManager;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MediaLibraryActivity extends Activity {
	
	/**
	 * TODO: 
	 * + fill List with media files
	 * + onClick events for header buttons
	 * + search functionality
	 */	
	public final static String TAG = "VLC/MediaLibraryActivity";
	
	private DatabaseManager mDBManager;
	
	private final Handler mHandler = new Handler();
	private final CyclicBarrier mBarrier = new CyclicBarrier(2);
	
	private MediaItem mItemToAdd = null;
	private MediaLibraryAdapter mAdapter;
	private List<MediaItem> mItems = new ArrayList<MediaItem>();
	private ListView mMediaList;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.media_library);	
		super.onCreate(savedInstanceState); 
		
		/* Initialize variables */
		mAdapter = new MediaLibraryAdapter(this, R.layout.media_library_item);
		mMediaList = (ListView)findViewById(R.id.ml_list);
		mMediaList.setAdapter(mAdapter);
		mDBManager = new DatabaseManager(this);
		
		
		// TODO: Add directories by preferencesAcitvity
		/** Debug */
		mDBManager.addMediaDir("/sdcard/media/video");
        
        /* Load all media files on storage */
        new Thread(new Runnable() {
        	
        	private Stack<File> directorys = new Stack<File>();

    		public void run() {
    			// show progressbar in header
    			mHandler.post(mShowProgressBar);
    			directorys.addAll(mDBManager.getMediaDirs());
    	    	MediaItemFilter mediaFileFilter = new MediaItemFilter();
    	    	
    	    	while (!directorys.isEmpty()) {
    	    		File dir = directorys.pop();
    	    		File[] f = null;
    	    		if ((f = dir.listFiles(mediaFileFilter)) != null) {
    			    	  	
    			    	for (int i = 0; i < f.length; i++) {
    			    		if (f[i].isFile()) {
    			    			mItemToAdd = new MediaItem(f[i]);		
    			    			mHandler.post(mAddMediaItem);	
    			    			try {
									mBarrier.await();
								} catch (InterruptedException e) {
									e.printStackTrace();
								} catch (BrokenBarrierException e) {
									e.printStackTrace();
								}
    			    		} else if (f[i].isDirectory()) {
    			    			directorys.push(f[i]);
    			    		}
    			    	}   
    		    	}
    	    	}
    	    	// hide progressbar in header
    	    	mHandler.post(mHideProgressBar);
    			
    		}
        }).start();
	
	}

	/** Create menu from XML 
	 * TODO: Add images 
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.media_library, menu);
		return super.onCreateOptionsMenu(menu);
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
		// About
		case R.id.ml_menu_about:
			intent = new Intent(MediaLibraryActivity.this, 
					AboutActivity.class);
			MediaLibraryActivity.this.startActivity(intent);
			break;
		// Preferences
		case R.id.ml_menu_preferences:
			intent = new Intent(MediaLibraryActivity.this, 
					PreferencesActivity.class);
			MediaLibraryActivity.this.startActivity(intent);
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	/**
	 * onClick event from xml
	 * @param view
	 */
	public void changeView(View view) {
		// TODO: implement!! ;)
		Toast.makeText(this, "not implemented", Toast.LENGTH_LONG).show();
	}
	
	/**
	 * onClick event from xml
	 * @param view
	 */
	public void search(View view) {
		// TODO: implement!! ;)
		Toast.makeText(this, "not implemented", Toast.LENGTH_LONG).show();
	}
	
	/**
	 * hide progress bar
	 */
	protected final Runnable mHideProgressBar = new Runnable() {
		
		public void run() {
			ProgressBar pb = (ProgressBar)findViewById(R.id.ml_progress_bar);
			pb.setVisibility(View.INVISIBLE);			
		}
	};
	
	/**
	 * show progress bar
	 */
	protected final Runnable mShowProgressBar = new Runnable() {

		public void run() {
			ProgressBar pb = (ProgressBar)findViewById(R.id.ml_progress_bar);
			pb.setVisibility(View.VISIBLE);			
		}
	};	

	/**
	 * add items to list
	 */
	private final Runnable mAddMediaItem = new Runnable() {
		
		public void run() {
				mItems.add(mItemToAdd);
				Collections.sort(mItems);
				int index = mItems.indexOf(mItemToAdd);
				mAdapter.insert(mItemToAdd, index);
				mItemToAdd = null;
				try {
					mBarrier.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (BrokenBarrierException e) {
					e.printStackTrace();
				}
		}
	};
	
	/** 
	 * Filters all irrelevant files 
	 */
    private class MediaItemFilter implements FileFilter {
    	
    	// FIXME: save extensions in external database
    	private String[] extensions = Constants.EXTENTIONS;

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
