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
import android.app.FragmentManager;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.support.v4.content.ContextCompat;
import android.support.v4.util.SimpleArrayMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.MediaUpdatedCb;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BuildConfig;
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
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.VLCInstance;

import java.util.ArrayList;
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

    public static final long ID_SETTINGS = 0;
    public static final long ID_ABOUT = 1;
    public static final long ID_LICENCE = 2;

    private static final int ACTIVITY_RESULT_PREFERENCES = 1;

    public static final String BROWSER_TYPE = "browser_type";

    public static final String TAG = "VLC/MainTvActivity";

    private Handler mHandler = new Handler();
    protected BrowseFragment mBrowseFragment;
    private ProgressBar mProgressBar;
    private ArrayObjectAdapter mRowsAdapter = null;
    private ArrayObjectAdapter mVideoAdapter, mCategoriesAdapter, mHistoryAdapter, mBrowserAdapter, mOtherAdapter;
    private View mRootContainer;
    private final SimpleArrayMap<String, Integer> mVideoIndex = new SimpleArrayMap<>(), mHistoryIndex = new SimpleArrayMap<>();
    private Drawable mDefaultBackground;
    private Activity mContext;
    private Object mSelectedItem;
    private AsyncUpdate mUpdateTask;
    private CardPresenter.SimpleCard mNowPlayingCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mSettings.getBoolean(PreferencesActivity.AUTO_RESCAN, true) && Permissions.canReadStorage())
            mMediaLibrary.reload();

        if (!VLCInstance.testCompatibleCPU(this)) {
            finish();
            return;
        }

        Permissions.checkReadStoragePermission(this, false);

        mContext = this;
        setContentView(R.layout.tv_main);

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

        //Enable search feature only if we detect Google Play Services.
        if (AndroidDevices.hasPlayServices()) {
            mBrowseFragment.setOnSearchClickedListener(this);
            // set search icon color
            mBrowseFragment.setSearchAffordanceColor(getResources().getColor(R.color.orange500));
        }

        mRootContainer = mBrowseFragment.getView();
        mBrowseFragment.setBrandColor(ContextCompat.getColor(this, R.color.orange800));
    }

    @Override
    public void onConnected(PlaybackService service) {
        super.onConnected(service);
        mService.addCallback(this);
        /*
         * skip browser and show directly Audio Player if a song is playing
         */
        if ((mRowsAdapter == null || mRowsAdapter.size() == 0) && Permissions.canReadStorage())
            update();
        else {
            updateBrowsers();
            updateNowPlayingCard();
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    final MediaWrapper[] history = VLCApplication.getMLInstance().lastMediaPlayed();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateHistory(history);
                        }
                    });
                }
            });
        }
    }

    @Override
    public void onDisconnected() {
        mService.removeCallback(this);
        super.onDisconnected();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (AndroidDevices.isAndroidTv()) {
            Intent recommendationIntent = new Intent(this,
                    RecommendationsService.class);
            startService(recommendationIntent);
        }
    }

    protected void onResume() {
        super.onResume();
        if (mService != null)
            mService.addCallback(this);
        mMediaLibrary.setMediaUpdatedCb(this, Medialibrary.FLAG_MEDIA_UPDATED_VIDEO);
    }

    protected void onPause() {
        super.onPause();
        if (mService != null)
            mService.removeCallback(this);
        mMediaLibrary.removeMediaUpdatedCb();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Permissions.PERMISSION_STORAGE_TAG: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mMediaLibrary.init(this);
                    ((VLCApplication) VLCApplication.getAppContext()).discoverStorages(mMediaLibrary);
                } else {
                    Permissions.showStoragePermissionDialog(this, false);
                }
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            if (resultCode == PreferencesActivity.RESULT_RESCAN) {
                VLCApplication.getMLInstance().reload();
                update();
            } else if (resultCode == PreferencesActivity.RESULT_RESTART) {
                Intent intent = getIntent();
                intent.setClass(this, StartActivity.class);
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
            intent.putExtra("item", new MediaItemDetails(media.getTitle(), media.getArtist(), media.getAlbum(), media.getLocation(), media.getArtworkURL()));
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

    public void update() {
        if (mUpdateTask == null || mUpdateTask.getStatus() == AsyncTask.Status.FINISHED) {
            mUpdateTask = new AsyncUpdate();
            mUpdateTask.execute();
        } else {
            mUpdateTask.AskRefresh();
        }
    }

    @Override
    public void onMediaUpdated(final MediaWrapper[] mediaList) {
        if (mVideoAdapter.size() > NUM_ITEMS_PREVIEW)
            return;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (MediaWrapper media : mediaList)
                    if (media != null)
                        updateItem(media);
            }
        });
    }

    public void updateItem(MediaWrapper item) {
        if (mVideoAdapter != null && item != null) {
            if (mVideoIndex.containsKey(item.getLocation())) {
                mVideoAdapter.notifyArrayItemRangeChanged(mVideoIndex.get(item.getLocation()), 1);
            } else {
                int position = mVideoAdapter.size();
                mVideoAdapter.add(position, item);
                mVideoIndex.put(item.getLocation(), position);
            }
        }
        if (mHistoryAdapter != null && item != null) {
            if (mHistoryIndex.containsKey(item.getLocation())) {
                mHistoryAdapter.notifyArrayItemRangeChanged(mHistoryIndex.get(item.getLocation()), 1);
            }
        }
    }


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
        } else if (row.getId() == HEADER_MISC) {
            long id = ((CardPresenter.SimpleCard) item).getId();
            if (id == ID_SETTINGS)
                startActivityForResult(new Intent(this, org.videolan.vlc.gui.tv.preferences.PreferencesActivity.class), ACTIVITY_RESULT_PREFERENCES);
            else if (id == ID_ABOUT)
                startActivity(new Intent(this, org.videolan.vlc.gui.tv.AboutActivity.class));
            else if (id == ID_LICENCE)
                startActivity(new Intent(this, org.videolan.vlc.gui.tv.LicenceActivity.class));
        } else {
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
        boolean showHistory;
        MediaWrapper[] history, videoList;

        AsyncUpdate() {
        }

        void AskRefresh() { //Ask for refresh while update is ongoing
            askRefresh = true;
        }

        @Override
        protected void onPreExecute() {

            showHistory = mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true);
            if (mRowsAdapter != null)
                mRowsAdapter.clear();
            mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
            mProgressBar.setVisibility(View.VISIBLE);
            mHistoryIndex.clear();

            //Video Section
            mVideoIndex.clear();
        }

        @Override
        protected Void doInBackground(Void... params) {
            videoList = mMediaLibrary.getVideos();
            if (showHistory)
                history = VLCApplication.getMLInstance().lastMediaPlayed();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mVideoAdapter = new ArrayObjectAdapter(
                    new CardPresenter(mContext));
            final HeaderItem videoHeader = new HeaderItem(HEADER_VIDEO, getString(R.string.video));
            // Empty item to launch grid activity
            mVideoAdapter.add(new CardPresenter.SimpleCard(0, "All videos", videoList.length+" "+getString(R.string.videos), R.drawable.ic_video_collection_big));
            // Update video section
            if (!Tools.isArrayEmpty(videoList)) {
                final int size = Math.min(NUM_ITEMS_PREVIEW, videoList.length);
                mRootContainer.post(new Runnable() {
                    @Override
                    public void run() {
                        MediaWrapper item;
                        for (int i = 0; i < size; ++i) {
                            item = videoList[i];
                            Tools.setMediaDescription(item);
                            mVideoAdapter.add(item);
                            mVideoIndex.put(item.getLocation(), i);
                        }
                    }
                });
                mVideoAdapter.notifyArrayItemRangeChanged(0, size);
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
            if (showHistory && !Tools.isArrayEmpty(history)){
                mHistoryAdapter = new ArrayObjectAdapter(
                        new CardPresenter(mContext));
                final HeaderItem historyHeader = new HeaderItem(HEADER_HISTORY, getString(R.string.history));
                updateHistory(history);
                mRowsAdapter.add(new ListRow(historyHeader, mHistoryAdapter));
            }

            //Browser section
            mBrowserAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            final HeaderItem browserHeader = new HeaderItem(HEADER_NETWORK, getString(R.string.browsing));
            updateBrowsers();
            mRowsAdapter.add(new ListRow(browserHeader, mBrowserAdapter));

            mOtherAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            final HeaderItem miscHeader = new HeaderItem(HEADER_MISC, getString(R.string.other));

            mOtherAdapter.add(new CardPresenter.SimpleCard(ID_SETTINGS, getString(R.string.preferences), R.drawable.ic_menu_preferences_big));
            mOtherAdapter.add(new CardPresenter.SimpleCard(ID_ABOUT, getString(R.string.about), getString(R.string.app_name_full)+" "+ BuildConfig.VERSION_NAME, R.drawable.ic_tv_icon_small));
            mOtherAdapter.add(new CardPresenter.SimpleCard(ID_LICENCE, getString(R.string.licence), R.drawable.ic_tv_icon_small));
            mRowsAdapter.add(new ListRow(miscHeader, mOtherAdapter));
            mBrowseFragment.setAdapter(mRowsAdapter);

            mProgressBar.setVisibility(View.GONE);
            if (askRefresh) { //in case new event occurred while loading view
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                });
            }
        }
    }

    private void updateHistory(MediaWrapper[] history) {
        if (mHistoryAdapter == null || history == null)
            return;
        mHistoryAdapter.clear();
        if (!mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true))
            return;
        MediaWrapper item;
        for (int i = 0; i < history.length; ++i) {
            item = history[i];
            mHistoryAdapter.add(item);
            mHistoryIndex.put(item.getLocation(), i);
        }
    }

    private void updateBrowsers() {
        if (mBrowserAdapter == null)
            return;
        mBrowserAdapter.clear();
        List<MediaWrapper> directories = AndroidDevices.getMediaDirectoriesList();
        if (!AndroidDevices.showInternalStorage())
            directories.remove(0);
        for (MediaWrapper directory : directories)
            mBrowserAdapter.add(new CardPresenter.SimpleCard(HEADER_DIRECTORIES, directory.getTitle(), R.drawable.ic_menu_folder_big, directory.getUri()));

        if (AndroidDevices.hasLANConnection()) {
            final ArrayList<MediaWrapper> favs = MediaDatabase.getInstance().getAllNetworkFav();
            mBrowserAdapter.add(new CardPresenter.SimpleCard(HEADER_NETWORK, getString(R.string.network_browsing), R.drawable.ic_menu_network_big));

            if (!favs.isEmpty()) {
                for (MediaWrapper fav : favs) {
                    fav.setDescription(fav.getUri().getScheme());
                    mBrowserAdapter.add(fav);
                }
            }
        }
        mBrowserAdapter.notifyArrayItemRangeChanged(0, mBrowserAdapter.size());
    }

    protected void refresh() {
        mMediaLibrary.reload();
    }

    @Override
    protected void onNetworkUpdated() {
        updateBrowsers();
    }

    @Override
    protected void onExternelDeviceChange() {
        updateBrowsers();
    }

    @Override
    public void updateProgress(){}

    @Override
    public
    void onMediaEvent(Media.Event event) {
    }

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
        if (mService == null)
            return;
        if ((!mService.hasMedia() || mService.canSwitchToVideo()) && mNowPlayingCard != null) {
            mCategoriesAdapter.removeItems(0, 1);
            mNowPlayingCard = null;
        } else  if (mService.hasMedia()){
            MediaWrapper mw = mService.getCurrentMediaWrapper();
            String display = MediaUtils.getMediaTitle(mw) + " - " + MediaUtils.getMediaReferenceArtist(MainTvActivity.this, mw);
            Bitmap cover = AudioUtil.getCover(MainTvActivity.this, mw, VLCApplication.getAppResources().getDimensionPixelSize(R.dimen.grid_card_thumb_width));
            if (mNowPlayingCard == null) {
                if (cover != null)
                    mNowPlayingCard = new CardPresenter.SimpleCard(MusicFragment.CATEGORY_NOW_PLAYING, display, cover);
                else
                    mNowPlayingCard = new CardPresenter.SimpleCard(MusicFragment.CATEGORY_NOW_PLAYING, display, R.drawable.ic_tv_icon_small);
                mCategoriesAdapter.add(0, mNowPlayingCard);
            } else {
                mNowPlayingCard.setId(MusicFragment.CATEGORY_NOW_PLAYING);
                mNowPlayingCard.setName(display);
                if (cover != null)
                    mNowPlayingCard.setImage(cover);
                else
                    mNowPlayingCard.setImageId(R.drawable.ic_tv_icon_small);
            }
            mCategoriesAdapter.notifyArrayItemRangeChanged(0,1);

        }
    }

}
