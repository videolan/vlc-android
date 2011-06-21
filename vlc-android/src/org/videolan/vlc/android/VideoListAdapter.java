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

	public int compare(MediaItem item1, MediaItem item2) {
		return item1.getName().toUpperCase().compareTo(
				item2.getName().toUpperCase());
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
		ImageView thumbnail = (ImageView)v.findViewById(R.id.ml_item_thumbnail);
		TextView title = (TextView)v.findViewById(R.id.ml_item_title);
		TextView length = (TextView)v.findViewById(R.id.ml_item_length);
		title.setText(item.getName());
		length.setText(Util.millisToString(item.getLength()));
		
		if (item.getThumbnail() != null)
			thumbnail.setImageBitmap(item.getThumbnail());
		else {
			// set default thumbnail
			Bitmap defaultThumbnail = BitmapFactory.decodeResource(
					MediaLibraryActivity.getInstance().getResources(), 
					R.drawable.thumbnail);
			thumbnail.setImageBitmap(defaultThumbnail);
		}


		return v;
	}

	public void sort() {
		super.sort(this);		
	}
	
}

