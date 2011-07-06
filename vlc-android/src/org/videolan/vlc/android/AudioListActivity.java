package org.videolan.vlc.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
/**
 * 
 * 
 * 
 *
 */
public class AudioListActivity extends ListActivity {
	public final static String TAG = "VLC/AudioListActivity";
	
	private SimpleAdapter mAudioAdapter;
	ArrayList<HashMap<String,String>> list = new ArrayList<HashMap<String,String>>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mAudioAdapter = new SimpleAdapter(this, list, android.R.layout.simple_list_item_2, 
				new String[] {"text1", "text2"}, new int[] {android.R.id.text1, android.R.id.text2});
		
		updateList();
		setListAdapter(mAudioAdapter);
	}
	


	private void updateList() {
		
		List<MediaItem> itemList = MediaLibrary.getInstance().getAudioItems();

		for (MediaItem item : itemList) {
			HashMap<String,String> listItem = new HashMap<String,String>();
			listItem.put( "text1", item.getName());
			listItem.put( "text2", item.getPath());
			list.add( listItem );
		}	

		mAudioAdapter.notifyDataSetChanged();

	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		TextView tv2 =(TextView) v.findViewById(android.R.id.text2);
		Intent intent = new Intent(this, VideoPlayerActivity.class);
		intent.putExtra("filePath", tv2.getText());
		startActivity(intent);
		super.onListItemClick(l, v, position, id);
	}
	
	
}
