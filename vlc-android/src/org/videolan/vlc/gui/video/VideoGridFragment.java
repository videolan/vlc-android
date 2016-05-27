/*****************************************************************************
 * VideoListActivity.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.video;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.ArrayMap;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.interfaces.IVideoBrowser;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.media.Thumbnailer;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.gui.view.AutoFitRecyclerView;

import java.util.ArrayList;
import java.util.List;

public class VideoGridFragment extends MediaBrowserFragment implements ISortable, IVideoBrowser, SwipeRefreshLayout.OnRefreshListener {

    public final static String TAG = "VLC/VideoListFragment";

    public final static String KEY_GROUP = "key_group";

    protected LinearLayout mLayoutFlipperLoading;
    protected AutoFitRecyclerView mGridView;
    protected TextView mTextViewNomedia;
    protected View mViewNomedia;
    protected String mGroup;

    private VideoListAdapter mVideoAdapter;
    private Thumbnailer mThumbnailer;
    private VideoGridAnimator mAnimator;

    private MainActivity mMainActivity;

    /* All subclasses of Fragment must include a public empty constructor. */
    public VideoGridFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVideoAdapter = new VideoListAdapter(this);

        if (savedInstanceState != null)
            setGroup(savedInstanceState.getString(KEY_GROUP));
        /* Load the thumbnailer */
        FragmentActivity activity = getActivity();
        if (activity != null)
            mThumbnailer = new Thumbnailer();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){

        View v = inflater.inflate(R.layout.video_grid, container, false);

        // init the information for the scan (1/2)
        mLayoutFlipperLoading = (LinearLayout) v.findViewById(R.id.layout_flipper_loading);
        mTextViewNomedia = (TextView) v.findViewById(R.id.textview_nomedia);
        mViewNomedia = v.findViewById(android.R.id.empty);
        mGridView = (AutoFitRecyclerView) v.findViewById(android.R.id.list);
        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);

        mSwipeRefreshLayout.setColorSchemeResources(R.color.orange700);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mGridView.addOnScrollListener(mScrollListener);
        mGridView.setAdapter(mVideoAdapter);
        return v;
    }

    RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            int topRowVerticalPosition =
                    (recyclerView == null || recyclerView.getChildCount() == 0) ? 0 : recyclerView.getChildAt(0).getTop();
            mSwipeRefreshLayout.setEnabled(topRowVerticalPosition >= 0);
        }
    };

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registerForContextMenu(mGridView);

        // init the information for the scan (2/2)
        IntentFilter filter = new IntentFilter();
        filter.addAction(MediaUtils.ACTION_SCAN_START);
        filter.addAction(MediaUtils.ACTION_SCAN_STOP);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(messageReceiverVideoListFragment, filter);
        if (mMediaLibrary.isWorking()) {
            MediaUtils.actionScanStart();
        }

        mAnimator = new VideoGridAnimator(mGridView);
    }

    @Override
    public void onPause() {
        super.onPause();
        mMediaLibrary.setBrowser(null);
        mMediaLibrary.removeUpdateHandler(mHandler);

        /* Stop the thumbnailer */
        if (mThumbnailer != null)
            mThumbnailer.stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if ((getActivity() instanceof MainActivity))
            mMainActivity = (MainActivity) getActivity();
        mMediaLibrary.setBrowser(this);
        mMediaLibrary.addUpdateHandler(mHandler);
        final boolean refresh = mVideoAdapter.isEmpty() && !mMediaLibrary.isWorking();
        // We don't animate while medialib is scanning. Because gridview is being populated.
        // That would lead to graphical glitches
        final boolean animate = mGroup == null && refresh;
        if (refresh)
            updateList();
        else {
            mViewNomedia.setVisibility(View.GONE);
        }
        //Get & set times
        ArrayMap<String, Long> times = MediaDatabase.getInstance().getVideoTimes();
        mVideoAdapter.setTimes(times);
        updateViewMode();
        if (animate)
            mAnimator.animate();

        /* Start the thumbnailer */
        if (mThumbnailer != null)
            mThumbnailer.start(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_GROUP, mGroup);
    }

    @Override
    public void onDestroyView() {
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(messageReceiverVideoListFragment);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mThumbnailer != null)
            mThumbnailer.clearJobs();
        mVideoAdapter.clear();
    }

    protected String getTitle(){
        if (mGroup == null)
            return getString(R.string.video);
        else
            return mGroup + "\u2026";
    }

    private void updateViewMode() {
        if (getView() == null || getActivity() == null) {
            Log.w(TAG, "Unable to setup the view");
            return;
        }
        Resources res = getResources();
        boolean listMode = res.getBoolean(R.bool.list_mode);
        listMode |= res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT &&
                PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("force_list_portrait", false);
        // Compute the left/right padding dynamically
        DisplayMetrics outMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(outMetrics);

        // Select between grid or list
        if (!listMode) {
            mGridView.setNumColumns(-1);
            int thumbnailWidth = res.getDimensionPixelSize(R.dimen.grid_card_thumb_width);
            mGridView.setColumnWidth(mGridView.getPerfectColumnWidth(thumbnailWidth, res.getDimensionPixelSize(R.dimen.default_margin)));
        } else {
            mGridView.setNumColumns(1);
        }
        mVideoAdapter.setListMode(listMode);
    }

    protected void playVideo(MediaWrapper media, boolean fromStart) {
        media.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
        VideoPlayerActivity.start(getActivity(), media.getUri(), fromStart);
    }

    protected void playAudio(MediaWrapper media) {
        if (mService != null) {
            media.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
            mService.load(media);
        }
    }

    private boolean handleContextItemSelected(MenuItem menu, final int position) {
        if (position >= mVideoAdapter.getItemCount())
            return false;
        final MediaWrapper media = mVideoAdapter.getItem(position);
        if (media == null)
            return false;
        switch (menu.getItemId()){
            case R.id.video_list_play_from_start:
                playVideo(media, true);
                return true;
            case R.id.video_list_play_audio:
                playAudio(media);
                return true;
            case R.id.video_list_play_all:
                ArrayList<MediaWrapper> playList = new ArrayList<>();
                ArrayList<MediaWrapper> videos = mVideoAdapter.getAll();
                MediaWrapper mw;
                int offset = 0;
                for (int i = 0; i < videos.size(); ++i) {
                    mw = videos.get(i);
                    if (mw instanceof MediaGroup) {
                        for (MediaWrapper item : ((MediaGroup) mw).getAll())
                            playList.add(item);
                        if (i < position)
                            offset += ((MediaGroup)mw).size()-1;
                    } else
                        playList.add(mw);
                }
                MediaUtils.openList(getActivity(), playList, position+offset);
                return true;
            case R.id.video_list_info:
                Activity activity = getActivity();
                if (activity instanceof MainActivity)
                    ((MainActivity)activity).showSecondaryFragment(SecondaryActivity.MEDIA_INFO, media.getLocation());
                else {
                    Intent i = new Intent(activity, SecondaryActivity.class);
                    i.putExtra("fragment", "mediaInfo");
                    i.putExtra("param", media.getLocation());
                    startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY);
                }
                return true;
            case R.id.video_list_delete:
                mVideoAdapter.remove(position);
                if (getView() != null)
                    UiTools.snackerWithCancel(getView(), getString(R.string.file_deleted), new Runnable() {
                        @Override
                        public void run() {
                            deleteMedia(media);
                        }
                    }, new Runnable() {
                        @Override
                        public void run() {
                            mVideoAdapter.add(position, media);
                        }
                    });
                return true;
            case R.id.video_group_play:
                MediaUtils.openList(getActivity(), ((MediaGroup) media).getAll(), 0);
                return true;
            case R.id.video_list_append:
                if (media instanceof MediaGroup)
                    mService.append(((MediaGroup)media).getAll());
                else
                    mService.append(media);
                return true;
            case R.id.video_download_subtitles:
                MediaUtils.getSubs(getActivity(), media);
                return true;
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo == null)
            return;
        // Do not show the menu of media group.
        ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView.RecyclerContextMenuInfo)menuInfo;
        MediaWrapper media = mVideoAdapter.getItem(info.position);
        if (media == null)
            return;
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(media instanceof MediaGroup ? R.menu.video_group_contextual : R.menu.video_list, menu);
        if (media instanceof MediaGroup) {
            if (!AndroidUtil.isHoneycombOrLater()) {
                menu.findItem(R.id.video_list_append).setVisible(false);
                menu.findItem(R.id.video_group_play).setVisible(false);
            }
        } else
            setContextMenuItems(menu, media);
    }

    private void setContextMenuItems(Menu menu, MediaWrapper mediaWrapper) {
        long lastTime = mediaWrapper.getTime();
        if (lastTime > 0)
            menu.findItem(R.id.video_list_play_from_start).setVisible(true);

        boolean hasInfo = false;
        final Media media = new Media(VLCInstance.get(), mediaWrapper.getUri());
        media.parse();
        boolean canWrite = FileUtils.canWrite(mediaWrapper.getLocation());
        if (media.getMeta(Media.Meta.Title) != null)
            hasInfo = true;
        media.release();
        menu.findItem(R.id.video_list_info).setVisible(hasInfo);
        menu.findItem(R.id.video_list_delete).setVisible(canWrite);
        menu.findItem(R.id.video_download_subtitles).setVisible(canWrite);
        if (!AndroidUtil.isHoneycombOrLater()) {
            menu.findItem(R.id.video_list_play_all).setVisible(false);
            menu.findItem(R.id.video_list_append).setVisible(false);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem menu) {
        ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView.RecyclerContextMenuInfo) menu.getMenuInfo();
        return info != null && handleContextItemSelected(menu, info.position);
    }

    /**
     * Handle changes on the list
     */
    private Handler mHandler = new VideoListHandler(this);

    public void updateItem(MediaWrapper item) {
        if (item.getType() != MediaWrapper.TYPE_VIDEO)
            return;
        mVideoAdapter.update(item);
        mViewNomedia.setVisibility(View.GONE);
    }

    public void updateList() {
        if (!mSwipeRefreshLayout.isRefreshing())
            mSwipeRefreshLayout.setRefreshing(true);
        final List<MediaWrapper> itemList = mMediaLibrary.getVideoItems();

        if (mThumbnailer != null)
            mThumbnailer.clearJobs();
        else
            Log.w(TAG, "Can't generate thumbnails, the thumbnailer is missing");

        if (itemList.size() > 0) {
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    final ArrayList<MediaWrapper> displayList = new ArrayList<>();
                    final ArrayList<MediaWrapper> jobsList = new ArrayList<>();
                    if (mGroup != null || itemList.size() <= 10) {
                        for (MediaWrapper item : itemList) {
                            if (mGroup == null || item.getTitle().startsWith(mGroup))
                                displayList.add(item);
                                jobsList.add(item);
                        }
                    } else {
                        List<MediaGroup> groups = MediaGroup.group(itemList);
                        for (MediaGroup item : groups) {
                            displayList.add(item.getMedia());
                            for (MediaWrapper media : item.getAll())
                                jobsList.add(media);
                        }
                    }

                    mVideoAdapter.addAll(displayList);
                    mVideoAdapter.sort();
                    if (mReadyToDisplay)
                        display();
                    if (mThumbnailer != null && !jobsList.isEmpty()) {
                        for (MediaWrapper item : jobsList)
                            mThumbnailer.addJob(item);
                    }
                }
            });
        }
        stopRefresh();
    }

    @Override
    public void showProgressBar() {
        if (mMainActivity != null)
            mMainActivity.showProgressBar();
    }

    @Override
    public void hideProgressBar() {
        if (mMainActivity != null)
            mMainActivity.hideProgressBar();
    }

    @Override
    public void clearTextInfo() {
        if (mMainActivity != null)
            mMainActivity.clearTextInfo();
    }

    @Override
    public void sendTextInfo(String info, int progress, int max) {
        if (mMainActivity != null)
            mMainActivity.sendTextInfo(info, progress, max);
    }

    @Override
    public void sortBy(int sortby) {
        mVideoAdapter.sortBy(sortby);
    }

    @Override
    public int sortDirection(int sortby) {
        return mVideoAdapter.sortDirection(sortby);
    }

    public void setItemToUpdate(MediaWrapper item) {
        if (mVideoAdapter.getAll().contains(item))
            mHandler.sendMessage(mHandler.obtainMessage(MediaLibrary.UPDATE_ITEM, item));
    }

    public void setGroup(String prefix) {
        mGroup = prefix;
    }

    private final BroadcastReceiver messageReceiverVideoListFragment = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(MediaUtils.ACTION_SCAN_START)) {
                mLayoutFlipperLoading.setVisibility(View.VISIBLE);
                mTextViewNomedia.setVisibility(View.INVISIBLE);
            } else if (action.equalsIgnoreCase(MediaUtils.ACTION_SCAN_STOP)) {
                mLayoutFlipperLoading.setVisibility(View.INVISIBLE);
                mTextViewNomedia.setVisibility(View.VISIBLE);
            }
        }
    };

    public void stopRefresh() {
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onRefresh() {
        if (getActivity()!=null && !MediaLibrary.getInstance().isWorking())
            MediaLibrary.getInstance().scanMediaItems(true);
    }

    @Override
    public void display() {
        if (getActivity() != null)
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mVideoAdapter.notifyDataSetChanged();
                    mViewNomedia.setVisibility(mVideoAdapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
                    mReadyToDisplay = true;
                    mGridView.requestFocus();
                }
            });
    }

    public void clear(){
        mVideoAdapter.clear();
    }

    public void deleteMedia(final MediaWrapper media){
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                FileUtils.deleteFile(media.getUri().getPath());
                MediaDatabase.getInstance().removeMedia(media.getUri());
            }
        });
        mMediaLibrary.getMediaItems().remove(media);
        if (mService != null) {
            final List<String> list = mService.getMediaLocations();
            if (list != null && list.contains(media.getLocation())) {
                mService.removeLocation(media.getLocation());
            }
        }
    }
}
