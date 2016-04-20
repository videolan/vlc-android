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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.IHistory;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaWrapper;

import java.util.ArrayList;

public class HistoryFragment extends MediaBrowserFragment implements IRefreshable, IHistory, SwipeRefreshLayout.OnRefreshListener {

    public final static String TAG = "VLC/HistoryFragment";

    private static final int UPDATE_LIST = 0;

    private HistoryAdapter mHistoryAdapter;
    private RecyclerView mRecyclerView;
    private RecyclerView.LayoutManager mLayoutManager;
    private View mEmptyView;

    /* All subclasses of Fragment must include a public empty constructor. */
    public HistoryFragment() {
        mHistoryAdapter = new HistoryAdapter();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){

        View v = inflater.inflate(R.layout.history_list, container, false);
        mRecyclerView = (RecyclerView)v.findViewById(android.R.id.list);
        mEmptyView = v.findViewById(android.R.id.empty);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mHistoryAdapter);
        mRecyclerView.setNextFocusUpId(R.id.ml_menu_search);
        mRecyclerView.setNextFocusLeftId(android.R.id.list);
        mRecyclerView.setNextFocusRightId(android.R.id.list);
        if (AndroidUtil.isHoneycombOrLater())
            mRecyclerView.setNextFocusForwardId(android.R.id.list);
        mRecyclerView.requestFocus();
        registerForContextMenu(mRecyclerView);

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);

        mSwipeRefreshLayout.setColorSchemeColors(R.color.orange700/*, R.attr.colorPrimary, R.attr.colorPrimaryDark*/);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mRecyclerView.addOnScrollListener(mScrollListener);
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

    public void onStart(){
        super.onStart();
        if (mReadyToDisplay && mHistoryAdapter.isEmpty())
            display();
    }

    @Override
    public void refresh() {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                ArrayList<MediaWrapper> list = MediaDatabase.getInstance().getHistory();
                mHandler.obtainMessage(UPDATE_LIST, list).sendToTarget();
            }
        });
    }

    @Override
    public void onRefresh() {
        refresh();
    }

    @Override
    public void display() {
        mReadyToDisplay = true;
        refresh();
    }

    @Override
    protected String getTitle() {
        return getString(R.string.history);
    }

    public void clear(){}

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case UPDATE_LIST:
                    mHistoryAdapter.setList((ArrayList<MediaWrapper>) msg.obj);
                    updateEmptyView();
                    if( mHistoryAdapter != null ) {
                        mHistoryAdapter.notifyDataSetChanged();
                    }
                    mSwipeRefreshLayout.setRefreshing(false);
                    getActivity().supportInvalidateOptionsMenu();
            }
        }
    };

    private void updateEmptyView() {
        if (mHistoryAdapter.isEmpty()){
            mRecyclerView.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    public boolean isEmpty() {
        return mHistoryAdapter.isEmpty();
    }

    @Override
    public void clearHistory() {
        mHistoryAdapter.clear();
        updateEmptyView();
    }
}
