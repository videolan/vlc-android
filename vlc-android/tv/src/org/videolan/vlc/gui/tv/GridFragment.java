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
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.gui.audio.MediaComparators;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;
import org.videolan.vlc.gui.video.VideoBrowserInterface;
import org.videolan.vlc.gui.video.VideoListHandler;
import org.videolan.vlc.util.Util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.VerticalGridFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.VerticalGridPresenter;
import android.util.Log;

public class GridFragment extends VerticalGridFragment implements VideoBrowserInterface {
    private static final String TAG = "VLC/GridFragment";

    private static final int NUM_COLUMNS = 5;

    public static final int CATEGORY_ARTISTS = 1;
    public static final int CATEGORY_ALBUMS = 2;
    public static final int CATEGORY_GENRES = 3;
    public static final int CATEGORY_SONGS = 4;

    protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
    protected Media mItemToUpdate;
    private Map<String, ListItem> mMediaItemMap;
    private ArrayList<ListItem> mMediaItemList;
    ArrayList<Media> mMediaList = null;
    private ArrayObjectAdapter mAdapter;
    private MediaLibrary mMediaLibrary;
    private static Thumbnailer sThumbnailer;
    HashMap<String, Integer> mMediaIndex;
    Context mContext;
    String mFilter;
    int mCategory;
    long mType = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        if (savedInstanceState != null){
            mType = savedInstanceState.getLong(MEDIA_SECTION);
            mCategory = savedInstanceState.getInt(AUDIO_CATEGORY);
            mFilter = savedInstanceState.getString(AUDIO_FILTER);
        } else {
            mType = getActivity().getIntent().getLongExtra(MEDIA_SECTION, -1);
            mCategory = getActivity().getIntent().getIntExtra(AUDIO_CATEGORY, 0);
            mFilter = getActivity().getIntent().getStringExtra(AUDIO_FILTER);
        }
        sThumbnailer = MainTvActivity.getThumbnailer();
        mMediaLibrary = MediaLibrary.getInstance();
        VerticalGridPresenter gridPresenter = new VerticalGridPresenter();
        gridPresenter.setNumberOfColumns(NUM_COLUMNS);
        setGridPresenter(gridPresenter);
        mAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
        setAdapter(mAdapter);
    }

    public void onResume() {
        super.onResume();
        if (mType == HEADER_VIDEO) {
            new AsyncVideoUpdate().execute();
        } else {
            new AsyncAudioUpdate().execute();
        }
        if (sThumbnailer != null)
            sThumbnailer.setVideoBrowser(this);
        if (mMediaLibrary.isWorking()) {
            Util.actionScanStart();
        }

    }

    public void onPause() {
        super.onPause();
        mMediaLibrary.removeUpdateHandler(mHandler);

		/* unregister from thumbnailer */
        if (sThumbnailer != null)
            sThumbnailer.setVideoBrowser(null);
        mBarrier.reset();
    }

    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putLong(MEDIA_SECTION, mType);
        outState.putInt(AUDIO_CATEGORY, mCategory);
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
        if (mAdapter != null && mMediaIndex.containsKey(mItemToUpdate.getLocation()))
            mAdapter.notifyArrayItemRangeChanged(mMediaIndex.get(mItemToUpdate.getLocation()), 1);
        try {
            mBarrier.await();
        } catch (InterruptedException e) {
        } catch (BrokenBarrierException e) {}
    }

    @Override
    public void updateList() {
    }

    @Override
    public void showProgressBar() {
        //TODO
    }

    @Override
    public void hideProgressBar() {
        //TODO
    }

    @Override
    public void clearTextInfo() {
        //TODO
    }

    @Override
    public void sendTextInfo(String info, int progress, int max) {
        Log.d(TAG, info);
    }

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

    public ListItem add(String title, String subTitle, Media media) {
        if(title == null) return null;
        title = title.trim();
        if(subTitle != null) subTitle = subTitle.trim();
        if (mMediaItemMap.containsKey(title))
            mMediaItemMap.get(title).mMediaList.add(media);
        else {
            ListItem item = new ListItem(title, subTitle, media, false);
            mMediaItemMap.put(title, item);
            mMediaItemList.add(item);
            return item;
        }
        return null;
    }

    public class AsyncVideoUpdate extends AsyncTask<Void, Media, Void> {

        public AsyncVideoUpdate() {}

        @Override
        protected void onPreExecute(){
            setTitle(getString(R.string.app_name_full));
            mAdapter.clear();
        }
        @Override
        protected Void doInBackground(Void... params) {
            int size;
            Media media;

            mMediaList = mMediaLibrary.getVideoItems();
            size = mMediaList == null ? 0 : mMediaList.size();
            mMediaIndex = new HashMap<String, Integer>(size);

            for (int i = 0 ; i < size ; ++i){
                media = mMediaList.get(i);
                mMediaIndex.put(media.getLocation(), i);
                publishProgress(media);

            }
            return null;
        }

        protected void onProgressUpdate(Media... medias){
            mAdapter.add(medias[0]);
        }

        @Override
        protected void onPostExecute(Void result) {
            setOnItemViewClickedListener(new OnItemViewClickedListener() {
                @Override
                public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                          RowPresenter.ViewHolder rowViewHolder, Row row) {
                    TvUtil.openMedia(getActivity(), (Media) item, null);
                }
            });
        }
    }

    public class AsyncAudioUpdate extends AsyncTask<Void, ListItem, String> {

        public AsyncAudioUpdate() {}

        @Override
        protected void onPreExecute() {
            setTitle(getString(R.string.app_name_full));
            mAdapter.clear();
            mMediaItemMap = new HashMap<String, ListItem>();
            mMediaItemList = new ArrayList<GridFragment.ListItem>();
        }

        @Override
        protected String doInBackground(Void... params) {
            String title;
            ListItem item;

            List<Media> audioList = MediaLibrary.getInstance().getAudioItems();
            if (CATEGORY_ARTISTS == mCategory){
                Collections.sort(audioList, MediaComparators.byArtist);
                title = getString(R.string.artists);
                for (Media media : audioList){
                    item = add(media.getArtist(), null, media);
                    if (item != null)
                        publishProgress(item);
                }
            } else if (CATEGORY_ALBUMS == mCategory){
                title = getString(R.string.albums);
                Collections.sort(audioList, MediaComparators.byAlbum);
                for (Media media : audioList){
                    if (mFilter == null
                            || (mType == FILTER_ARTIST && mFilter.equals(media.getArtist().trim()))
                            || (mType == FILTER_GENRE && mFilter.equals(media.getGenre().trim()))) {
                        item = add(media.getAlbum(), media.getArtist(), media);
                        if (item != null)
                            publishProgress(item);
                    }
                }
                //Customize title for artist/genre browsing
                if (mType == FILTER_ARTIST){
                    title = title + " " + mMediaItemList.get(0).mMediaList.get(0).getArtist();
                } else if (mType == FILTER_GENRE){
                    title = title + " " + mMediaItemList.get(0).mMediaList.get(0).getGenre();
                }
            } else if (CATEGORY_GENRES == mCategory){
                title = getString(R.string.genres);
                Collections.sort(audioList, MediaComparators.byGenre);
                for (Media media : audioList){
                    item = add(media.getGenre(), null, media);
                    if (item != null)
                        publishProgress(item);
                }
            } else if (CATEGORY_SONGS == mCategory){
                title = getString(R.string.songs);
                Collections.sort(audioList, MediaComparators.byName);
                ListItem mediaItem;
                for (Media media : audioList){
                    mediaItem = new ListItem(media.getTitle(), media.getArtist(), media, false);
                    mMediaItemMap.put(title, mediaItem);
                    mMediaItemList.add(mediaItem);
                    publishProgress(mediaItem);
                }
            } else {
                title = getString(R.string.app_name_full);
            }
            return title;
        }

        protected void onProgressUpdate(ListItem... items){
            mAdapter.add(items[0]);
        }

        @Override
        protected void onPostExecute(String title) {
            setTitle(title);
            setOnItemViewClickedListener(new OnItemViewClickedListener() {
                @Override
                public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                          RowPresenter.ViewHolder rowViewHolder, Row row) {
                    ListItem listItem = (ListItem) item;
                    Intent intent;
                    if (CATEGORY_ARTISTS == mCategory) {
                        intent = new Intent(mContext, VerticalGridActivity.class);
                        intent.putExtra(AUDIO_CATEGORY, CATEGORY_ALBUMS);
                        intent.putExtra(MEDIA_SECTION, FILTER_ARTIST);
                        intent.putExtra(AUDIO_FILTER, listItem.mMediaList.get(0).getArtist().trim());
                    } else if (CATEGORY_GENRES == mCategory) {
                        intent = new Intent(mContext, VerticalGridActivity.class);
                        intent.putExtra(AUDIO_CATEGORY, CATEGORY_ALBUMS);
                        intent.putExtra(MEDIA_SECTION, FILTER_GENRE);
                        intent.putExtra(AUDIO_FILTER, listItem.mMediaList.get(0).getGenre().trim());
                    } else {
                        ArrayList<String> locations = new ArrayList<String>();
                        if (CATEGORY_ALBUMS == mCategory)
                            Collections.sort(listItem.mMediaList, MediaComparators.byTrackNumber);
                        for (Media media : listItem.mMediaList) {
                            locations.add(media.getLocation());
                        }
                        intent = new Intent(mContext, AudioPlayerActivity.class);
                        intent.putExtra("locations", locations);
                    }
                    startActivity(intent);
                }
            });
        }
    }
}