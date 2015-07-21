/*****************************************************************************
 * MainTvActivity.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
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

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;

import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.Thumbnailer;
import org.videolan.vlc.gui.PreferencesActivity;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;
import org.videolan.vlc.gui.tv.browser.BaseTvActivity;
import org.videolan.vlc.gui.tv.browser.MusicFragment;
import org.videolan.vlc.gui.tv.browser.VerticalGridActivity;
import org.videolan.vlc.gui.video.VideoListHandler;
import org.videolan.vlc.interfaces.IVideoBrowser;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.VLCInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class MainTvActivity extends BaseTvActivity implements IVideoBrowser, OnItemViewSelectedListener,
        OnItemViewClickedListener, OnClickListener {

    private static final int NUM_ITEMS_PREVIEW = 5;

    public static final long HEADER_VIDEO = 0;
    public static final long HEADER_CATEGORIES = 1;
    public static final long HEADER_NETWORK = 2;
    public static final long HEADER_MISC = 3;

    private static final int ACTIVITY_RESULT_PREFERENCES = 1;

    public static final String BROWSER_TYPE = "browser_type";

    public static final String TAG = "VLC/MainTvActivity";

    protected BrowseFragment mBrowseFragment;
    private ProgressBar mProgressBar;
    protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
    private static Thumbnailer sThumbnailer;
    private MediaWrapper mItemToUpdate;
    ArrayObjectAdapter mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
    ArrayObjectAdapter mVideoAdapter;
    ArrayObjectAdapter mCategoriesAdapter;
    ArrayObjectAdapter mNetworkAdapter;
    ArrayObjectAdapter mOtherAdapter;
    View mRootContainer;
    HashMap<String, Integer> mVideoIndex;
    Drawable mDefaultBackground;
    Activity mContext;
    private Object mSelectedItem;
    private AsyncUpdate mUpdateTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaLibrary.loadMediaItems(false);

        if (!VLCInstance.testCompatibleCPU(this)) {
            finish();
            return;
        }

        mContext = this;
        setContentView(R.layout.tv_main_fragment);

        mDefaultBackground = getResources().getDrawable(R.drawable.background);
        final FragmentManager fragmentManager = getFragmentManager();
        mBrowseFragment = (BrowseFragment) fragmentManager.findFragmentById(
                R.id.browse_fragment);
        mProgressBar = (ProgressBar) findViewById(R.id.tv_main_progress);

        // Set display parameters for the BrowseFragment
        mBrowseFragment.setHeadersState(BrowseFragment.HEADERS_ENABLED);
        mBrowseFragment.setTitle(getString(R.string.app_name));
        mBrowseFragment.setBadgeDrawable(getResources().getDrawable(R.drawable.icon));

        // add a listener for selected items
        mBrowseFragment.setOnItemViewClickedListener(this);
        mBrowseFragment.setOnItemViewSelectedListener(this);

        if (!Build.MANUFACTURER.equalsIgnoreCase("amazon")) { //Hide search for Amazon Fire TVs
            mBrowseFragment.setOnSearchClickedListener(this);
            // set search icon color
            mBrowseFragment.setSearchAffordanceColor(getResources().getColor(R.color.orange500));
        }
        mRootContainer = mBrowseFragment.getView();
    }

    @Override
    public void onConnected(PlaybackService service) {
        super.onConnected(service);
        /*
         * skip browser and show directly Audio Player if a song is playing
         */
        if (service.isPlaying() && !service.canSwitchToVideo()) {
            updateList();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Intent recommendationIntent = new Intent(this,
                RecommendationsService.class);
        startService(recommendationIntent);
    }

    protected void onResume() {
        super.onResume();
        mMediaLibrary.addUpdateHandler(mHandler);
        if (sThumbnailer != null)
            sThumbnailer.setVideoBrowser(this);

        mBrowseFragment.setBrandColor(getResources().getColor(R.color.orange800));
    }

    protected void onPause() {
        super.onPause();
        mMediaLibrary.removeUpdateHandler(mHandler);

        /* Stop the thumbnailer */
        if (sThumbnailer != null)
            sThumbnailer.setVideoBrowser(null);
        mBarrier.reset();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sThumbnailer != null)
            sThumbnailer.clearJobs();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            if (resultCode == PreferencesActivity.RESULT_RESCAN)
                MediaLibrary.getInstance().loadMediaItems(true);
            else if (resultCode == PreferencesActivity.RESULT_RESTART) {
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_BUTTON_Y) && mSelectedItem instanceof MediaWrapper) {
            MediaWrapper media = (MediaWrapper) mSelectedItem;
            if (media.getType() != MediaWrapper.TYPE_DIR)
                return false;
            Intent intent = new Intent(this,
                    DetailsActivity.class);
            // pass the item information
            intent.putExtra("media", (MediaWrapper) mSelectedItem);
            intent.putExtra("item", new MediaItemDetails(media.getTitle(), media.getArtist(), media.getAlbum(), media.getLocation()));
            startActivity(intent);
            return true;
        }
        return super.onKeyDown(keyCode, event);
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
        if (mUpdateTask == null || mUpdateTask.getStatus() == AsyncTask.Status.FINISHED) {
            mUpdateTask = new AsyncUpdate();
            mUpdateTask.execute();
        } else {
            mUpdateTask.AskRefresh();
        }
        checkThumbs();
    }

    @Override
    public void showProgressBar() {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                mProgressBar.setVisibility(View.VISIBLE);
//            }
//        });
    }

    @Override
    public void hideProgressBar() {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                mProgressBar.setVisibility(View.GONE);
//            }
//        });
    }

    @Override
    public void clearTextInfo() {
        //TODO
    }

    @Override
    public void sendTextInfo(String info, int progress, int max) {
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
        } catch (BrokenBarrierException e) {
        }

    }

    private Handler mHandler = new VideoListHandler(this);

    @Override
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        mSelectedItem = item;
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        if (row.getId() == HEADER_CATEGORIES) {
            if (((CardPresenter.SimpleCard)item).getId() == MusicFragment.CATEGORY_NOW_PLAYING){ //NOW PLAYING CARD
                startActivity(new Intent(this, AudioPlayerActivity.class));
                return;
            }
            CardPresenter.SimpleCard card = (CardPresenter.SimpleCard) item;
            Intent intent = new Intent(mContext, VerticalGridActivity.class);
            intent.putExtra(BROWSER_TYPE, HEADER_CATEGORIES);
            intent.putExtra(MusicFragment.AUDIO_CATEGORY, card.getId());
            startActivity(intent);
        } else if (row.getId() == HEADER_VIDEO)
            TvUtil.openMedia(mContext, item, row);
        else if (row.getId() == HEADER_MISC)
            startActivityForResult(new Intent(this, PreferencesActivity.class), ACTIVITY_RESULT_PREFERENCES);
        else if (row.getId() == HEADER_NETWORK) {
            TvUtil.openMedia(mContext, item, row);
        }
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(mContext, SearchActivity.class);
        startActivity(intent);
    }

    public class AsyncUpdate extends AsyncTask<Void, Void, Void> {
        private boolean askRefresh = false;

        public AsyncUpdate() {
        }

        public void AskRefresh() { //Ask for refresh while update is ongoing
            askRefresh = true;
        }

        @Override
        protected void onPreExecute() {
            mRowsAdapter.clear();
            mProgressBar.setVisibility(View.VISIBLE);

            //Video Section
            mVideoIndex = new HashMap<String, Integer>();
            mVideoAdapter = new ArrayObjectAdapter(
                    new CardPresenter(mContext));
            final HeaderItem videoHeader = new HeaderItem(HEADER_VIDEO, getString(R.string.video));
            // Empty item to launch grid activity
            mVideoAdapter.add(new CardPresenter.SimpleCard(0, "All videos", R.drawable.ic_video_collection_big));
            mRowsAdapter.add(new ListRow(videoHeader, mVideoAdapter));
            //Music sections
            mCategoriesAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            final HeaderItem musicHeader = new HeaderItem(HEADER_CATEGORIES, getString(R.string.audio));
            if (mService != null && mService.hasMedia() && !mService.canSwitchToVideo())
                mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_NOW_PLAYING, getString(R.string.music_now_playing), R.drawable.ic_play_circle_big_o));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_ARTISTS, getString(R.string.artists), R.drawable.ic_artist_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_ALBUMS, getString(R.string.albums), R.drawable.ic_album_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_GENRES, getString(R.string.genres), R.drawable.ic_genre_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_SONGS, getString(R.string.songs), R.drawable.ic_song_big));
            mRowsAdapter.add(new ListRow(musicHeader, mCategoriesAdapter));

            if (AndroidDevices.hasLANConnection()) {
                mNetworkAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
                final ArrayList<MediaWrapper> favs = MediaDatabase.getInstance().getAllNetworkFav();
                final HeaderItem networkHeader = new HeaderItem(HEADER_NETWORK, getString(R.string.network_browsing));

                mNetworkAdapter.add(new CardPresenter.SimpleCard(0, getString(R.string.network_browsing), R.drawable.ic_menu_network_big));
                if (!favs.isEmpty()) {
                    for (MediaWrapper fav : favs) {
                        mNetworkAdapter.add(fav);
                    }
                }
                mRowsAdapter.add(new ListRow(networkHeader, mNetworkAdapter));
            }

            mOtherAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            final HeaderItem miscHeader = new HeaderItem(HEADER_MISC, getString(R.string.other));

            mOtherAdapter.add(new CardPresenter.SimpleCard(0, getString(R.string.preferences), R.drawable.ic_menu_preferences_big));
            mRowsAdapter.add(new ListRow(miscHeader, mOtherAdapter));
            mBrowseFragment.setAdapter(mRowsAdapter);
        }

        @Override
        protected Void doInBackground(Void... params) {
            final ArrayList<MediaWrapper> videoList = mMediaLibrary.getVideoItems();
            int size;
            // Update video section
            if (!videoList.isEmpty()) {
                size = videoList.size();
                if (NUM_ITEMS_PREVIEW < size)
                    size = NUM_ITEMS_PREVIEW;
                final int total = size;
                mRootContainer.post(new Runnable() {
                    @Override
                    public void run() {
                        MediaWrapper item;
                        for (int i = 0; i < total; ++i) {
                            item = videoList.get(i);
                            mVideoAdapter.add(item);
                            mVideoIndex.put(item.getLocation(), i);
                        }
                    }
                });
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (!mMediaLibrary.isWorking())
                mProgressBar.setVisibility(View.GONE);
            if (askRefresh) { //in case new event occured while loading view
                mHandler.sendEmptyMessage(VideoListHandler.MEDIA_ITEMS_UPDATED);
            }
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
                    for (MediaWrapper MediaWrapper : videoList) {
                        picture = mediaDatabase.getPicture(MediaWrapper.getUri());
                        if (picture == null)
                            sThumbnailer.addJob(MediaWrapper);
                    }
                    if (sThumbnailer.getJobsCount() > 0)
                        sThumbnailer.start((IVideoBrowser) mContext);
                }
            }
        }).start();
    }

    public static Thumbnailer getThumbnailer() {
        return sThumbnailer;
    }

    protected void refresh() {
        mMediaLibrary.loadMediaItems(true);
    }
}
