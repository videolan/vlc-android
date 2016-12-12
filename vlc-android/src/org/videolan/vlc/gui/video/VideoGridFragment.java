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
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.view.ActionMode;
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
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.medialibrary.interfaces.MediaAddedCb;
import org.videolan.medialibrary.interfaces.MediaUpdatedCb;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.AutoFitRecyclerView;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.DividerItemDecoration;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.Filterable;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.VLCInstance;

import java.util.ArrayList;
import java.util.List;

public class VideoGridFragment extends MediaBrowserFragment implements MediaUpdatedCb, ISortable, SwipeRefreshLayout.OnRefreshListener, DevicesDiscoveryCb, MediaAddedCb, Filterable, IEventsHandler {

    public final static String TAG = "VLC/VideoListFragment";

    public final static String KEY_GROUP = "key_group";

    protected LinearLayout mLayoutFlipperLoading;
    protected AutoFitRecyclerView mGridView;
    protected TextView mTextViewNomedia;
    protected View mViewNomedia;
    protected String mGroup;
    private View mSearchButtonView;
    private VideoListAdapter mVideoAdapter;
    private DividerItemDecoration mDividerItemDecoration;

    private FloatingActionButton mFabPlayAll;

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
    }


    public void onStart() {
        super.onStart();
        mFabPlay.setImageResource(R.drawable.ic_fab_play);
        setFabPlayVisibility(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        setSearchVisibility(false);
        mMediaLibrary.setMediaUpdatedCb(this, Medialibrary.FLAG_MEDIA_UPDATED_VIDEO);
        mMediaLibrary.setMediaAddedCb(this, Medialibrary.FLAG_MEDIA_ADDED_VIDEO);
        mMediaLibrary.addDeviceDiscoveryCb(this);
        updateViewMode();
        mHandler.sendEmptyMessage(UPDATE_LIST);
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
                MediaUtils.openList(getActivity(), playList, mVideoAdapter.getListWithPosition(playList, position));
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
    public void onFabPlayClick(View view) {
        ArrayList<MediaWrapper> playList = new ArrayList<>();
        MediaUtils.openList(getActivity(), playList, mVideoAdapter.getListWithPosition(playList, 0));
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
                        updateEmptyView();
                    }
                });
    }

    @MainThread
    public void updateList() {
        mHandler.sendEmptyMessageDelayed(SET_REFRESHING, 300);

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
                        stopRefresh();
                        mVideoAdapter.dispatchUpdate(displayList);
                    }
                });
            }
        });
    }

    void updateEmptyView() {
        mViewNomedia.setVisibility(mVideoAdapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
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
        mHandler.removeMessages(SET_REFRESHING);
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onRefresh() {
        mMediaLibrary.reload();
    }

    @Override
    public void display() {}

    public void clear(){
        mVideoAdapter.clear();
    }

    boolean mParsing = false;
    @Override
    public void onDiscoveryStarted(String entryPoint) {}

    @Override
    public void onDiscoveryProgress(String entryPoint) {}

    @Override
    public void onDiscoveryCompleted(final String entryPoint) {
        mHandler.sendEmptyMessage(mParsing ? SET_REFRESHING : UNSET_REFRESHING);
    }

    @Override
    public void onParsingStatsUpdated(final int percent) {
        mParsing = percent < 100;
        if (percent == 100)
            mHandler.sendEmptyMessage(UPDATE_LIST);
        else if (!mSwipeRefreshLayout.isRefreshing())
            mHandler.sendEmptyMessage(SET_REFRESHING);
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
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        List<MediaWrapper> selection = mVideoAdapter.getSelection();
        if (selection.isEmpty()) {
            stopActionMode();
            return false;
        }
        boolean honeyComb = AndroidUtil.isHoneycombOrLater();
        int count = selection.size();
        menu.findItem(R.id.action_video_info).setVisible(count == 1);
        menu.findItem(R.id.action_video_play).setVisible(honeyComb || count == 1);
        menu.findItem(R.id.action_video_append).setVisible(honeyComb);
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
//            case R.id.action_video_delete:
//                for (int position : mVideoAdapter.getSelectedPositions())
//                    removeVideo(position, mVideoAdapter.getItem(position));
//                break;
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
        mActionMode = null;
        ArrayList<MediaWrapper> items = mVideoAdapter.getAll();
        for (int i = 0; i < items.size(); ++i) {
            MediaWrapper mw = items.get(i);
            if (mw.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                mw.removeStateFlags(MediaLibraryItem.FLAG_SELECTED);
                mVideoAdapter.notifyItemChanged(i, mw);
            }
        }
    }

    private static final int UPDATE_LIST = 14;
    private static final int SET_REFRESHING = 15;
    private static final int UNSET_REFRESHING = 16;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_LIST:
                    removeMessages(UPDATE_LIST);
                    updateList();
                    break;
                case SET_REFRESHING:
                    mSwipeRefreshLayout.setRefreshing(true);
                    break;
                case UNSET_REFRESHING:
                    mSwipeRefreshLayout.setRefreshing(false);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        MediaWrapper media = (MediaWrapper) item;
            if (mActionMode != null) {
                item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
                mVideoAdapter.notifyItemChanged(position, item);
                invalidateActionMode();
                return;
            }
            Activity activity = getActivity();
            if (media instanceof MediaGroup) {
                String title = media.getTitle().substring(media.getTitle().toLowerCase().startsWith("the") ? 4 : 0);
                ((MainActivity)activity).showSecondaryFragment(SecondaryActivity.VIDEO_GROUP_LIST, title);
            } else {
                media.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());
                if (settings.getBoolean("force_play_all", false)) {
                    ArrayList<MediaWrapper> playList = new ArrayList<>();
                    MediaUtils.openList(activity, playList, mVideoAdapter.getListWithPosition(playList, position));
                } else {
                    MediaUtils.openMedia(activity, media);
                }
            }
    }

    @Override
    public boolean onLongClick(View v, int position, MediaLibraryItem item) {
            if (mActionMode != null)
                return false;
            item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
            mVideoAdapter.notifyItemChanged(position, item);
            startActionMode();
            return true;
    }

    @Override
    public void onCtxClick(View v, int position, MediaLibraryItem item) {
            if (mActionMode != null)
                return;
            mGridView.openContextMenu(position);
    }

    @Override
    public void onUpdateFinished(RecyclerView.Adapter adapter) {
        updateEmptyView();
    }
}
