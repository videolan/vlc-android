package vlc.android;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MediaLibraryActivity extends ListActivity {
	public final static String TAG = "VLC/MediaLibraryActivity";
	/**
	 * TODO: 
	 * + change
	 * + onClick events for header buttons
	 * + search functionality
	 * + change to ListActivity
	 */	
	private static MediaLibraryActivity mInstance;
	
	private DatabaseManager mDBManager;
	
	protected final Handler mHandler = new Handler();
	protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
	protected MediaItem mItemToAdd = null;
	
	private MediaLibraryAdapter mAdapter;
	private ProgressBar mProgressBar;
	protected ThumbnailerManager mThumbnailerManager;
	
	@Override   
	protected void onCreate(Bundle savedInstanceState) {	
		setContentView(R.layout.media_library);	
		super.onCreate(savedInstanceState); 

		/* Initialize variables */
		mInstance = this;
		mAdapter = new MediaLibraryAdapter(this, R.layout.media_library_item);	
		mDBManager = DatabaseManager.getInstance();
		mProgressBar = (ProgressBar)findViewById(R.id.ml_progress_bar);
		mThumbnailerManager = new ThumbnailerManager();

		setListAdapter(mAdapter);

		/** Debug */
//		mDBManager.addMediaDir("/sdcard/media");
//		mDBManager.mediaDirExists("/sdcard/media");
//		mDBManager.removeMediaDir("/sdcard/media/video");
//		mDBManager.mediaDirExists("/sdcard/media/video");
        
        updateMediaList();
	
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
	

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		MediaItem item = mAdapter.getItem(position);
		Intent intent = new Intent(this, PlayerActivity.class);
		intent.putExtra("filePath", item.getPath());
		startActivity(intent);
		super.onListItemClick(l, v, position, id);
	}

	/**
	 * Get instance e.g. for Context or Handler
	 * @return this ;)
	 */
	protected static MediaLibraryActivity getInstance() {
		return mInstance;	
	}
	

	/**
	 * hide progress bar
	 */
	protected final Runnable mHideProgressBar = new Runnable() {	
		public void run() {
			mProgressBar.setVisibility(View.INVISIBLE);			
		}
	};
	
	/**
	 * show progress bar
	 */
	protected final Runnable mShowProgressBar = new Runnable() {
		public void run() {
			mProgressBar.setVisibility(View.VISIBLE);			
		}
	};	
	

	/**
	 * add items to list
	 */
	protected final Runnable mAddMediaItem = new Runnable() {		
		public void run() {
			mAdapter.insert(mItemToAdd);
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
	 * 
	 */
	protected void updateMediaList() {
		mAdapter.clear();
		new Thread(mUpdateMediaList).start();
	}
	
	
	private final Runnable mUpdateMediaList = new Runnable() {
    	
    	private Stack<File> directorys = new Stack<File>();

		public void run() {
			// show progressbar in header
			mHandler.post(mShowProgressBar);	
			
			// get directories from database
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
