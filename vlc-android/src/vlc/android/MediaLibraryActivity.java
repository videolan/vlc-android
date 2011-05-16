package vlc.android;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MediaLibraryActivity extends ListActivity {
	public final static String TAG = "VLC/MediaLibraryActivity";
	/**
	 * TODO: 
	 * + onClick events for header buttons
	 * + search functionality
	 */	
	private static MediaLibraryActivity mInstance;
	
	private DatabaseManager mDBManager;
	
	protected final Handler mHandler = new Handler();
	private final CyclicBarrier mBarrierList = new CyclicBarrier(2);
	protected final CyclicBarrier mBarrierItem = new CyclicBarrier(2);
	private List<MediaItem> mItemList = new ArrayList<MediaItem>();
	protected MediaItem mItemToUpdate;
	
	private MediaLibraryAdapter mAdapter;
	private ProgressBar mProgressBar;
	
	private LinearLayout mNoFileLayout;
	private LinearLayout mLoadFileLayout;
	
	protected ThumbnailerManager mThumbnailerManager;
	
	
	@Override   
	protected void onCreate(Bundle savedInstanceState) {	
		setContentView(R.layout.media_library);	
		super.onCreate(savedInstanceState); 

		/* Initialize variables */
		mInstance = this;	
		mDBManager = DatabaseManager.getInstance();
		mProgressBar = (ProgressBar)findViewById(R.id.ml_progress_bar);
		mThumbnailerManager = new ThumbnailerManager();
		mAdapter = new MediaLibraryAdapter(MediaLibraryActivity.this, 
				R.layout.browser_item);
		
		mNoFileLayout = (LinearLayout)findViewById(R.id.ml_empty_nofile);
		mLoadFileLayout = (LinearLayout)findViewById(R.id.ml_empty_loadfile);

		setListAdapter(mAdapter);

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
	protected final Runnable mUpdateMediaItem = new Runnable() {
		public void run() {
			mAdapter.update(mItemToUpdate);
			try {
				mBarrierItem.await();
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
		mLoadFileLayout.setVisibility(View.VISIBLE);
		mNoFileLayout.setVisibility(View.INVISIBLE);
		new Thread(mGetMediaList).start();
	}
	
	protected final Runnable mUpdateMediaList = new Runnable() {
		public void run() {
			mAdapter.clear();
			if (mItemList.size() > 0) {
				for (MediaItem item : mItemList) {
					mAdapter.add(item);
					if (item.getThumbnail() == null)
						mThumbnailerManager.addJob(item);
				}	
				mAdapter.sort();
			} else {
				mLoadFileLayout.setVisibility(View.INVISIBLE);
				mNoFileLayout.setVisibility(View.VISIBLE);
			}
			try {
				mBarrierList.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (BrokenBarrierException e) {
				e.printStackTrace();
			}
		}
	};
	
	
	private final Runnable mGetMediaList = new Runnable() {
    	
    	private Stack<File> directorys = new Stack<File>();

		public void run() {
			// show progressbar in header
			mHandler.post(mShowProgressBar);	
			
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
	    	
	    	/** DEBUG */
	    	try {
				Thread.sleep(2000);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
	    	
	    	// update the listView
	    	mHandler.post(mUpdateMediaList);
			try {
				mBarrierList.await();
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
	    	mHandler.post(mHideProgressBar);
			
		}
    };
	
	
	
	/** 
	 * Filters all irrelevant files 
	 */
    private class MediaItemFilter implements FileFilter {
    	
    	// FIXME: save extensions in external database
    	private String[] extensions = Constant.EXTENTIONS; 
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
