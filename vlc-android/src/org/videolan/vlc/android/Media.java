package org.videolan.vlc.android;


import java.io.File;

import android.graphics.Bitmap;
import android.util.Log;

public class Media implements Comparable<Media> {

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
	
	public final static int TYPE_ALL = -1;
	public final static int TYPE_VIDEO = 0;
	public final static int TYPE_AUDIO = 1;
	
	/** Meta data form libvlc_media */
	private String mTitle;
	private String mArtist;
	private String mGenre;
	private String mCopyright;
	private String mAlbum;
	private String mTrackNumber;
	private String mDescription;
	private String mRating;
	private String mDate;
	private String mSettings;
	private String mNowPlaying;
	private String mPublisher;
	private String mEncodedBy;
	private String mTrackID;

	private File mFile;
	private long mTime = 0;
	private long mLength = 0;
	private int mType;
	private int mWidth = 0;
	private int mHeight = 0;
	private Bitmap mPicture;


	/**
	 * Create an new Media
	 * @param file: path on the local storage
	 */
	public Media(File file) {
		this.mFile = file;

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
		db.addMedia(this);
	}

	
	public Media(File file, long time, long length, int type, Bitmap picture, 
			String title, String artist, String genre, String album) {
		
		mFile = file;
		mTime = time;
		mLength = length;
		mType = type;
		mPicture = picture;
		
		mTitle = title;
		mArtist = artist;
		mGenre = genre;
		mAlbum = album;
	}
	
	/**
	 * Compare the filenames to sort items
	 */
	public int compareTo(Media another) {
		return mTitle.toUpperCase().compareTo(
				another.getTitle().toUpperCase());
	}
	
	public File getFile() {
		return mFile;
	}
	
	public String getPath() {
		return mFile.getPath();
	}

	public void updateMeta() {
		
	}

	public String getFileName() {
		return mFile.getName().substring(0, mFile.getName().lastIndexOf('.'));
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

	public Bitmap getPicture() {
		 return mPicture;
	}

	public void setPicture(Bitmap p) {
		Log.d(TAG, "Set new picture for " + getTitle());
		DatabaseManager.getInstance().updateMedia(
				mFile.getPath(),
				DatabaseManager.mediaColumn.MEDIA_PICTURE,
				p);
		mPicture = p;
	}

	public String getTitle() {
		if (mTitle == null)
			return mFile.getName().substring(0, mFile.getName().lastIndexOf('.'));
		else
			return mTitle;
	}
	
	public String getArtist() {
		return mArtist;
	}

	public String getGenre() {
		return mGenre;
	}

	public String getCopyright() {
		return mCopyright;
	}

	public String getAlbum() {
		return mAlbum;
	}

	public String getTrackNumber() {
		return mTrackNumber;
	}

	public String getDescription() {
		return mDescription;
	}

	public String getRating() {
		return mRating;
	}

	public String getDate() {
		return mDate;
	}

	public String getSettings() {
		return mSettings;
	}

	public String getNowPlaying() {
		return mNowPlaying;
	}

	public String getPublisher() {
		return mPublisher;
	}

	public String getEncodedBy() {
		return mEncodedBy;
	}

	public String getTrackID() {
		return mTrackID;
	}

}
