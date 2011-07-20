package org.videolan.vlc.android;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import android.os.Handler;


public class MediaLibrary {
	public final static String TAG = "VLC/MediaLibrary";
	
	protected static final int MEDIA_ITEMS_UPDATED = 100;

	private static MediaLibrary mInstance;
	private DatabaseManager mDBManager;	
	private ArrayList<Media> mItemList;
	private final CyclicBarrier mBarrier = new CyclicBarrier(2);
	private ArrayList<Handler> mUpdateHandler;
	
	private MediaLibrary() {
		mInstance = this;
		mItemList = new ArrayList<Media>();
		mUpdateHandler = new ArrayList<Handler>();
		mDBManager = DatabaseManager.getInstance();	
	}
	
	public void loadMediaItems() {
		new Thread(mGetMediaItems).start();
	}
	

	public static MediaLibrary getInstance() {
		if (mInstance == null)
			mInstance = new MediaLibrary();
		return mInstance;
	}
	
	public void addUpdateHandler(Handler handler) {
		mUpdateHandler.add(handler);
	}

	public ArrayList<Media> getVideoItems() {
		ArrayList<Media> videoItems = new ArrayList<Media>();
		for (int i = 0; i < mItemList.size(); i++) {
			Media item = mItemList.get(i);
			if (item.getType() == Media.TYPE_VIDEO) {
				videoItems.add(item);
			}
		}
		return videoItems;
	}
	
	public ArrayList<Media> getAudioItems() {
		ArrayList<Media> videoItems = new ArrayList<Media>();
		for (int i = 0; i < mItemList.size(); i++) {
			Media item = mItemList.get(i);
			if (item.getType() == Media.TYPE_AUDIO) {
				videoItems.add(item);
			}
		}
		return videoItems;
	}
	
	public ArrayList<Media> getMediaItems() {
		return mItemList;
	}
	
	public Media getMediaItem(String path) {
		for (int i = 0; i < mItemList.size(); i++) {
			Media item = mItemList.get(i);
			if (item.getPath().equals(path)) {
				return item;
			}
		}
		return null;
	}
	
	
	private final Runnable mGetMediaItems = new Runnable() {
    	
    	private Stack<File> directorys = new Stack<File>();
    	private  MainActivity mMainActivity;
    	private  Handler mHandler;
    	

		public void run() {
			// Initialize variables
			mMainActivity = MainActivity.getInstance();
			mHandler = mMainActivity.mHandler;
			
			
			// show progressbar in header
			mHandler.sendEmptyMessage(MainActivity.SHOW_PROGRESSBAR);	
			
			// get directories from database
			directorys.addAll(mDBManager.getMediaDirs());
			
			// get all paths of the existing media items
			List<File> existingFiles = mDBManager.getMediaFiles();
			
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
				    				mItemList.add(mDBManager.getMedia(
				    						f[i].getPath()));	
				    				addedFiles.add( f[i] );
			    				}
			    			} else {
			    				// create new media item
			    				mItemList.add( new Media( f[i] ));
			    			}
			    		} else if (f[i].isDirectory()) {
			    			directorys.push(f[i]);
			    		}
			    	}   
		    	}
	    	}
	    	

	    	
	    	// update the video and audio activities
	    	for (int i = 0; i < mUpdateHandler.size(); i++) {
	    		Handler h = mUpdateHandler.get(i);
	    		h.sendEmptyMessage(MEDIA_ITEMS_UPDATED);
	    	}
	    	
	    	
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
		    		mDBManager.removeMedia(existingFiles.get(i).getPath());
	    		}
	    	}
	    	// hide progressbar in header
	    	mHandler.sendEmptyMessage(MainActivity.HIDE_PROGRESSBAR);
			
		}
    };

	
	/** 
	 * Filters all irrelevant files 
	 */
    private class MediaItemFilter implements FileFilter {
    	
    	// FIXME: save extensions in external database
    	private String[] extensions = Media.EXTENTIONS; 
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
