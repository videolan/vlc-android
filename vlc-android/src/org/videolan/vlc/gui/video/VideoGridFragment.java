/*****************************************************************************
 * VideoListActivity.java
 *****************************************************************************
 * Copyright © 2011-2017 VLC authors and VideoLAN
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.interfaces.MediaAddedCb;
import org.videolan.medialibrary.interfaces.MediaUpdatedCb;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.browser.SortableFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.AutoFitRecyclerView;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.Filterable;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.List;

public class VideoGridFragment extends SortableFragment<VideoListAdapter> implements MediaUpdatedCb, SwipeRefreshLayout.OnRefreshListener, MediaAddedCb, Filterable, IEventsHandler {

    private final static String TAG = "VLC/VideoListFragment";

    private final static String KEY_GROUP = "key_group";

    private AutoFitRecyclerView mGridView;
    private View mViewNomedia;
    private String mGroup;
    private View mSearchButtonView;
    private DividerItemDecoration mDividerItemDecoration;

    /* All subclasses of Fragment must include a public empty constructor. */
    public VideoGridFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new VideoListAdapter(this);

        if (savedInstanceState != null)
            setGroup(savedInstanceState.getString(KEY_GROUP));
    }


    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.ml_menu_sortby_artist_name).setVisible(false);
        menu.findItem(R.id.ml_menu_sortby_album_name).setVisible(false);
        menu.findItem(R.id.ml_menu_sortby_length).setVisible(true);
        menu.findItem(R.id.ml_menu_sortby_date).setVisible(true);
        menu.findItem(R.id.ml_menu_last_playlist).setVisible(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ml_menu_last_playlist:
                getActivity().sendBroadcast(new Intent(Constants.ACTION_REMOTE_LAST_VIDEO_PLAYLIST));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        return inflater.inflate(R.layout.video_grid, container, false);
    }

    @Override
    public void onViewCreated(View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        mViewNomedia = v.findViewById(android.R.id.empty);
        mGridView = v.findViewById(android.R.id.list);
        mSwipeRefreshLayout = v.findViewById(R.id.swipeLayout);
        mSearchButtonView = v.findViewById(R.id.searchButton);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mDividerItemDecoration = new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL);
        if (mAdapter.isListMode())
            mGridView.addItemDecoration(mDividerItemDecoration);
        if (savedInstanceState != null) {
            final List<MediaWrapper> list = (List<MediaWrapper>) VLCApplication.getData("list"+getTitle());
            if (!Util.isListEmpty(list)) mAdapter.update(list);
        }
        mGridView.setAdapter(mAdapter);
    }

    private boolean restart = false;
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            if (mMediaLibrary.isInitiated())
                onMedialibraryReady();
            else if (mGroup == null)
                setupMediaLibraryReceiver();
            registerForContextMenu(mGridView);
            setSearchVisibility(false);
            updateViewMode();
            mFabPlay.setImageResource(R.drawable.ic_fab_play);
            setFabPlayVisibility(true);
            if (restart && !mMediaLibrary.isWorking()) updateList();
        } else {
            mMediaLibrary.removeMediaUpdatedCb();
            mMediaLibrary.removeMediaAddedCb();
            unregisterForContextMenu(mGridView);
            restart = true;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_GROUP, mGroup);
        VLCApplication.storeData("list"+getTitle(), mAdapter.getAll());
    }

    protected void onMedialibraryReady() {
        super.onMedialibraryReady();
        if (mGroup == null) {
            mMediaLibrary.setMediaUpdatedCb(this, Medialibrary.FLAG_MEDIA_UPDATED_VIDEO);
            mMediaLibrary.setMediaAddedCb(this, Medialibrary.FLAG_MEDIA_ADDED_VIDEO);
        }
        if (!isHidden())
            mHandler.sendEmptyMessage(UPDATE_LIST);
    }

    public String getTitle() {
        return mGroup == null ? getString(R.string.video) : mGroup + "\u2026";
    }

    private void updateViewMode() {
        if (getView() == null || getActivity() == null) {
            Log.w(TAG, "Unable to setup the view");
            return;
        }
        final Resources res = getResources();
        final boolean listMode = res.getBoolean(R.bool.list_mode)
                || (res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT &&
                PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("force_list_portrait", false));

        // Select between grid or list
        if (!listMode) {
            final int thumbnailWidth = res.getDimensionPixelSize(R.dimen.grid_card_thumb_width);
            final int margin = res.getDimensionPixelSize(R.dimen.default_margin);
            mGridView.setColumnWidth(mGridView.getPerfectColumnWidth(thumbnailWidth, margin));
            mAdapter.setGridCardWidth(mGridView.getColumnWidth());
        }
        mGridView.setNumColumns(listMode ? 1 : -1);
        if (mAdapter.isListMode() != listMode) {
            if (listMode)
                mGridView.addItemDecoration(mDividerItemDecoration);
            else
                mGridView.removeItemDecoration(mDividerItemDecoration);
            mAdapter.setListMode(listMode);
        }
    }


    protected void playVideo(MediaWrapper media, boolean fromStart) {
        final Activity activity = getActivity();
        if (activity instanceof PlaybackService.Callback)
            mService.removeCallback((PlaybackService.Callback) activity);
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
        if (position >= mAdapter.getItemCount()) return false;
        final MediaWrapper media = mAdapter.getItem(position);
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
                List<MediaWrapper> playList = new ArrayList<>();
                MediaUtils.openList(getActivity(), playList, mAdapter.getListWithPosition(playList, position));
                return true;
            case R.id.video_list_info:
                showInfoDialog(media);
                return true;
            case R.id.video_list_delete:
                removeVideo(media);
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

    private void removeVideo(final MediaWrapper media) {
        if (!checkWritePermission(media, new Runnable() {
            @Override
            public void run() {
                removeVideo(media);
            }
        })) return;
        final int position = mAdapter.remove(media);
        final View view = getView();
        if (position != -1 && view != null) {
            final Runnable revert = new Runnable() {
                @Override
                public void run() {
                    mAdapter.add(media);
                }
            };
            UiTools.snackerWithCancel(view, getString(R.string.file_deleted), new Runnable() {
                @Override
                public void run() {
                    deleteMedia(media, false, revert);
                }
            }, revert);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo == null) return;
        // Do not show the menu of media group.
        final ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView.RecyclerContextMenuInfo)menuInfo;
        final MediaWrapper media = mAdapter.getItem(info.position);
        if (media == null) return;
        final MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(media instanceof MediaGroup ? R.menu.video_group_contextual : R.menu.video_list, menu);
        if (media instanceof MediaGroup) {
            if (!AndroidUtil.isHoneycombOrLater) {
                menu.findItem(R.id.video_list_append).setVisible(false);
                menu.findItem(R.id.video_group_play).setVisible(false);
            }
        } else setContextMenuItems(menu, media);
    }

    private void setContextMenuItems(Menu menu, MediaWrapper mediaWrapper) {
        menu.findItem(R.id.video_list_play_from_start).setVisible(mediaWrapper.getTime() > 0);
        if (!AndroidUtil.isHoneycombOrLater) {
            menu.findItem(R.id.video_list_play_all).setVisible(false);
            menu.findItem(R.id.video_list_append).setVisible(false);
        }
    }

    @Override
    public void onFabPlayClick(View view) {
        List<MediaWrapper> playList = new ArrayList<>();
        MediaUtils.openList(getActivity(), playList, mAdapter.getListWithPosition(playList, 0));
    }

    @Override
    public void onMediaUpdated(final MediaWrapper[] mediaList) {
        mAdapter.add(mediaList);
    }

    @Override
    public void onMediaAdded(final MediaWrapper[] mediaList) {
        mAdapter.add(mediaList);
    }

    @MainThread
    public void updateList() {
        mHandler.sendEmptyMessageDelayed(SET_REFRESHING, 300);

        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final MediaWrapper[] itemList = mMediaLibrary.getVideos();
                final List<MediaWrapper> displayList = new ArrayList<>();
                if (mGroup != null) {
                    for (MediaWrapper item : itemList) {
                        String title = item.getTitle().substring(item.getTitle().toLowerCase().startsWith("the") ? 4 : 0);
                        if (mGroup == null || title.toLowerCase().startsWith(mGroup.toLowerCase()))
                            displayList.add(item);
                    }
                } else {
                    for (MediaGroup item : MediaGroup.group(itemList))
                        displayList.add(item.getMedia());
                }
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.update(displayList);
                    }
                });
                mHandler.sendEmptyMessage(UNSET_REFRESHING);
            }
        });
    }

    void updateEmptyView() {
        mViewNomedia.setVisibility(mAdapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
    }

    public void setGroup(String prefix) {
        mGroup = prefix;
    }

    @Override
    public void onRefresh() {
        getActivity().startService(new Intent(Constants.ACTION_RELOAD, null, getActivity(), MediaParsingService.class));
    }

    @Override
    public void display() {}

    public void clear(){
        mAdapter.clear();
    }

    @Override
    public void setFabPlayVisibility(boolean enable) {
        super.setFabPlayVisibility(!mAdapter.isEmpty() && enable);
    }

    @Override
    protected void onParsingServiceStarted() {
        mHandler.sendEmptyMessageDelayed(SET_REFRESHING, 300);
    }

    @Override
    protected void onParsingServiceFinished() {
        mMediaLibrary.removeMediaUpdatedCb();
        mMediaLibrary.removeMediaAddedCb();
        if (!isHidden()) mHandler.sendEmptyMessage(UPDATE_LIST);
    }

    @Override
    public boolean enableSearchOption() {
        return true;
    }

    @Override
    public Filter getFilter() {
        return mAdapter.getFilter();
    }

    @Override
    public void restoreList() {
        if (mAdapter != null && mGridView != null) mAdapter.restoreList();
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
        final int count = mAdapter.getSelectionCount();
        if (count == 0) {
            stopActionMode();
            return false;
        }
        menu.findItem(R.id.action_video_info).setVisible(count == 1);
        menu.findItem(R.id.action_video_play).setVisible(AndroidUtil.isHoneycombOrLater || count == 1);
        menu.findItem(R.id.action_video_append).setVisible(mService != null && mService.hasMedia() && AndroidUtil.isHoneycombOrLater);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        final List<MediaWrapper> list = mAdapter.getSelection();
        if (!list.isEmpty()) {
            switch (item.getItemId()) {
                case R.id.action_video_play:
                    MediaUtils.openList(getActivity(), list, 0);
                    break;
                case R.id.action_video_append:
                    MediaUtils.appendMedia(getActivity(), list);
                    break;
                case R.id.action_video_info:
                    showInfoDialog(list.get(0));
                    break;
                //            case R.id.action_video_delete:
                //                for (int position : mAdapter.getSelectedPositions())
                //                    removeVideo(position, mAdapter.getItem(position));
                //                break;
                case R.id.action_video_download_subtitles:
                    MediaUtils.getSubs(getActivity(), list);
                    break;
                case R.id.action_video_play_audio:
                    for (MediaWrapper media : list)
                        media.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                    MediaUtils.openList(getActivity(), list, 0);
                    break;
                default:
                    stopActionMode();
                    return false;
            }
        }
        stopActionMode();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
        setFabPlayVisibility(true);
        final List<MediaWrapper> items = mAdapter.getAll();
        for (int i = 0; i < items.size(); ++i) {
            MediaWrapper mw = items.get(i);
            if (mw.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                mw.removeStateFlags(MediaLibraryItem.FLAG_SELECTED);
                mAdapter.resetSelectionCount();
                mAdapter.notifyItemChanged(i, VideoListAdapter.UPDATE_SELECTION);
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
                    removeMessages(SET_REFRESHING);
                    mSwipeRefreshLayout.setRefreshing(false);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        final MediaWrapper media = (MediaWrapper) item;
        if (mActionMode != null) {
            item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
            mAdapter.updateSelectionCount(item.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
            mAdapter.notifyItemChanged(position, VideoListAdapter.UPDATE_SELECTION);
            invalidateActionMode();
            return;
        }
        final Activity activity = getActivity();
        if (media instanceof MediaGroup) {
            final String title = media.getTitle().substring(media.getTitle().toLowerCase().startsWith("the") ? 4 : 0);
            ((MainActivity)activity).showSecondaryFragment(SecondaryActivity.VIDEO_GROUP_LIST, title);
        } else {
            media.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());
            if (settings.getBoolean("force_play_all", false)) {
                final List<MediaWrapper> playList = new ArrayList<>();
                MediaUtils.openList(activity, playList, mAdapter.getListWithPosition(playList, position));
            } else {
                playVideo(media, false);
            }
        }
    }

    @Override
    public boolean onLongClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode != null)
            return false;
        item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
        mAdapter.updateSelectionCount(item.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
        mAdapter.notifyItemChanged(position, VideoListAdapter.UPDATE_SELECTION);
        startActionMode();
        return true;
    }

    @Override
    public void onCtxClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode == null)
            mGridView.openContextMenu(position);
    }

    @Override
    public void onUpdateFinished(RecyclerView.Adapter adapter) {
        if (!mMediaLibrary.isWorking())
            mHandler.sendEmptyMessage(UNSET_REFRESHING);
        updateEmptyView();
        setFabPlayVisibility(true);
    }

    public void updateSeenMediaMarker() {
        mAdapter.setSeenMediaMarkerVisible(PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext()).getBoolean("media_seen", true));
        mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount()-1, VideoListAdapter.UPDATE_SEEN);
    }
}
