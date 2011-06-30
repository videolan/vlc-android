package org.videolan.vlc.android;

import java.util.Comparator;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class SearchResultAdapter extends ArrayAdapter<MediaItem> 
									implements Comparator<MediaItem>  {

	public SearchResultAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
	}
			
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;
		if (v == null){
			LayoutInflater inflater = (LayoutInflater) 
					getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = inflater.inflate(android.R.layout.simple_list_item_1, 
					parent, false);
		}

		MediaItem item = getItem(position);
		TextView textView = (TextView)v.findViewById(android.R.id.text1);
		textView.setText(item.getName());
		
		return v;
	}

	@Override
	public int compare(MediaItem object1, MediaItem object2) {
		return object1.getName().compareToIgnoreCase(object2.getName());
	}

	public void sort() {
		super.sort(this);
	}
	
	

}
