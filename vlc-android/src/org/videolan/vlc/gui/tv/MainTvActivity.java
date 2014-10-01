package org.videolan.vlc.gui.tv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.Thumbnailer;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.video.VideoBrowserInterface;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.util.WeakHandler;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemClickedListener;
import android.support.v17.leanback.widget.Row;
import android.support.v4.app.FragmentActivity;

public class MainTvActivity extends FragmentActivity implements VideoBrowserInterface {

	public static final String TAG = "BrowseActivity";

	protected BrowseFragment mBrowseFragment;
	protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
	private MediaLibrary mMediaLibrary;
	private Thumbnailer mThumbnailer;
	protected Media mItemToUpdate;
	ArrayObjectAdapter mRowsAdapter;
	ArrayObjectAdapter videoAdapter;
	ArrayObjectAdapter audioAdapter;
	HashMap<String, Integer> mVideoIndex;
	Drawable mDefaultBackground;
	Activity mContext;

	OnItemClickedListener mItemClickListener = new OnItemClickedListener() {
		@Override
		public void onItemClicked(Object item, Row row) {
			VideoPlayerActivity.start(mContext, ((Media)item).getLocation(), false);
			//code to launch a DetailActivity
			//                    Intent intent = new Intent(MainTvActivity.this,
			//                            DetailsActivity.class);
			//                    // pass the item information
			//                    intent.putExtra("id", row.getId());
			//                    intent.putExtra("item", (Parcelable)item);
			//                    startActivity(intent);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		setContentView(R.layout.tv_main_fragment);

		mMediaLibrary = MediaLibrary.getInstance();
		mDefaultBackground = getResources().getDrawable(R.drawable.background);
		final FragmentManager fragmentManager = getFragmentManager();
		mBrowseFragment = (BrowseFragment) fragmentManager.findFragmentById(
				R.id.browse_fragment);

		// Set display parameters for the BrowseFragment
		mBrowseFragment.setHeadersState(BrowseFragment.HEADERS_ENABLED);
		mBrowseFragment.setTitle(getString(R.string.app_name));
		mBrowseFragment.setBadgeDrawable(getResources().getDrawable(R.drawable.cone));

		// add a listener for selected items
		mBrowseFragment.setOnItemClickedListener(mItemClickListener);
		mMediaLibrary.loadMediaItems(this, true);
		mThumbnailer = new Thumbnailer(this, getWindowManager().getDefaultDisplay());
		BackgroundManager.getInstance(this).attach(getWindow());
	}

	public void onResume() {
		super.onResume();
		mMediaLibrary.addUpdateHandler(mHandler);
		if (mMediaLibrary.isWorking()) {
			actionScanStart();
		}

		/* Start the thumbnailer */
		if (mThumbnailer != null)
			mThumbnailer.start(this);
	}

	public void onPause() {
		super.onPause();
		mMediaLibrary.removeUpdateHandler(mHandler);

		/* Stop the thumbnailer */
		if (mThumbnailer != null)
			mThumbnailer.stop();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mThumbnailer != null)
			mThumbnailer.clearJobs();
		mBarrier.reset();
	}

    protected void updateBackground(Drawable drawable) {
        BackgroundManager.getInstance(this).setDrawable(drawable);
    }

    protected void clearBackground() {
        BackgroundManager.getInstance(this).setDrawable(mDefaultBackground);
    }


	public static void actionScanStart() {
		Intent intent = new Intent();
		intent.setAction(ACTION_SCAN_START);
		VLCApplication.getAppContext().sendBroadcast(intent);
	}

	public static void actionScanStop() {
		Intent intent = new Intent();
		intent.setAction(ACTION_SCAN_STOP);
		VLCApplication.getAppContext().sendBroadcast(intent);
	}

	public void await() throws InterruptedException, BrokenBarrierException {
		mBarrier.await();
	}

	public void resetBarrier() {
		mBarrier.reset();
	}

	private void updateList() {
		MediaDatabase mediaDatabase = MediaDatabase.getInstance();
		ArrayList<Media> videoList = mMediaLibrary.getVideoItems();
		ArrayList<Media> audioList = mMediaLibrary.getAudioItems();
		int size;
		Media item;
		Bitmap picture;
		mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

		// Update video section
		if (!videoList.isEmpty()) {
			size = videoList.size();
			mVideoIndex = new HashMap<String, Integer>(size);
			videoAdapter = new ArrayObjectAdapter(
					new CardPresenter());
			for (int i = 0 ; i < videoList.size() ; ++i) {
				item = videoList.get(i);
				picture = mediaDatabase.getPicture(this, item.getLocation());

				videoAdapter.add(item);
				mVideoIndex.put(item.getLocation(), i);
				if (mThumbnailer != null){
					if (picture== null) {
						mThumbnailer.addJob(item);
					} else {
						MediaDatabase.setPicture(item, picture);
						picture = null;
					}
				}
			}
			HeaderItem header = new HeaderItem(0, "Videos", null);
			mRowsAdapter.add(new ListRow(header, videoAdapter));
		}
		
		// update audio section
		if (!audioList.isEmpty()) {
			audioAdapter = new ArrayObjectAdapter(new CardPresenter());
			for (Media music : audioList) {
				audioAdapter.add(music);
			}
			HeaderItem header = new HeaderItem(1, "Music", null);
			mRowsAdapter.add(new ListRow(header, audioAdapter));
		}
		mBrowseFragment.setAdapter(mRowsAdapter);
	}

	@Override
	public void setItemToUpdate(Media item) {
		mItemToUpdate = item;
		mHandler.sendEmptyMessage(UPDATE_ITEM);
	}

	private void updateItem() {
		videoAdapter.notifyArrayItemRangeChanged(mVideoIndex.get(mItemToUpdate.getLocation()), 1);
		try {
			mBarrier.await();
		} catch (InterruptedException e) {
		} catch (BrokenBarrierException e) {}
	}

	private Handler mHandler = new VideoListHandler(this);

	private static class VideoListHandler extends WeakHandler<MainTvActivity> {
		public VideoListHandler(MainTvActivity owner) {
			super(owner);
		}

		@Override
		public void handleMessage(Message msg) {
			MainTvActivity owner = getOwner();
			if(owner == null) return;

			switch (msg.what) {
			case UPDATE_ITEM:
				owner.updateItem();
				break;
			case MediaLibrary.MEDIA_ITEMS_UPDATED:
				owner.updateList();
			}
		}
	};
}
