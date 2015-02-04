/*****************************************************************************
 * MainTvActivity.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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

import org.videolan.libvlc.LibVLC;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.Thumbnailer;
import org.videolan.vlc.gui.PreferencesActivity;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;
import org.videolan.vlc.gui.tv.browser.GridFragment;
import org.videolan.vlc.gui.tv.browser.MusicFragment;
import org.videolan.vlc.gui.tv.browser.VerticalGridActivity;
import org.videolan.vlc.interfaces.IVideoBrowser;
import org.videolan.vlc.gui.video.VideoListHandler;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

public class MainTvActivity extends Activity implements IVideoBrowser {

    private static final int NUM_ITEMS_PREVIEW = 5;

    public static final long HEADER_VIDEO = 0;
    public static final long HEADER_CATEGORIES = 1;
    public static final long HEADER_NETWORK = 2;
    public static final long HEADER_MISC = 3;

    public static final String BROWSER_TYPE = "browser_type";

    public static final String TAG = "VLC/MainTvActivity";

    protected BrowseFragment mBrowseFragment;
    protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
    private MediaLibrary mMediaLibrary;
    private static Thumbnailer sThumbnailer;
    private MediaWrapper mItemToUpdate;
    ArrayObjectAdapter mRowsAdapter;
    ArrayObjectAdapter mVideoAdapter;
    ArrayObjectAdapter mCategoriesAdapter;
    ArrayObjectAdapter mNetworkAdapter;
    ArrayObjectAdapter mOtherAdapter;
    HashMap<String, Integer> mVideoIndex;
    Drawable mDefaultBackground;
    Activity mContext;

    OnItemViewClickedListener mItemClickListener = new OnItemViewClickedListener() {
        @Override
        public void onItemClicked(Presenter.ViewHolder viewHolder, Object o, RowPresenter.ViewHolder viewHolder2, Row row) {
            if (row.getId() == HEADER_CATEGORIES){
                CardPresenter.SimpleCard card = (CardPresenter.SimpleCard)o;
                Intent intent = new Intent(mContext, VerticalGridActivity.class);
                intent.putExtra(BROWSER_TYPE, HEADER_CATEGORIES);
                intent.putExtra(MusicFragment.AUDIO_CATEGORY, card.getId());
                startActivity(intent);
            } else if (row.getId() == HEADER_VIDEO)
                TvUtil.openMedia(mContext, o, row);
            else if (row.getId() == HEADER_MISC)
                startActivity(new Intent(mContext, PreferencesActivity.class));
            else if (row.getId() == HEADER_NETWORK){
                    TvUtil.openMedia(mContext, o, row);
            }
        }
    };

    OnClickListener mSearchClickedListenernew = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent(mContext, SearchActivity.class);
            startActivity(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*
         * skip browser and show directly Audio Player if a song is playing
         */
        if (LibVLC.getExistingInstance() != null){
            if (LibVLC.getExistingInstance().isPlaying()){
                startActivity(new Intent(this, AudioPlayerActivity.class));
                finish();
                return;
            }
        }
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
        // set search icon color
        mBrowseFragment.setSearchAffordanceColor(getResources().getColor(R.color.darkorange));

        // add a listener for selected items
        mBrowseFragment.setOnItemViewClickedListener(mItemClickListener);

        mBrowseFragment.setOnSearchClickedListener(mSearchClickedListenernew);
        mMediaLibrary.loadMediaItems(this, true);
        BackgroundManager.getInstance(this).attach(getWindow());
    }

    public void onResume() {
        super.onResume();
        mMediaLibrary.addUpdateHandler(mHandler);
        if (!mMediaLibrary.isWorking())
            updateList();
        if (sThumbnailer != null)
            sThumbnailer.setVideoBrowser(this);
    }

    public void onPause() {
        super.onPause();
        mMediaLibrary.removeUpdateHandler(mHandler);

        /* Stop the thumbnailer */
        if (sThumbnailer != null)
            sThumbnailer.setVideoBrowser(null);
        mBarrier.reset();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sThumbnailer != null)
            sThumbnailer.clearJobs();
    }

    protected void updateBackground(Drawable drawable) {
        BackgroundManager.getInstance(this).setDrawable(drawable);
    }

    protected void clearBackground() {
        BackgroundManager.getInstance(this).setDrawable(mDefaultBackground);
    }

    public void await() throws InterruptedException, BrokenBarrierException {
        mBarrier.await();
    }

    public void resetBarrier() {
        mBarrier.reset();
    }

    public void updateList() {
        new AsyncUpdate().execute();
        checkThumbs();
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

    @Override
    public void setItemToUpdate(MediaWrapper item) {
        mItemToUpdate = item;
        mHandler.sendEmptyMessage(VideoListHandler.UPDATE_ITEM);
    }

    public void updateItem() {
        if (mVideoAdapter != null && mVideoIndex != null && mItemToUpdate != null) {
            if (mVideoIndex.containsKey(mItemToUpdate.getLocation())) {
                mVideoAdapter.notifyArrayItemRangeChanged(mVideoIndex.get(mItemToUpdate.getLocation()), 1);
            }
        }
        try {
            mBarrier.await();
        } catch (InterruptedException e) {
        } catch (BrokenBarrierException e) {}

    }

    private Handler mHandler = new VideoListHandler(this);

    public class AsyncUpdate extends AsyncTask<Void, Void, Void> {

        public AsyncUpdate() { }

        @Override
        protected void onPreExecute(){
            mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        }
        @Override
        protected Void doInBackground(Void... params) {
            MediaDatabase mediaDatabase = MediaDatabase.getInstance();
            ArrayList<MediaWrapper> videoList = mMediaLibrary.getVideoItems();
            int size;
            MediaWrapper item;

            // Update video section
            if (!videoList.isEmpty()) {
                size = videoList.size();
                mVideoIndex = new HashMap<String, Integer>(size);
                mVideoAdapter = new ArrayObjectAdapter(
                        new CardPresenter(mContext));
                if (NUM_ITEMS_PREVIEW < size)
                    size = NUM_ITEMS_PREVIEW;
                for (int i = 0 ; i < size ; ++i) {
                    item = videoList.get(i);

                    mVideoAdapter.add(item);
                    mVideoIndex.put(item.getLocation(), i);
                }
                // Empty item to launch grid activity
                mVideoAdapter.add(new CardPresenter.SimpleCard(0, "Browse more", R.drawable.ic_video_collection_big));

                HeaderItem header = new HeaderItem(HEADER_VIDEO, getString(R.string.video), null);
                mRowsAdapter.add(new ListRow(header, mVideoAdapter));
            }

            mCategoriesAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_ARTISTS, getString(R.string.artists), R.drawable.ic_artist_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_ALBUMS, getString(R.string.albums), R.drawable.ic_album_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_GENRES, getString(R.string.genres), R.drawable.ic_genre_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_SONGS, getString(R.string.songs), R.drawable.ic_song_big));
            HeaderItem header = new HeaderItem(HEADER_CATEGORIES, getString(R.string.audio), null);
            mRowsAdapter.add(new ListRow(header, mCategoriesAdapter));

            mNetworkAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            mNetworkAdapter.add(new CardPresenter.SimpleCard(0, getString(R.string.network_browsing), R.drawable.ic_menu_goto));
            ArrayList<String> favs = MediaDatabase.getInstance().getAllNetworkFav();
            if (!favs.isEmpty()) {
                for (String fav : favs) {
                    mNetworkAdapter.add(new MediaWrapper(fav));
                }
            }
            header = new HeaderItem(HEADER_NETWORK, getString(R.string.network_browsing), null);
            mRowsAdapter.add(new ListRow(header, mNetworkAdapter));


            mOtherAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            mOtherAdapter.add(new CardPresenter.SimpleCard(0, getString(R.string.preferences), R.drawable.ic_menu_preferences_big));
            header = new HeaderItem(HEADER_MISC, getString(R.string.other), null);
            mRowsAdapter.add(new ListRow(header, mOtherAdapter));

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mBrowseFragment.setAdapter(mRowsAdapter);
        }
    }

    private void checkThumbs() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                sThumbnailer = new Thumbnailer(mContext, getWindowManager().getDefaultDisplay());
                Bitmap picture;
                ArrayList<MediaWrapper> videoList = mMediaLibrary.getVideoItems();
                MediaDatabase mediaDatabase = MediaDatabase.getInstance();
                if (sThumbnailer != null && videoList != null && !videoList.isEmpty()) {
                    for (MediaWrapper MediaWrapper : videoList){
                        picture = mediaDatabase.getPicture(mContext, MediaWrapper.getLocation());
                        if (picture== null)
                            sThumbnailer.addJob(MediaWrapper);
                    }
                    if (sThumbnailer.getJobsCount() > 0)
                        sThumbnailer.start((IVideoBrowser) mContext);
                }
            }
        }).start();
    }

    public static Thumbnailer getThumbnailer(){
        return sThumbnailer;
    }
}
