/*****************************************************************************
 * GridFragment.java
 *****************************************************************************
 * Copyright © 2012-2014 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v17.leanback.app.VerticalGridFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.OnItemClickedListener;
import android.support.v17.leanback.widget.OnItemSelectedListener;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.VerticalGridPresenter;
import android.util.Log;

public class GridFragment extends VerticalGridFragment implements VideoBrowserInterface {
	private static final String TAG = "VerticalGridFragment";

	private static final int NUM_COLUMNS = 5;

	protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
	protected Media mItemToUpdate;
	private ArrayObjectAdapter mAdapter;
	private MediaLibrary mMediaLibrary;
	private Thumbnailer mThumbnailer;
	HashMap<String, Integer> mVideoIndex;
	Context mContext;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		mContext = getActivity();

		setTitle(getString(R.string.app_name_full));

		mMediaLibrary = MediaLibrary.getInstance();
		mThumbnailer = new Thumbnailer(mContext, getActivity().getWindowManager().getDefaultDisplay());
		setupFragment();
	}

	public void onResume() {
		super.onResume();
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

	private void setupFragment() {
		int size;
		Media media;
		Bitmap picture;

		MediaDatabase mediaDatabase = MediaDatabase.getInstance();
		VerticalGridPresenter gridPresenter = new VerticalGridPresenter();
		mAdapter = new ArrayObjectAdapter(new CardPresenter());

		gridPresenter.setNumberOfColumns(NUM_COLUMNS);
		setGridPresenter(gridPresenter);


		ArrayList<Media> videoList = mMediaLibrary.getVideoItems();
		size = videoList.size();
		mVideoIndex = new HashMap<String, Integer>(size);
		
		for (int i = 0 ; i < size ; ++i){
			media = videoList.get(i);
			picture = mediaDatabase.getPicture(mContext, media.getLocation());
			mAdapter.add(media);
			mVideoIndex.put(media.getLocation(), i);
			if (mThumbnailer != null){
				if (picture== null) {
					mThumbnailer.addJob(media);
				} else {
					MediaDatabase.setPicture(media, picture);
					picture = null;
				}
			}
		}

		setAdapter(mAdapter);

		/*setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(Object item, Row row) {
			}
		});*/

		setOnItemClickedListener(new OnItemClickedListener() {
			@Override
			public void onItemClicked(Object item, Row row) {
				Media media = (Media)item;
				if (media.getType() == Media.TYPE_VIDEO){
					VideoPlayerActivity.start(getActivity(), media.getLocation(), false);
				}
			}
		});
	}

	public void await() throws InterruptedException, BrokenBarrierException {
		mBarrier.await();
	}

	public void resetBarrier() {
		mBarrier.reset();
	}
	@Override
	public void setItemToUpdate(Media item) {
		mItemToUpdate = item;
		mHandler.sendEmptyMessage(UPDATE_ITEM);
	}

	private void updateItem() {
		mAdapter.notifyArrayItemRangeChanged(mVideoIndex.get(mItemToUpdate.getLocation()), 1);
		try {
			mBarrier.await();
		} catch (InterruptedException e) {
		} catch (BrokenBarrierException e) {}
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

	private Handler mHandler = new VideoListHandler(this);

	private static class VideoListHandler extends WeakHandler<GridFragment> {
		public VideoListHandler(GridFragment owner) {
			super(owner);
		}

		@Override
		public void handleMessage(Message msg) {
			GridFragment owner = getOwner();
			if(owner == null) return;

			switch (msg.what) {
			case UPDATE_ITEM:
				owner.updateItem();
				break;
			}
		}
	};
}