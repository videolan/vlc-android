/*****************************************************************************
 * HistoryFragment.java
 *****************************************************************************
 * Copyright © 2012-2013 VLC authors and VideoLAN
 * Copyright © 2012-2013 Edward Wang
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

import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.interfaces.IRefreshable;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;

public class HistoryFragment extends BrowserFragment implements IRefreshable, SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemClickListener {
    public final static String TAG = "VLC/HistoryFragment";

    private HistoryAdapter mHistoryAdapter;
    private ListView mListView;

    /* All subclasses of Fragment must include a public empty constructor. */
    public HistoryFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHistoryAdapter = new HistoryAdapter(getActivity());
    }

    private void focusHelper(boolean idIsEmpty) {
        View parent = View.inflate(getActivity(), R.layout.history_list,
            null);
        MainActivity main = (MainActivity)getActivity();
        main.setMenuFocusDown(idIsEmpty, android.R.id.list);
        main.setSearchAsFocusDown(idIsEmpty, parent,
                android.R.id.list);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){

        View v = inflater.inflate(R.layout.history_list, container, false);
        mListView = (ListView)v.findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);
        mListView.setAdapter(mHistoryAdapter);
        mListView.setNextFocusUpId(R.id.ml_menu_search);
        mListView.setNextFocusLeftId(android.R.id.list);
        mListView.setNextFocusRightId(android.R.id.list);
        if (LibVlcUtil.isHoneycombOrLater())
            mListView.setNextFocusForwardId(android.R.id.list);
        focusHelper(mHistoryAdapter.getCount() == 0);
        mListView.requestFocus();
        registerForContextMenu(mListView);

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);

        mSwipeRefreshLayout.setColorSchemeColors(R.color.darkerorange/*, R.attr.colorPrimary, R.attr.colorPrimaryDark*/);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                mSwipeRefreshLayout.setEnabled(firstVisibleItem == 0);
            }
        });
        return v;
    }

    @Override
    public void onDestroy() {
        mHistoryAdapter.release();
        super.onDestroy();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        MenuInflater menuInflater = getActivity().getMenuInflater();
        menuInflater.inflate(R.menu.history_view, menu);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        playListIndex(position);
    }

    private void playListIndex(int position) {
        AudioServiceController audioController = AudioServiceController.getInstance();

        audioController.playIndex(position);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if(!getUserVisibleHint()) return super.onContextItemSelected(item);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        if(info == null) // info can be null
            return super.onContextItemSelected(item);
        int id = item.getItemId();

        if(id == R.id.history_view_play) {
            playListIndex(info.position);
            return true;
        } else if(id == R.id.history_view_delete) {
            mHistoryAdapter.remove(info.position);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void refresh() {
        if( mHistoryAdapter != null ) {
            mHistoryAdapter.notifyDataSetChanged();
            focusHelper(mHistoryAdapter.getCount() == 0);
        } else
            focusHelper(true);
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onRefresh() {
        refresh();
    }

    @Override
    public void setReadyToDisplay(boolean ready) {
        if (ready && !mReadyToDisplay)
            display();
        else
            mReadyToDisplay = ready;
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
}
