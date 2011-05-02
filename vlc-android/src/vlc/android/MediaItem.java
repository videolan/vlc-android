package vlc.android;

import java.io.File;

import android.graphics.Bitmap;

public class MediaItem implements Comparable<MediaItem> {
	
	public final static String TAG = "VLC/MediaItem";
	
	private Bitmap thumbnail;
	private File file;
	
	public MediaItem(File file) {
		this.file = file;
//		mThumbnailerManager.addJob(this);
//		thumbnail = BitmapFactory.decodeResource(getResources(), 
//				R.drawable.thumbnail);
	}

	public String getName() {
		return file.getName().substring(0, file.getName().lastIndexOf('.'));
	}
	
	public String getLenght() {
		return "0:21:45";
	}
	
	public String getFormat() {
		return "608x336";
	}
	
	public Bitmap getThumbnail() {
		 return thumbnail;
	}
	
	public void setThumbnail(Bitmap t) {
		// TODO: save thumbnail on storage
		thumbnail = t;
	}
	
	public String getPath() {
		return file.getPath();
	}

	/**
	 * Compare the filenames to sort items
	 */
	public int compareTo(MediaItem another) {
		return getName().toUpperCase().compareTo(
				another.getName().toUpperCase());
	}

}
