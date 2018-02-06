/*****************************************************************************
 * MainTvActivity.java
 *****************************************************************************
 * Copyright © 2014-2016 VLC authors, VideoLAN and VideoLabs
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.MainThread;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.app.BrowseSupportFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.MediaUpdatedCb;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.ExternalMonitor;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.RecommendationsService;
import org.videolan.vlc.StartActivity;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.preferences.PreferencesFragment;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;
import org.videolan.vlc.gui.tv.browser.BaseTvActivity;
import org.videolan.vlc.gui.tv.browser.MusicFragment;
import org.videolan.vlc.gui.tv.browser.VerticalGridActivity;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.VLCInstance;

import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class MainTvActivity extends BaseTvActivity implements OnItemViewSelectedListener,
        OnItemViewClickedListener, OnClickListener, PlaybackService.Callback, MediaUpdatedCb {

    private static final int NUM_ITEMS_PREVIEW = 5;

    public static final long HEADER_VIDEO = 0;
    public static final long HEADER_CATEGORIES = 1;
    public static final long HEADER_HISTORY = 2;
    public static final long HEADER_NETWORK = 3;
    public static final long HEADER_DIRECTORIES = 4;
    public static final long HEADER_MISC = 5;
    public static final long HEADER_STREAM = 6;

    public static final long ID_SETTINGS = 0;
    public static final long ID_ABOUT = 1;
    public static final long ID_LICENCE = 2;

    private static final int ACTIVITY_RESULT_PREFERENCES = 1;

    public static final String BROWSER_TYPE = "browser_type";

    public static final String TAG = "VLC/MainTvActivity";

    protected BrowseSupportFragment mBrowseFragment;
    private ProgressBar mProgressBar;
    private ArrayObjectAdapter mRowsAdapter = null;
    private ArrayObjectAdapter mVideoAdapter, mCategoriesAdapter, mHistoryAdapter, mBrowserAdapter, mOtherAdapter;
    private final SimpleArrayMap<String, Integer> mVideoIndex = new SimpleArrayMap<>(), mHistoryIndex = new SimpleArrayMap<>();
    private Activity mContext;
    private Object mSelectedItem;
    private AsyncUpdate mUpdateTask;
    private CardPresenter.SimpleCard mNowPlayingCard;
    private BackgroundManager mBackgroundManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!VLCInstance.testCompatibleCPU(this)) {
            finish();
            return;
        }

        // Delay access permission dialog prompt to avoid background corruption
        if (!Permissions.canReadStorage(this))
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Permissions.checkReadStoragePermission(MainTvActivity.this, false);
                }
            }, 1000);

        mContext = this;
        setContentView(R.layout.tv_main);

        final android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        mBrowseFragment = (BrowseSupportFragment) fragmentManager.findFragmentById(
                R.id.browse_fragment);
        mProgressBar = findViewById(R.id.tv_main_progress);

        // Set display parameters for the BrowseFragment
        mBrowseFragment.setHeadersState(BrowseFragment.HEADERS_ENABLED);
        mBrowseFragment.setTitle(getString(R.string.app_name));
        mBrowseFragment.setBadgeDrawable(ContextCompat.getDrawable(this, R.drawable.icon));

        //Enable search feature only if we detect Google Play Services.
        if (AndroidDevices.hasPlayServices) {
            mBrowseFragment.setOnSearchClickedListener(this);
            // set search icon color
            mBrowseFragment.setSearchAffordanceColor(getResources().getColor(R.color.orange500));
        }

        mBrowseFragment.setBrandColor(ContextCompat.getColor(this, R.color.orange800));
        mBackgroundManager = BackgroundManager.getInstance(this);
        mBackgroundManager.setAutoReleaseOnStop(false);
        TvUtil.clearBackground(mBackgroundManager);
    }

    @Override
    public void onConnected(PlaybackService service) {
        super.onConnected(service);
        mService.addCallback(this);
        if (!mMediaLibrary.isInitiated()) return;
        /*
         * skip browser and show directly Audio Player if a song is playing
         */
        if ((mRowsAdapter == null || mRowsAdapter.size() == 0) && Permissions.canReadStorage(this))
            update();
        else {
            updateBrowsers();
            updateNowPlayingCard();
            if (mMediaLibrary.isInitiated()) {
                VLCApplication.runBackground(new Runnable() {
                    @Override
                    public void run() {
                        final MediaWrapper[] history = mMediaLibrary.lastMediaPlayed();
                        VLCApplication.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                updateHistory(history);
                            }
                        });
                    }
                });
            }
        }
    }

    @Override
    public void onDisconnected() {
        if (mService != null) mService.removeCallback(this);
        super.onDisconnected();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mBackgroundManager.isAttached()) mBackgroundManager.attach(getWindow());
        if (mSelectedItem != null) TvUtil.updateBackground(mBackgroundManager, mSelectedItem);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (AndroidDevices.isAndroidTv && !AndroidUtil.isOOrLater) startService(new Intent(this, RecommendationsService.class));
        TvUtil.releaseBackgroundManager(mBackgroundManager);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mService != null) mService.addCallback(this);
        if (mMediaLibrary.isInitiated()) setmedialibraryListeners();
        else setupMediaLibraryReceiver();
    }

    @Override
    protected void onPause() {
        if (mUpdateTask != null) mUpdateTask.cancel(true);
        super.onPause();
        if (mService != null) mService.removeCallback(this);
        mMediaLibrary.removeMediaUpdatedCb();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            switch (resultCode) {
                case PreferencesActivity.RESULT_RESCAN:
                    startService(new Intent(Constants.ACTION_RELOAD, null,this, MediaParsingService.class));;
                    break;
                case PreferencesActivity.RESULT_RESTART:
                case PreferencesActivity.RESULT_RESTART_APP:
                    Intent intent = getIntent();
                    intent.setClass(this, resultCode == PreferencesActivity.RESULT_RESTART_APP ? StartActivity.class : MainTvActivity.class);
                    finish();
                    startActivity(intent);
                    break;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_BUTTON_Y)
                && mSelectedItem instanceof MediaWrapper) {
            MediaWrapper media = (MediaWrapper) mSelectedItem;
            if (media.getType() != MediaWrapper.TYPE_DIR) return false;
            final Intent intent = new Intent(this, DetailsActivity.class);
            // pass the item information
            intent.putExtra("media", (MediaWrapper) mSelectedItem);
            intent.putExtra("item", new MediaItemDetails(media.getTitle(), media.getArtist(), media.getAlbum(), media.getLocation(), media.getArtworkURL()));
            startActivity(intent);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void update() {
        if (mUpdateTask == null || mUpdateTask.getStatus() == AsyncTask.Status.FINISHED) {
            mUpdateTask = new AsyncUpdate();
            mUpdateTask.execute();
        }
    }

    @Override
    public void onMediaUpdated(final MediaWrapper[] mediaList) {
        if (mVideoAdapter == null || mVideoAdapter.size() > NUM_ITEMS_PREVIEW) return;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (MediaWrapper media : mediaList) updateItem(media);
            }
        });
    }

    public void updateItem(MediaWrapper item) {
        if (item == null) return;
        if (mVideoAdapter != null) {
            if (mVideoIndex.containsKey(item.getLocation())) {
                mVideoAdapter.notifyArrayItemRangeChanged(mVideoIndex.get(item.getLocation()), 1);
            } else {
                int position = mVideoAdapter.size();
                mVideoAdapter.add(position, item);
                mVideoIndex.put(item.getLocation(), position);
            }
        }
        if (mHistoryAdapter != null) {
            if (mHistoryIndex.containsKey(item.getLocation())) {
                mHistoryAdapter.notifyArrayItemRangeChanged(mHistoryIndex.get(item.getLocation()), 1);
            }
        }
    }


    @Override
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        mSelectedItem = item;
        TvUtil.updateBackground(mBackgroundManager, item);
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        if (row.getId() == HEADER_CATEGORIES) {
            if (((CardPresenter.SimpleCard)item).getId() == MusicFragment.CATEGORY_NOW_PLAYING){ //NOW PLAYING CARD
                startActivity(new Intent(this, AudioPlayerActivity.class));
                return;
            }
            final CardPresenter.SimpleCard card = (CardPresenter.SimpleCard) item;
            final Intent intent = new Intent(mContext, VerticalGridActivity.class);
            intent.putExtra(BROWSER_TYPE, HEADER_CATEGORIES);
            intent.putExtra(MusicFragment.AUDIO_CATEGORY, card.getId());
            startActivity(intent);
        } else if (row.getId() == HEADER_MISC) {
            long id = ((CardPresenter.SimpleCard) item).getId();
            if (id == ID_SETTINGS) startActivityForResult(new Intent(this, org.videolan.vlc.gui.tv.preferences.PreferencesActivity.class), ACTIVITY_RESULT_PREFERENCES);
            else if (id == ID_ABOUT) startActivity(new Intent(this, org.videolan.vlc.gui.tv.AboutActivity.class));
            else if (id == ID_LICENCE) startActivity(new Intent(this, org.videolan.vlc.gui.tv.LicenceActivity.class));
        } else TvUtil.openMedia(mContext, item, row);
    }

    @Override
    public void onClick(View v) {
        startActivity(new Intent(mContext, SearchActivity.class));
    }

    @Override
    protected void onParsingServiceStarted() {
        mHandler.sendEmptyMessageDelayed(SHOW_LOADING, 300);
    }

    @Override
    protected void onParsingServiceProgress() {
        if (mProgressBar.getVisibility() == View.GONE) mHandler.sendEmptyMessage(SHOW_LOADING);
    }

    @Override
    protected void onParsingServiceFinished() {
        update();
    }

    private static final int SHOW_LOADING = 0;
    private static final int HIDE_LOADING = 1;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_LOADING:
                    mProgressBar.setVisibility(View.VISIBLE);
                    break;
                case HIDE_LOADING:
                    removeMessages(SHOW_LOADING);
                    mProgressBar.setVisibility(View.GONE);
                default:
                    super.handleMessage(msg);
            }
        }
    };

    private class AsyncUpdate extends AsyncTask<Void, Void, Void> {
        private boolean showHistory;
        private MediaWrapper[] history, videoList;

        AsyncUpdate() {}

        @Override
        protected void onPreExecute() {
            showHistory = mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true);
            mHandler.sendEmptyMessageDelayed(SHOW_LOADING, 300);
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (isCancelled()) return null;
            videoList = mMediaLibrary.getRecentVideos();
            if (showHistory && !isCancelled()) history = VLCApplication.getMLInstance().lastMediaPlayed();
            return null;
        }

        @Override
        protected void onCancelled() {
            mUpdateTask = null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mHandler.sendEmptyMessage(HIDE_LOADING);
            if (!isVisible()) return;
            if (mRowsAdapter != null) mRowsAdapter.clear();
            else mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
            mHistoryIndex.clear();
            //Video Section
            mVideoIndex.clear();
            mVideoAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            final HeaderItem videoHeader = new HeaderItem(HEADER_VIDEO, getString(R.string.video));
            // Empty item to launch grid activity
            mVideoAdapter.add(new CardPresenter.SimpleCard(0, "All videos", videoList.length+" "+getString(R.string.videos), R.drawable.ic_video_collection_big));
            // Update video section
            if (!Tools.isArrayEmpty(videoList)) {
                final int size = Math.min(NUM_ITEMS_PREVIEW, videoList.length);
                for (int i = 0; i < size; ++i) {
                    Tools.setMediaDescription(videoList[i]);
                    mVideoAdapter.add(videoList[i]);
                    mVideoIndex.put(videoList[i].getLocation(), i);
                }
            }
            mRowsAdapter.add(new ListRow(videoHeader, mVideoAdapter));

            //Music sections
            mCategoriesAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            final HeaderItem musicHeader = new HeaderItem(HEADER_CATEGORIES, getString(R.string.audio));
            updateNowPlayingCard();
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_ARTISTS, getString(R.string.artists), R.drawable.ic_artist_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_ALBUMS, getString(R.string.albums), R.drawable.ic_album_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_GENRES, getString(R.string.genres), R.drawable.ic_genre_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_SONGS, getString(R.string.songs), R.drawable.ic_song_big));
            mRowsAdapter.add(new ListRow(musicHeader, mCategoriesAdapter));

            //History
            if (showHistory && !Tools.isArrayEmpty(history)) updateHistory(history);

            //Browser section
            mBrowserAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            final HeaderItem browserHeader = new HeaderItem(HEADER_NETWORK, getString(R.string.browsing));
            updateBrowsers();
            mRowsAdapter.add(new ListRow(browserHeader, mBrowserAdapter));

            mOtherAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            final HeaderItem miscHeader = new HeaderItem(HEADER_MISC, getString(R.string.other));

            mOtherAdapter.add(new CardPresenter.SimpleCard(ID_SETTINGS, getString(R.string.preferences), R.drawable.ic_menu_preferences_big));
            mOtherAdapter.add(new CardPresenter.SimpleCard(ID_ABOUT, getString(R.string.about), getString(R.string.app_name_full)+" "+ BuildConfig.VERSION_NAME, R.drawable.ic_default_cone));
            mOtherAdapter.add(new CardPresenter.SimpleCard(ID_LICENCE, getString(R.string.licence), R.drawable.ic_default_cone));
            mRowsAdapter.add(new ListRow(miscHeader, mOtherAdapter));
            if (mBrowseFragment.getSelectedPosition() >= mRowsAdapter.size()) mBrowseFragment.setSelectedPosition(RecyclerView.NO_POSITION);
            mBrowseFragment.setAdapter(mRowsAdapter);
            mBrowseFragment.setSelectedPosition(Math.min(mBrowseFragment.getSelectedPosition(), mRowsAdapter.size()-1));

            // add a listener for selected items
            mBrowseFragment.setOnItemViewClickedListener(MainTvActivity.this);
            mBrowseFragment.setOnItemViewSelectedListener(MainTvActivity.this);
        }
    }

    @MainThread
    private void updateHistory(MediaWrapper[] history) {
        if (history == null) return;
        final boolean createAdapter = mHistoryAdapter == null;
        if (createAdapter) mHistoryAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
        else mHistoryAdapter.clear();
        if (!mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true)) return;
        for (int i = 0; i < history.length; ++i) {
            final MediaWrapper item = history[i];
            mHistoryAdapter.add(item);
            mHistoryIndex.put(item.getLocation(), i);
        }
        if (createAdapter) {
            final HeaderItem historyHeader = new HeaderItem(HEADER_HISTORY, getString(R.string.history));
            mRowsAdapter.add(Math.min(2, mRowsAdapter.size()), new ListRow(historyHeader, mHistoryAdapter));
        }
    }

    private void updateBrowsers() {
        if (mBrowserAdapter == null) return;
        mBrowserAdapter.clear();
        final List<MediaWrapper> directories = AndroidDevices.getMediaDirectoriesList();
        if (!AndroidDevices.showInternalStorage && !directories.isEmpty()) directories.remove(0);
        for (MediaWrapper directory : directories)
            mBrowserAdapter.add(new CardPresenter.SimpleCard(HEADER_DIRECTORIES, directory.getTitle(), R.drawable.ic_menu_folder_big, directory.getUri()));

        if (ExternalMonitor.isLan()) {
            try {
                final List<MediaWrapper> favs = MediaDatabase.getInstance().getAllNetworkFav();
                mBrowserAdapter.add(new CardPresenter.SimpleCard(HEADER_NETWORK, getString(R.string.network_browsing), R.drawable.ic_menu_network_big));
                mBrowserAdapter.add(new CardPresenter.SimpleCard(HEADER_STREAM, getString(R.string.open_mrl), R.drawable.ic_menu_stream_big));

                if (!favs.isEmpty()) {
                    for (MediaWrapper fav : favs) {
                        fav.setDescription(fav.getUri().getScheme());
                        mBrowserAdapter.add(fav);
                    }
                }
            } catch (Exception ignored) {} //SQLite can explode
        }
        mBrowserAdapter.notifyArrayItemRangeChanged(0, mBrowserAdapter.size());
    }

    protected void refresh() {
        mMediaLibrary.reload();
    }

    @Override
    public void onNetworkConnectionChanged(boolean connected) {
        updateBrowsers();
    }

    @Override
    public void updateProgress(){}

    @Override
    public
    void onMediaEvent(Media.Event event) {}

    @Override
    public
    void onMediaPlayerEvent(MediaPlayer.Event event){
        switch (event.type) {
            case MediaPlayer.Event.Opening:
                updateNowPlayingCard();
                break;
            case MediaPlayer.Event.Stopped:
                if (mNowPlayingCard != null)
                    mCategoriesAdapter.remove(mNowPlayingCard);
                break;
        }
    }

    public void updateNowPlayingCard () {
        if (mService == null) return;
        final boolean hasmedia = mService.hasMedia();
        final boolean canSwitch = mService.canSwitchToVideo();
        if ((!hasmedia || canSwitch) && mNowPlayingCard != null) {
            mCategoriesAdapter.removeItems(0, 1);
            mNowPlayingCard = null;
        } else if (hasmedia && !canSwitch){
            final MediaWrapper mw = mService.getCurrentMediaWrapper();
            final String display = MediaUtils.getMediaTitle(mw) + " - " + MediaUtils.getMediaReferenceArtist(MainTvActivity.this, mw);
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    final Bitmap cover = AudioUtil.readCoverBitmap(Uri.decode(mw.getArtworkMrl()), VLCApplication.getAppResources().getDimensionPixelSize(R.dimen.grid_card_thumb_width));
                    VLCApplication.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mNowPlayingCard == null) {
                                if (cover != null) mNowPlayingCard = new CardPresenter.SimpleCard(MusicFragment.CATEGORY_NOW_PLAYING, display, cover);
                                else mNowPlayingCard = new CardPresenter.SimpleCard(MusicFragment.CATEGORY_NOW_PLAYING, display, R.drawable.ic_default_cone);
                                mCategoriesAdapter.add(0, mNowPlayingCard);
                            } else {
                                mNowPlayingCard.setId(MusicFragment.CATEGORY_NOW_PLAYING);
                                mNowPlayingCard.setName(display);
                                if (cover != null) mNowPlayingCard.setImage(cover);
                                else mNowPlayingCard.setImageId(R.drawable.ic_default_cone);
                            }
                            mCategoriesAdapter.notifyArrayItemRangeChanged(0,1);
                        }
                    });
                }
            });

        }
    }

    private void setmedialibraryListeners() {
        mMediaLibrary.setMediaUpdatedCb(this, Medialibrary.FLAG_MEDIA_UPDATED_VIDEO);
    }

    private void setupMediaLibraryReceiver() {
        final BroadcastReceiver libraryReadyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LocalBroadcastManager.getInstance(MainTvActivity.this).unregisterReceiver(this);
                setmedialibraryListeners();
                update();
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(libraryReadyReceiver, new IntentFilter(VLCApplication.ACTION_MEDIALIBRARY_READY));
    }
}
