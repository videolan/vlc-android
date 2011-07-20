package org.videolan.vlc.android;

import java.util.Comparator;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class SearchResultAdapter extends ArrayAdapter<Media> 
									implements Comparator<Media>  {

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

		Media item = getItem(position);
		TextView textView = (TextView)v.findViewById(android.R.id.text1);
		textView.setText(item.getTitle());
		
		return v;
	}

	@Override
	public int compare(Media object1, Media object2) {
		return object1.getTitle().compareToIgnoreCase(object2.getTitle());
	}

	public void sort() {
		super.sort(this);
	}
	
	

}
