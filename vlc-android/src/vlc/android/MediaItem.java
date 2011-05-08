package vlc.android;

import java.io.File;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class MediaItem implements Comparable<MediaItem> {
	
	public final static String TAG = "VLC/MediaItem";
	
	private Bitmap mThumbnail;
	private File mFile;
	
	public MediaItem(File file) {
		this.mFile = file;
		// TODO: get data/thumbnail from database;
		mThumbnail = BitmapFactory.decodeResource(
				MediaLibraryActivity.getInstance().getResources(), 
				R.drawable.thumbnail);
		MediaLibraryActivity.getInstance().mThumbnailerManager.addJob(this);
	}

	public String getName() {
		return mFile.getName().substring(0, mFile.getName().lastIndexOf('.'));
	}
	
	public String getLenght() {
		return "0:21:45";
	}
	
	public String getFormat() {
		return "608x336";
	}
	
	public Bitmap getThumbnail() {
		 return mThumbnail;
	}
	
	public void setThumbnail(Bitmap t) {
		// TODO: save thumbnail on storage
		mThumbnail = t;
	}
	
	public String getParentPath() {
		return mFile.getParentFile().getPath();
	}
	
	public String getPath() {
		return mFile.getPath();
	}
	
	public String getExtention() { 
		return mFile.getName().substring(mFile.getName().lastIndexOf('.') + 1);
	}

	/**
	 * Compare the filenames to sort items
	 */
	public int compareTo(MediaItem another) {
		return getName().toUpperCase().compareTo(
				another.getName().toUpperCase());
	}

}
