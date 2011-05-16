package vlc.android;

import java.io.File;

import android.graphics.Bitmap;

public class MediaItem implements Comparable<MediaItem> {
	
	public final static String TAG = "VLC/MediaItem";
	
	private String mName;
	private File mFile;
	private long mTime = 0;
	private long mLength = 0;
	private String mType;
	private int mWidth = 0;
	private int mHeight = 0;
	private Bitmap mThumbnail;
	
	/**
	 * Create an new MediaItem
	 * @param file: path on the local storage
	 */
	public MediaItem(File file) {
		this.mFile = file;
		mName = file.getName().substring(0, mFile.getName().lastIndexOf('.'));
		
		// TODO: get ALL info e.g. length,width...
		
		/** TODO: change from extension to something useful e.g. right 
		 * temporary thumbnail ;)
		 */
		mType = mFile.getName().substring(mFile.getName().lastIndexOf('.') + 1);
		
		// Add this item to database
		DatabaseManager db = DatabaseManager.getInstance();
		db.addMediaItem(this);
	}
	
	/**
	 * Create an existing item from database
	 */
	public MediaItem(String name, File file, long time, long length,
			String type, int width, int height, Bitmap thumbnail) {
		mName = name;
		mFile = file;
		mTime = time;
		mLength = length;
		mType = type;
		mWidth = width;
		mHeight = height;
		if (thumbnail == null) {
//			mThumbnail = BitmapFactory.decodeResource(
//					MediaLibraryActivity.getInstance().getResources(), 
//					R.drawable.thumbnail);
			MediaLibraryActivity.getInstance().mThumbnailerManager.addJob(this);			 
		} else {
			mThumbnail = thumbnail;
		}
		
	}

	public String getName() {
		return mName;
	}
	
	public void setName(String name) {
		mName = name;
	}
	
	public long getTime() {
		return mTime;
	}
	
	public void setTime(long time) {
		mTime = time;
	}
	
	public long getLength() {
		return mLength;
	}
	
	public String getType() {
		return mType;
	}
	
	public int getWidth() {
		return mWidth;
	}
	
	public int getHeight() {
		return mHeight;
	}
	
	public Bitmap getThumbnail() {
		 return mThumbnail;
	}
	
	public void setThumbnail(Bitmap t) {
		DatabaseManager.getInstance().updateMediaItem(
				mFile.getPath(), 
				DatabaseManager.mediaColumn.MEDIA_THUMBNAIL,
				t);
		mThumbnail = t;
	}
	
	public String getPath() {
		return mFile.getPath();
	}
	
	public File getFile() {
		return mFile;
	}
	

	/**
	 * Compare the filenames to sort items
	 */
	public int compareTo(MediaItem another) {
		return getName().toUpperCase().compareTo(
				another.getName().toUpperCase());
	}

}
