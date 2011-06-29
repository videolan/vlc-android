package org.videolan.vlc.android;


import java.io.File;

import android.graphics.Bitmap;
import android.util.Log;

public class MediaItem implements Comparable<MediaItem> {

	public final static String TAG = "VLC/MediaItem";

	public final static String[] EXTENTIONS = {
		 ".3g2", ".3gp", ".3gp2", ".3gpp", ".amv", ".asf", ".avi", ".bin", ".divx", ".dv", "f4v",
		 ".flv", ".gxf", ".iso", ".m1v", ".m2v", ".m2t", ".m2ts", ".m4v", ".mkv", ".mov", ".mp2",
		 ".mp2v", ".mp4", ".mp4v", ".mpa", ".mpe", ".mpeg", ".mpeg1", ".mpeg2", ".mpeg4", ".mpg",
		 ".mpv2", ".mts", ".mxf", ".nsv", ".nuv", ".ogg", ".ogm", ".ogv", ".ogx", ".ps", ".rec",
		 ".rm", ".rmvb", ".tod", ".ts", ".tts", ".vob", ".vro", ".webm", ".wmv",

		 ".a52", ".aac", ".ac3", ".adt", ".adts", ".aif", ".aifc", ".aiff", ".amr", ".aob", ".ape",
		 ".awb", ".cda", ".dts", ".flac", ".it", ".m4a", ".m4p", ".mid", ".mka", ".mlp", ".mod",
		 ".mp1", ".mp2", ".mp3", ".mpc", ".oga", ".ogg", ".oma", ".rmi", ".s3m", ".spx", ".tta",
		 ".voc", ".vqf", ".w64", ".wav", ".wma", ".wv", ".xa", ".xm"};

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
			mLength = mLibVlc.getLengthFromFile(file.getPath());
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
