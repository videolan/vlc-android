/*****************************************************************************
 * VideoListActivity.java
 *****************************************************************************
 * Copyright © 2011-2018 VLC authors and VideoLAN
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
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
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
import android.support.annotation.NonNull;
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

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.VideoGridBinding;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.PlaylistManager;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.viewmodels.VideosProvider;

import java.util.ArrayList;
import java.util.List;

public class VideoGridFragment extends MediaBrowserFragment<VideosProvider> implements SwipeRefreshLayout.OnRefreshListener, IEventsHandler, Observer<List<MediaWrapper>> {

    private final static String TAG = "VLC/VideoListFragment";

    private VideoListAdapter mAdapter;
    private VideoGridBinding mBinding;
    private String mGroup;
    private DividerItemDecoration mDividerItemDecoration;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mAdapter == null) {
            mAdapter = new VideoListAdapter(this);
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
            final int minGroupLengthValue = Integer.valueOf(preferences.getString("video_min_group_length", "6"));
            mProvider = ViewModelProviders.of(requireActivity(), new VideosProvider.Factory(mGroup, minGroupLengthValue, Medialibrary.SORT_DEFAULT)).get(VideosProvider.class);
            mProvider.getDataset().observe(this, this);
        }
        if (savedInstanceState != null) setGroup(savedInstanceState.getString(Constants.KEY_GROUP));
    }


    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.ml_menu_last_playlist).setVisible(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ml_menu_last_playlist:
                MediaUtils.loadlastPlaylist(getActivity(), Constants.PLAYLIST_TYPE_VIDEO);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        mBinding = VideoGridBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mDividerItemDecoration = new DividerItemDecoration(requireActivity(), DividerItemDecoration.VERTICAL);
        if (mAdapter.isListMode()) mBinding.videoGrid.addItemDecoration(mDividerItemDecoration);
        mBinding.videoGrid.setAdapter(mAdapter);
    }

    private boolean restart = false;
    @Override
    public void onStart() {
        super.onStart();
        registerForContextMenu(mBinding.videoGrid);
        setSearchVisibility(false);
        updateViewMode();
        mFabPlay.setImageResource(R.drawable.ic_fab_play);
        setFabPlayVisibility(true);
        if (restart) mProvider.refresh();
    }

    @Override
    public void onStop() {
        super.onStop();
        unregisterForContextMenu(mBinding.videoGrid);
        restart = true;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(Constants.KEY_GROUP, mGroup);
    }

    @Override
    public void onChanged(@Nullable List<MediaWrapper> mediaWrappers) {
        if (mediaWrappers != null) mAdapter.update(mediaWrappers);
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
            mBinding.videoGrid.setColumnWidth(mBinding.videoGrid.getPerfectColumnWidth(thumbnailWidth, margin));
            mAdapter.setGridCardWidth(mBinding.videoGrid.getColumnWidth());
        }
        mBinding.videoGrid.setNumColumns(listMode ? 1 : -1);
        if (mAdapter.isListMode() != listMode) {
            if (listMode) mBinding.videoGrid.addItemDecoration(mDividerItemDecoration);
            else mBinding.videoGrid.removeItemDecoration(mDividerItemDecoration);
            mAdapter.setListMode(listMode);
        }
    }


    protected void playVideo(MediaWrapper media, boolean fromStart) {
        media.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
        VideoPlayerActivity.start(getActivity(), media.getUri(), fromStart);
    }

    protected void playAudio(MediaWrapper media) {
        media.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
        MediaUtils.openMedia(getActivity(), media);
    }

    protected boolean handleContextItemSelected(MenuItem menu, final int position) {
        if (position >= mAdapter.getItemCount()) return false;
        final MediaWrapper media = mAdapter.getItem(position);
        if (media == null) return false;
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
                if (media instanceof MediaGroup) MediaUtils.appendMedia(getActivity(), ((MediaGroup)media).getAll());
                else MediaUtils.appendMedia(getActivity(), media);
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
        mProvider.remove(media);
        final View view = getView();
        if (view != null) {
            final Runnable revert = new Runnable() {
                @Override
                public void run() {
                    mProvider.refresh();
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
        if (!(media instanceof MediaGroup)) setContextMenuItems(menu, media);
    }

    private void setContextMenuItems(Menu menu, MediaWrapper mediaWrapper) {
        menu.findItem(R.id.video_list_play_from_start).setVisible(mediaWrapper.getTime() > 0);
    }

    @Override
    public void onFabPlayClick(View view) {
        List<MediaWrapper> playList = new ArrayList<>();
        MediaUtils.openList(getActivity(), playList, mAdapter.getListWithPosition(playList, 0));
    }

    @MainThread
    public void updateList() {
        mProvider.refresh();
        mHandler.sendEmptyMessageDelayed(SET_REFRESHING, 300);
    }

    void updateEmptyView() {
        mBinding.setEmpty(mAdapter.isEmpty());
    }

    public void setGroup(String prefix) {
        mGroup = prefix;
    }

    @Override
    public void onRefresh() {
        final Activity activity = getActivity();
        if (activity != null) activity.startService(new Intent(Constants.ACTION_RELOAD, null, getActivity(), MediaParsingService.class));
    }

    public void clear(){
        mAdapter.clear();
    }

    @Override
    public void setFabPlayVisibility(boolean enable) {
        super.setFabPlayVisibility(!mAdapter.isEmpty() && enable);
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
        menu.findItem(R.id.action_video_append).setVisible(PlaylistManager.Companion.hasMedia());
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
                //                for (int position : rowsAdapter.getSelectedPositions())
                //                    removeVideo(position, rowsAdapter.getItem(position));
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
            final MediaWrapper mw = items.get(i);
            if (mw.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                mw.removeStateFlags(MediaLibraryItem.FLAG_SELECTED);
                mAdapter.resetSelectionCount();
                mAdapter.notifyItemChanged(i, Constants.UPDATE_SELECTION);
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
            mAdapter.notifyItemChanged(position, Constants.UPDATE_SELECTION);
            invalidateActionMode();
            return;
        }
        final Activity activity = getActivity();
        if (media instanceof MediaGroup) {
            final String title = media.getTitle().substring(media.getTitle().toLowerCase().startsWith("the") ? 4 : 0);
            ((MainActivity)activity).getNavigator().showSecondaryFragment(SecondaryActivity.VIDEO_GROUP_LIST, title);
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
        if (mActionMode != null) return false;
        item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
        mAdapter.updateSelectionCount(item.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
        mAdapter.notifyItemChanged(position, Constants.UPDATE_SELECTION);
        startActionMode();
        return true;
    }

    @Override
    public void onCtxClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode == null) mBinding.videoGrid.openContextMenu(position);
    }

    @Override
    public void onUpdateFinished(RecyclerView.Adapter adapter) {
        if (!mMediaLibrary.isWorking()) mHandler.sendEmptyMessage(UNSET_REFRESHING);
        updateEmptyView();
        setFabPlayVisibility(true);
        UiTools.updateSortTitles(this);
    }

    public void updateSeenMediaMarker() {
        mAdapter.setSeenMediaMarkerVisible(PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext()).getBoolean("media_seen", true));
        mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount()-1, Constants.UPDATE_SEEN);
    }
}
