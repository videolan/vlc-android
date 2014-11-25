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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.Thumbnailer;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.gui.audio.MediaComparators;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;
import org.videolan.vlc.gui.video.VideoBrowserInterface;
import org.videolan.vlc.gui.video.VideoListHandler;
import org.videolan.vlc.util.Util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.VerticalGridFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.OnItemClickedListener;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.VerticalGridPresenter;

public class GridFragment extends VerticalGridFragment implements VideoBrowserInterface {
	private static final String TAG = "VLC/GridFragment";

	private static final int NUM_COLUMNS = 5;

	protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
	protected Media mItemToUpdate;
    private Map<String, ListItem> mMediaItemMap;
    private ArrayList<ListItem> mMediaItemList;
	private ArrayObjectAdapter mAdapter;
	private MediaLibrary mMediaLibrary;
	private Thumbnailer mThumbnailer;
	HashMap<String, Integer> mMediaIndex;
	Context mContext;
	String mCategory, mFilter;
	long mType = -1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = getActivity();
		if (savedInstanceState != null){
			mType = savedInstanceState.getLong(MEDIA_SECTION);
			mCategory = savedInstanceState.getString(AUDIO_CATEGORY);
			mFilter = savedInstanceState.getString(AUDIO_FILTER);
		} else {
			mType = getActivity().getIntent().getLongExtra(MEDIA_SECTION, -1);
			mCategory = getActivity().getIntent().getStringExtra(AUDIO_CATEGORY);
			mFilter = getActivity().getIntent().getStringExtra(AUDIO_FILTER);
		}


		mMediaLibrary = MediaLibrary.getInstance();
		if (mType == HEADER_VIDEO) {
			mThumbnailer = new Thumbnailer(mContext, getActivity().getWindowManager().getDefaultDisplay());
			setupFragment();
		} else if (mType == HEADER_MUSIC) {
			setupFragment();
		} else {
			setupFragmentForAudio();
		}
	}

	public void onResume() {
		super.onResume();
		if (mMediaLibrary.isWorking()) {
			Util.actionScanStart();
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

	public void onSaveInstanceState(Bundle outState){
		super.onSaveInstanceState(outState);
		outState.putLong(MEDIA_SECTION, mType);
		outState.putString(AUDIO_CATEGORY, mCategory);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mThumbnailer != null)
			mThumbnailer.clearJobs();
		mBarrier.reset();
	}

	private void setupFragmentForAudio() {
		Bitmap picture;
		String title;
		mMediaItemMap = new HashMap<String, ListItem>();
		mMediaItemList = new ArrayList<GridFragment.ListItem>();
		VerticalGridPresenter gridPresenter = new VerticalGridPresenter();
		mAdapter = new ArrayObjectAdapter(new CardPresenter());
		gridPresenter.setNumberOfColumns(NUM_COLUMNS);
		setGridPresenter(gridPresenter);

		List<Media> audioList = MediaLibrary.getInstance().getAudioItems();
		if (getString(R.string.artists).equals(mCategory)){
			Collections.sort(audioList, MediaComparators.byArtist);
			title = getString(R.string.artists);
			for (Media media : audioList){
				add(media.getArtist(), null, media);
			}
		} else if (getString(R.string.albums).equals(mCategory)){
			title = getString(R.string.albums);
			Collections.sort(audioList, MediaComparators.byAlbum);
			for (Media media : audioList){
				if (mFilter == null 
						|| (mType == FILTER_ARTIST && mFilter.equals(media.getArtist().trim()))
						|| (mType == FILTER_GENRE && mFilter.equals(media.getGenre().trim()))) {
				add(media.getAlbum(), media.getArtist(), media);
				}
			}
			//Customize title for artist/genre browsing
			if (mType == FILTER_ARTIST){
				title = title + " " + mMediaItemList.get(0).mMediaList.get(0).getArtist();
			} else if (mType == FILTER_GENRE){
				title = title + " " + mMediaItemList.get(0).mMediaList.get(0).getGenre();
			}
		} else if (getString(R.string.genres).equals(mCategory)){
			title = getString(R.string.genres);
			Collections.sort(audioList, MediaComparators.byGenre);
			for (Media media : audioList){
				add(media.getGenre(), null, media);
			}
		} else if (getString(R.string.songs).equals(mCategory)){
			title = getString(R.string.songs);
			Collections.sort(audioList, MediaComparators.byName);
			for (Media media : audioList){
				add(media.getTitle(), media.getArtist(), media);
			}
		} else {
			title = getString(R.string.app_name_full);
		}
		setTitle(title);
		//check for pictures
		for (Media media : audioList){
			picture = AudioUtil.getCover(mContext, media, 320);
			if (picture != null){
				MediaDatabase.setPicture(media, picture);
				picture = null;
			}
		}
		mAdapter.addAll(0, mMediaItemList);
		setAdapter(mAdapter);

        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                      RowPresenter.ViewHolder rowViewHolder, Row row) {
				ListItem listItem = (ListItem) item;
				Intent intent;
				if (getString(R.string.artists).equals(mCategory)){
					intent = new Intent(mContext, VerticalGridActivity.class);
					intent.putExtra(AUDIO_CATEGORY, getString(R.string.albums));
					intent.putExtra(MEDIA_SECTION, FILTER_ARTIST);
					intent.putExtra(AUDIO_FILTER, listItem.mMediaList.get(0).getArtist().trim());
				} else if (getString(R.string.genres).equals(mCategory)){
					intent = new Intent(mContext, VerticalGridActivity.class);
					intent.putExtra(AUDIO_CATEGORY, getString(R.string.albums));
					intent.putExtra(MEDIA_SECTION, FILTER_GENRE);
					intent.putExtra(AUDIO_FILTER, listItem.mMediaList.get(0).getGenre().trim());
				} else {
					ArrayList<String> locations = new ArrayList<String>();
					for (Media media : listItem.mMediaList){
						locations.add(media.getLocation());
					}
					intent = new Intent(mContext, AudioPlayerActivity.class);
					intent.putExtra("locations", locations);
				}
				startActivity(intent);
			}
		});
	}

	//TODO shrink audio part, I keep it for now just in case...
	private void setupFragment() {
		setTitle(getString(R.string.app_name_full));
		int size;
		Media media;
		Bitmap picture;

		MediaDatabase mediaDatabase = MediaDatabase.getInstance();
		VerticalGridPresenter gridPresenter = new VerticalGridPresenter();
		mAdapter = new ArrayObjectAdapter(new CardPresenter());

		gridPresenter.setNumberOfColumns(NUM_COLUMNS);
		setGridPresenter(gridPresenter);


		ArrayList<Media> mediaList = null;
		if (mType == HEADER_VIDEO)
			mediaList = mMediaLibrary.getVideoItems();
		else
			mediaList = mMediaLibrary.getAudioItems();
		size = mediaList == null ? 0 : mediaList.size();
		mMediaIndex = new HashMap<String, Integer>(size);
		
		for (int i = 0 ; i < size ; ++i){
			media = mediaList.get(i);
			mAdapter.add(media);
			mMediaIndex.put(media.getLocation(), i);
			if (mThumbnailer != null){
				picture = mediaDatabase.getPicture(mContext, media.getLocation());
				if (picture== null) {
					mThumbnailer.addJob(media);
				} else {
					MediaDatabase.setPicture(media, picture);
					picture = null;
				}
			} else {
				picture = AudioUtil.getCover(mContext, media, 320);
				if (picture != null){
					MediaDatabase.setPicture(media, picture);
					picture = null;
				}
			}
		}

		setAdapter(mAdapter);

		setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                      RowPresenter.ViewHolder rowViewHolder, Row row) {
                Media media = (Media) item;
                TvUtil.openMedia(getActivity(), media, null);
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
		mHandler.sendEmptyMessage(VideoListHandler.UPDATE_ITEM);
	}

	public void updateItem() {
		mAdapter.notifyArrayItemRangeChanged(mMediaIndex.get(mItemToUpdate.getLocation()), 1);
		try {
			mBarrier.await();
		} catch (InterruptedException e) {
		} catch (BrokenBarrierException e) {}
	}

	@Override
	public void updateList() {
		// TODO Auto-generated method stub
	};

	private Handler mHandler = new VideoListHandler(this);

	// An item of the list: a media or a separator.
    public class ListItem {
        public String mTitle;
        public String mSubTitle;
        public ArrayList<Media> mMediaList;
        public boolean mIsSeparator;

        public ListItem(String title, String subTitle, Media media, boolean isSeparator) {
            mMediaList = new ArrayList<Media>();
            if (media != null)
                mMediaList.add(media);
            mTitle = title;
            mSubTitle = subTitle;
            mIsSeparator = isSeparator;
        }
    }

    public void add(String title, String subTitle, Media media) {
        if(title == null) return;
        title = title.trim();
        if(subTitle != null) subTitle = subTitle.trim();
        if (mMediaItemMap.containsKey(title))
            mMediaItemMap.get(title).mMediaList.add(media);
        else {
            ListItem item = new ListItem(title, subTitle, media, false);
            mMediaItemMap.put(title, item);
            mMediaItemList.add(item);
        }
    }
}