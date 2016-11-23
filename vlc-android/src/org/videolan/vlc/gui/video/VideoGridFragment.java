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

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.view.ActionMode;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.medialibrary.interfaces.MediaAddedCb;
import org.videolan.medialibrary.interfaces.MediaUpdatedCb;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.AutoFitRecyclerView;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.DividerItemDecoration;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.Filterable;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.VLCInstance;

import java.util.ArrayList;
import java.util.List;

public class VideoGridFragment extends MediaBrowserFragment implements MediaUpdatedCb, ISortable, SwipeRefreshLayout.OnRefreshListener, DevicesDiscoveryCb, MediaAddedCb, Filterable {

    public final static String TAG = "VLC/VideoListFragment";

    public final static String KEY_GROUP = "key_group";

    protected LinearLayout mLayoutFlipperLoading;
    protected AutoFitRecyclerView mGridView;
    protected TextView mTextViewNomedia;
    protected View mViewNomedia;
    protected String mGroup;
    private View mSearchButtonView;

    private Handler mHandler = new Handler();
    private VideoListAdapter mVideoAdapter;
    private VideoGridAnimator mAnimator;
    private DividerItemDecoration mDividerItemDecoration;

    /* All subclasses of Fragment must include a public empty constructor. */
    public VideoGridFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVideoAdapter = new VideoListAdapter(this);

        if (savedInstanceState != null)
            setGroup(savedInstanceState.getString(KEY_GROUP));
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
        mSearchButtonView = v.findViewById(R.id.searchButton);

        mSwipeRefreshLayout.setOnRefreshListener(this);

        mDividerItemDecoration = new DividerItemDecoration(v.getContext(), DividerItemDecoration.VERTICAL_LIST);
        mGridView.setAdapter(mVideoAdapter);
        return v;
    }

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
    public void onResume() {
        super.onResume();
        setSearchVisibility(false);
        mMediaLibrary.setMediaUpdatedCb(this, Medialibrary.FLAG_MEDIA_UPDATED_VIDEO);
        mMediaLibrary.setMediaAddedCb(this, Medialibrary.FLAG_MEDIA_ADDED_VIDEO);
        final boolean isWorking = mMediaLibrary.isWorking();
        mMediaLibrary.addDeviceDiscoveryCb(this);
        final boolean refresh = mVideoAdapter.isEmpty();
        // We don't animate while medialib is scanning. Because gridview is being populated.
        // That would lead to graphical glitches
        final boolean animate = mGroup == null && refresh && !mMediaLibrary.isWorking();
        if (refresh)
            updateList();
        else {
            mViewNomedia.setVisibility(mVideoAdapter.isEmpty() ? View.VISIBLE : View.GONE);
            if (!isWorking)
                updateTimes();
        }

        updateViewMode();
        if (animate)
            mAnimator.animate();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMediaLibrary.removeDeviceDiscoveryCb(this);
        mMediaLibrary.removeMediaUpdatedCb();
        mMediaLibrary.removeMediaAddedCb();
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
        mVideoAdapter.clear();
    }

    protected String getTitle(){
        if (mGroup == null)
            return getString(R.string.video);
        else
            return mGroup + "\u2026";
    }

    private void updateTimes() {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                MediaWrapper[] videos = mMediaLibrary.getVideos();
                final SimpleArrayMap<Long, Long> times = new SimpleArrayMap<>(videos.length);
                for (MediaWrapper mw : videos)
                    times.put(mw.getId(), mw.getTime());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mVideoAdapter.setTimes(times);
                    }
                });
            }
        });
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
            mVideoAdapter.setGridCardWidth(mGridView.getColumnWidth());
            mGridView.removeItemDecoration(mDividerItemDecoration);
        } else {
            mGridView.setNumColumns(1);
            mGridView.addItemDecoration(mDividerItemDecoration);
        }
        if (mVideoAdapter.isListMode() != listMode)
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

    protected boolean handleContextItemSelected(MenuItem menu, final int position) {
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
                showInfoDialog(media);
                return true;
            case R.id.video_list_delete:
                removeVideo(position, media);
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

    private void removeVideo(int position, final MediaWrapper media) {
        mVideoAdapter.remove(position);
        if (getView() != null)
            UiTools.snackerWithCancel(getView(), getString(R.string.file_deleted), new Runnable() {
                @Override
                public void run() {
                    deleteMedia(media, false);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    mVideoAdapter.add(media);
                }
            });
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
        if (!AndroidUtil.isHoneycombOrLater()) {
            menu.findItem(R.id.video_list_play_all).setVisible(false);
            menu.findItem(R.id.video_list_append).setVisible(false);
        }
    }

    @Override
    public void onMediaUpdated(final MediaWrapper[] mediaList) {
        updateItems(mediaList);
    }

    @Override
    public void onMediaAdded(final MediaWrapper[] mediaList) {
        updateItems(mediaList);
    }

    public void updateItems(final MediaWrapper[] mediaList) {
        for (final MediaWrapper mw : mediaList)
            if (mw != null && mw.getType() == MediaWrapper.TYPE_VIDEO)
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mVideoAdapter.update(mw);
                        mViewNomedia.setVisibility(mVideoAdapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
                    }
                });
    }

    @MainThread
    public void updateList() {
        if (!mSwipeRefreshLayout.isRefreshing())
            mSwipeRefreshLayout.setRefreshing(true);

        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final MediaWrapper[] itemList = mMediaLibrary.getVideos();
                final ArrayList<MediaWrapper> displayList = new ArrayList<>();
                if (mGroup != null || itemList.length <= 10) {
                    for (MediaWrapper item : itemList) {
                        String title = item.getTitle().substring(item.getTitle().toLowerCase().startsWith("the") ? 4 : 0);
                        if (mGroup == null || title.toLowerCase().startsWith(mGroup.toLowerCase()))
                            displayList.add(item);
                    }
                } else {
                    for (MediaGroup item : MediaGroup.group(itemList))
                        displayList.add(item.getMedia());
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mVideoAdapter.clear();
                        mVideoAdapter.addAll(displayList);
                        if (mReadyToDisplay)
                            display();
                    }
                });
            }
        });
    }

    @Override
    public void sortBy(int sortby) {
        mVideoAdapter.sortBy(sortby);
    }

    @Override
    public int sortDirection(int sortby) {
        return mVideoAdapter.sortDirection(sortby);
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
            } else if (action.equalsIgnoreCase(MediaUtils.ACTION_SCAN_STOP)) {
                mLayoutFlipperLoading.setVisibility(View.INVISIBLE);
            }
        }
    };

    public void stopRefresh() {
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onRefresh() {
        if (!VLCApplication.getMLInstance().isWorking())
           updateList();
        else
            mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void display() {
        if (getActivity() != null)
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopRefresh();
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

    boolean mParsing = false;
    @Override
    public void onDiscoveryStarted(String entryPoint) {}

    @Override
    public void onDiscoveryProgress(String entryPoint) {}

    @Override
    public void onDiscoveryCompleted(String entryPoint) {
        if (!mParsing && mSwipeRefreshLayout.isRefreshing())
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateList();
                }
            });
    }

    @Override
    public void onParsingStatsUpdated(int percent) {
        mParsing = percent < 100;
        if (percent == 100)
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateList();
                }
            });
        else if (!mSwipeRefreshLayout.isRefreshing())
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mSwipeRefreshLayout.setRefreshing(true);
                }
            });
    }

    @Override
    public boolean enableSearchOption() {
        return true;
    }

    @Override
    public Filter getFilter() {
        return mVideoAdapter.getFilter();
    }

    @Override
    public void restoreList() {
        mVideoAdapter.restoreList();
    }

    @Override
    public void setSearchVisibility(boolean visible) {
        mSearchButtonView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.action_mode_video, menu);
        mVideoAdapter.setActionMode(true);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        if (mVideoAdapter.getSelectedPositions().isEmpty()) {
            stopActionMode();
            return false;
        }
        boolean honeComb = AndroidUtil.isHoneycombOrLater();
        int count = mVideoAdapter.getSelectedPositions().size();
        menu.findItem(R.id.action_video_info).setVisible(count == 1);
        menu.findItem(R.id.action_video_play).setVisible(honeComb || count == 1);
        menu.findItem(R.id.action_video_append).setVisible(honeComb);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_video_play:
                MediaUtils.openList(getActivity(), mVideoAdapter.getSelection(), 0);
                break;
            case R.id.action_video_append:
                MediaUtils.appendMedia(getActivity(), mVideoAdapter.getSelection());
                break;
            case R.id.action_video_info:
                showInfoDialog(mVideoAdapter.getSelection().get(0));
                break;
            case R.id.action_video_delete:
                for (int position : mVideoAdapter.getSelectedPositions())
                    removeVideo(position, mVideoAdapter.getItem(position));
                break;
            case R.id.action_video_download_subtitles:
                MediaUtils.getSubs(getActivity(), mVideoAdapter.getSelection());
                break;
            case R.id.action_video_play_audio:
                List<MediaWrapper> list = mVideoAdapter.getSelection();
                for (MediaWrapper media : list)
                    media.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                MediaUtils.openList(getActivity(), list, 0);
                break;
            default:
                stopActionMode();
                return false;
        }
        stopActionMode();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mVideoAdapter.setActionMode(false);
    }
}
