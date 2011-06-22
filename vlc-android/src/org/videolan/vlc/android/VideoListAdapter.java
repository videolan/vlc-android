package org.videolan.vlc.android;


import java.util.Comparator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class VideoListAdapter extends ArrayAdapter<MediaItem> 
								 implements Comparator<MediaItem> {

	public final static int SORT_BY_NAME = 0;
	public final static int SORT_BY_LENGTH = 1;
	private int mSortBy = SORT_BY_NAME;
	
	
	public VideoListAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
	}

	public final static String TAG = "VLC/MediaLibraryAdapter";
	

	public synchronized void update(MediaItem item) {
		int position = getPosition(item);
		if (position != -1) {
			remove(item);
			insert(item, position);
		}
	}
	
	public void sortBy(int sortby) {
		switch (sortby) {
		case SORT_BY_NAME:
			mSortBy = SORT_BY_NAME;
			break;
		case SORT_BY_LENGTH:
			mSortBy = SORT_BY_LENGTH;
			break;
		default:
			mSortBy = SORT_BY_NAME;
			break;
		}
		sort();
	}

	public int compare(MediaItem item1, MediaItem item2) {
		int compare = 0;
		switch (mSortBy) {
		case SORT_BY_NAME:
			compare = item1.getName().toUpperCase().compareTo(
					item2.getName().toUpperCase());
			break;
		case SORT_BY_LENGTH:
			compare = ((Long)item1.getLength()).compareTo(item2.getLength());
			break;
		}
		return compare;
	}
	
	

	/**
     * Display the view of a file browser item.
     */
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		View v = convertView;
		if (v == null){
			LayoutInflater inflater = (LayoutInflater) 
					getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = inflater.inflate(R.layout.video_list_item, 
					parent, false);
		}

		MediaItem item = getItem(position);
		ImageView thumbnailView = (ImageView)v.findViewById(R.id.ml_item_thumbnail);
		TextView titleView = (TextView)v.findViewById(R.id.ml_item_title);
		TextView lengthView = (TextView)v.findViewById(R.id.ml_item_length);
		titleView.setText(item.getName());
		lengthView.setText(" " + Util.millisToString(item.getLength()) + " ");
		
		Bitmap thumbnail;
		if (item.getThumbnail() != null) {
			thumbnail = item.getThumbnail();
			thumbnailView.setImageBitmap(thumbnail);
		} else {
			// set default thumbnail
			thumbnail = BitmapFactory.decodeResource(
					MediaLibraryActivity.getInstance().getResources(), 
					R.drawable.thumbnail);
			thumbnailView.setImageBitmap(thumbnail);
		}


		return v;
	}

	public void sort() {
		super.sort(this);		
	}
	
}

