/*****************************************************************************
 * HistoryFragment.java
 *****************************************************************************
 * Copyright © 2012-2015 VLC authors and VideoLAN
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
package org.videolan.vlc.gui;

import android.annotation.TargetApi;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.interfaces.IHistory;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.PlaylistManager;
import org.videolan.vlc.viewmodels.HistoryModel;

import java.util.List;

public class HistoryFragment extends MediaBrowserFragment<HistoryModel> implements IRefreshable, IHistory, SwipeRefreshLayout.OnRefreshListener, IEventsHandler {

    public final static String TAG = "VLC/HistoryFragment";

    private HistoryAdapter mHistoryAdapter;
    private View mEmptyView;
    private RecyclerView mRecyclerView;

    /* All subclasses of Fragment must include a public empty constructor. */
    public HistoryFragment() {
        mHistoryAdapter = new HistoryAdapter(this);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        return inflater.inflate(R.layout.history_list, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEmptyView = view.findViewById(R.id.empty);
        mRecyclerView = view.findViewById(android.R.id.list);
        viewModel = ViewModelProviders.of(requireActivity(), new HistoryModel.Factory(requireContext())).get(HistoryModel.class);
        viewModel.getDataset().observe(this, new Observer<List<MediaWrapper>>() {
            @Override
            public void onChanged(@Nullable List<MediaWrapper> mediaWrappers) {
                if (mediaWrappers != null) mHistoryAdapter.update(mediaWrappers);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        viewModel.refresh();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.setAdapter(mHistoryAdapter);
        mRecyclerView.setNextFocusUpId(R.id.ml_menu_search);
        mRecyclerView.setNextFocusLeftId(android.R.id.list);
        mRecyclerView.setNextFocusRightId(android.R.id.list);
        mRecyclerView.setNextFocusForwardId(android.R.id.list);
        mRecyclerView.requestFocus();
        registerForContextMenu(mRecyclerView);
        mSwipeRefreshLayout.setOnRefreshListener(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_option_history, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.ml_menu_clean).setVisible(!isEmpty());
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ml_menu_clean:
                clearHistory();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void setFabPlayVisibility(boolean enable) {
        if (mFabPlay != null) mFabPlay.setVisibility(View.GONE);
    }

    @Override
    public void refresh() {
        viewModel.refresh();
    }

    @Override
    public void onRefresh() {
        refresh();
    }

    @Override
    public String getTitle() {
        return getString(R.string.history);
    }

    public void clear(){}

    private void updateEmptyView() {
        if (mHistoryAdapter.isEmpty()){
            mSwipeRefreshLayout.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mEmptyView.setVisibility(View.GONE);
            mSwipeRefreshLayout.setVisibility(View.VISIBLE);
        }
    }

    public boolean isEmpty() {
        return mHistoryAdapter.isEmpty();
    }

    @Override
    public void clearHistory() {
        mMediaLibrary.clearHistory();
        viewModel.clear();
        updateEmptyView();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.action_mode_history, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        int selectionCount = mHistoryAdapter.getSelection().size();
        if (selectionCount == 0) {
            stopActionMode();
            return false;
        }
        menu.findItem(R.id.action_history_info).setVisible(selectionCount == 1);
        menu.findItem(R.id.action_history_append).setVisible(PlaylistManager.Companion.hasMedia());
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        List<MediaWrapper> selection =  mHistoryAdapter.getSelection();
        if (!selection.isEmpty()) {
            switch (item.getItemId()) {
                case R.id.action_history_play:
                    MediaUtils.INSTANCE.openList(getActivity(), selection, 0);
                    break;
                case R.id.action_history_append:
                    MediaUtils.INSTANCE.appendMedia(getActivity(), selection);
                    break;
                case R.id.action_history_info:
                    showInfoDialog(selection.get(0));
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
        int index = -1;
        for (MediaWrapper media : viewModel.getDataset().getValue()) {
            ++index;
            if (media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                media.removeStateFlags(MediaLibraryItem.FLAG_SELECTED);
                mHistoryAdapter.notifyItemChanged(index, media);
            }
        }
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode != null) {
            item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
            mHistoryAdapter.notifyItemChanged(position, item);
            invalidateActionMode();
            return;
        }
        if (position != 0) viewModel.moveUp((MediaWrapper) item);
        MediaUtils.INSTANCE.openMedia(v.getContext(), (MediaWrapper) item);
    }

    @Override
    public boolean onLongClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode != null) return false;
        item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
        mHistoryAdapter.notifyItemChanged(position, item);
        startActionMode();
        return true;
    }

    @Override
    public void onCtxClick(View v, int position, MediaLibraryItem item) {}

    @Override
    public void onUpdateFinished(RecyclerView.Adapter adapter) {
        invalidateActionMode();
        UiTools.updateSortTitles(this);
    }
}
