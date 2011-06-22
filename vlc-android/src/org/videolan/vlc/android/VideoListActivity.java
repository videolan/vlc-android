package org.videolan.vlc.android;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

public class VideoListActivity extends ListActivity {
	
	private LinearLayout mNoFileLayout;
	private LinearLayout mLoadFileLayout;
	private VideoListAdapter mVideoAdapter;

	protected MediaItem mItemToUpdate;

	protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
	protected ThumbnailerManager mThumbnailerManager;
	private static VideoListActivity mInstance;	
	
	protected static final int UPDATE_ITEM = 0;
	protected static final int UPDATE_LIST = 1;

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		mInstance = this;
		super.onCreate(savedInstanceState);
		setContentView(R.layout.video_list);
		
		mVideoAdapter = new VideoListAdapter(this, R.layout.video_list_item);
		mNoFileLayout = (LinearLayout)findViewById(R.id.video_list_empty_nofile);
		mLoadFileLayout = (LinearLayout)findViewById(R.id.video_list_empty_loadfile);
		
		mThumbnailerManager = new ThumbnailerManager();
		
		setListAdapter(mVideoAdapter);
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		MediaItem item = (MediaItem) getListAdapter().getItem(position);
		Intent intent = new Intent(this, VideoPlayerActivity.class);
		intent.putExtra("filePath", item.getPath());
		startActivity(intent);
		super.onListItemClick(l, v, position, id);
	}
	
	public static VideoListActivity getInstance() {
		return mInstance;
	}
	
	
	/**
	 * Handle changes on the list
	 */
	protected Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case UPDATE_ITEM:
				mVideoAdapter.update(mItemToUpdate);
				try {
					mBarrier.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (BrokenBarrierException e) {
					e.printStackTrace();
				}
				break;
			case UPDATE_LIST:
				updateList();
				break;
			}
		}
	};
	
	private void updateList() {
		
		MediaLibraryActivity mediaLibraryActivity = MediaLibraryActivity.getInstance();
		
		List<MediaItem> itemList = mediaLibraryActivity.mItemList;
		
		mVideoAdapter.clear();
		
		if (itemList.size() > 0) {
			for (MediaItem item : itemList) {
				if (item.getType() == MediaItem.TYPE_VIDEO) {
					mVideoAdapter.add(item);
					if (item.getThumbnail() == null)
						mThumbnailerManager.addJob(item);
				}
			}	
			mVideoAdapter.sort();
		} else {
			mLoadFileLayout.setVisibility(View.INVISIBLE);
			mNoFileLayout.setVisibility(View.VISIBLE);
		}
		
		mediaLibraryActivity.mHandler.sendEmptyMessage(MediaLibraryActivity.HIDE_PROGRESSBAR);
		
		
	}

}
