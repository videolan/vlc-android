package org.videolan.vlc.android;


import java.io.File;

import android.graphics.Bitmap;

public class MediaItem implements Comparable<MediaItem> {
	
	public final static String TAG = "VLC/MediaItem";
	
	public final static String[] EXTENTIONS = {".3gp", ".asf", ".wmv", ".au", 
		".avi", ".flv", ".mov", ".mp4", ".ogm", ".ogg", ".mkv", ".mka", ".ts",
		".mpg", ".mp3", ".mp2", ".nsc", ".nsv", ".nut", ".ra", ".ram", ".rm", 
		".rv" , ".rmbv", ".a52", ".dts", ".aac", ".flac", ".dv", ".vid", ".tta",
		".tac", ".ty", ".wav", ".dts", ".xa"};
	
	public final static int TYPE_VIDEO = 0;
	public final static int TYPE_AUDIO = 1;

	private String mName;
	private File mFile;
	private long mTime = 0;
	private long mLength = 0;
	private int mType;
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
		
    	LibVLC mLibVlc = null;
    	try {
			mLibVlc = LibVLC.getInstance();
			mType = (mLibVlc.hasVideoTrack(file.getPath())) ? TYPE_VIDEO : TYPE_AUDIO;
		} catch (LibVlcException e) {
			e.printStackTrace();
		} 

		// Add this item to database
		DatabaseManager db = DatabaseManager.getInstance();
		db.addMediaItem(this);
	}
	
	/**
	 * Create an existing item from database
	 */
	public MediaItem(String name, File file, long time, long length,
			int type, int width, int height, Bitmap thumbnail) {
		mName = name;
		mFile = file;
		mTime = time;
		mLength = length;
		mType = type;
		mWidth = width;
		mHeight = height;
		if (thumbnail != null) {
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
	
	public int getType() {
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
