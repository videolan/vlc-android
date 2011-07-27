package org.videolan.vlc.android;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class AudioSongsListAdapter extends ArrayAdapter<Media> {

	private ArrayList<Media> mMediaList;
	
	public AudioSongsListAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
		mMediaList = new ArrayList<Media>();
	}
	
	@Override
	public void add(Media m) {
		mMediaList.add(m);
		super.add(m);
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;
		if (v == null) {
			LayoutInflater inflater = (LayoutInflater) 
					getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = inflater.inflate(android.R.layout.simple_list_item_1, 
					parent, false);
		}
		
		TextView text = (TextView)v.findViewById(android.R.id.text1);
		text.setText(getItem(position).getTitle());
		return v;
	}

	public List<String> getPaths() {
		List<String> paths = new ArrayList<String>();
		for (int i = 0; i < mMediaList.size(); i++)	{
			paths.add(mMediaList.get(i).getPath());
		}
		return paths;
	}

}
