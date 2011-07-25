package org.videolan.vlc.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.videolan.vlc.android.widget.FlingViewGroup;
import org.videolan.vlc.android.widget.FlingViewGroup.ViewSwitchListener;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class AudioBrowserActivity extends Activity {
	public final static String TAG = "VLC/AudioBrowserActivity";
	
	private SimpleAdapter mAudioAdapter;
	private FlingViewGroup mFlingViewGroup;
	ArrayList<HashMap<String,String>> list = new ArrayList<HashMap<String,String>>();
	
	private HorizontalScrollView mHeader;
	private AudioServiceController mAudioController;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.audio_browser);
		
		mFlingViewGroup = (FlingViewGroup)findViewById(R.id.content);
		mFlingViewGroup.setOnViewSwitchedListener(mViewSwitchListener);
		
		mHeader =(HorizontalScrollView)findViewById(R.id.header);
		mAudioController = AudioServiceController.getInstance();

		mAudioAdapter = new SimpleAdapter(this, list, android.R.layout.simple_list_item_2, 
				new String[] {"text1", "text2"}, new int[] {android.R.id.text1, android.R.id.text2});
		
		ListView songsList = (ListView)findViewById(R.id.songs_list);
		songsList.setAdapter(mAudioAdapter);
		songsList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> av, View v, int p, long id) {
				TextView tv2 =(TextView) v.findViewById(android.R.id.text2);
				mAudioController.load(tv2.getText().toString());
			}
		});
		updateList();
	}
	
	private ViewSwitchListener mViewSwitchListener = new ViewSwitchListener() {
				
		int mCurrentPosition = 0;
		
		@Override
		public void onSwitching(float progress) {
			LinearLayout hl = (LinearLayout)findViewById(R.id.header_layout);
			int width = hl.getChildAt(0).getWidth();
			int x = (int) (progress * width);
			mHeader.smoothScrollTo(x, 0);
		}

		@Override
		public void onSwitched(int position) {
			LinearLayout hl = (LinearLayout)findViewById(R.id.header_layout);
			TextView oldView = (TextView)hl.getChildAt(mCurrentPosition);
			oldView.setTextColor(Color.GRAY);
			TextView newView = (TextView)hl.getChildAt(position);
			newView.setTextColor(Color.WHITE);
			mCurrentPosition = position;
		}

	};


	private void updateList() {
		
		List<Media> itemList = MediaLibrary.getInstance().getAudioItems();

		for (Media item : itemList) {
			HashMap<String,String> listItem = new HashMap<String,String>();
			listItem.put( "text1", item.getTitle());
			listItem.put( "text2", item.getPath());
			list.add( listItem );
		}	

		mAudioAdapter.notifyDataSetChanged();

	}

}
