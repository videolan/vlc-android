/**
 * **************************************************************************
 * NetworkFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.BrowserFragment;
import org.videolan.vlc.gui.DividerItemDecoration;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;


public class NetworkFragment extends BrowserFragment implements IRefreshable, MediaBrowser.EventListener, SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = "VLC/NetworkFragment";

    public static final String SMB_ROOT = "smb";
    public static final String KEY_MRL = "key_mrl";
    public static final String KEY_POSITION = "key_list";

    private NetworkFragmentHandler mHandler;
    private MediaBrowser mMediaBrowser;
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private NetworkAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;
    TextView mEmptyView;
    public String mMrl;
    private int savedPosition = -1, mFavorites = 0;
    private boolean mRoot;
    LibVLC mLibVLC = LibVLC.getExistingInstance();

    public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        if (bundle == null)
            bundle = getArguments();
        if (bundle != null){
            mMrl = bundle.getString(KEY_MRL);
            savedPosition = bundle.getInt(KEY_POSITION);
        }
        if (mMrl == null)
            mMrl = SMB_ROOT;
        mRoot = SMB_ROOT.equals(mMrl);
        mHandler = new NetworkFragmentHandler(this);
        mAdapter = new NetworkAdapter(this);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(R.layout.network_browser, container, false);
        mRecyclerView = (RecyclerView) v.findViewById(R.id.network_list);
        mEmptyView = (TextView) v.findViewById(android.R.id.empty);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL_LIST));
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setOnScrollListener(mScrollListener);

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkerorange);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        return v;
    }

    public void onStop(){
        super.onStop();
        if (mMediaBrowser != null)
            mMediaBrowser.release();
        savedPosition = mRecyclerView.getScrollY();
    }
    public void onStart(){
        super.onStart();

        //Handle network connection state
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        getActivity().registerReceiver(networkReceiver, filter);
    }
    public void onSaveInstanceState(Bundle outState){
        outState.putString(KEY_MRL, mMrl);
        if (mRecyclerView != null) {
            outState.putInt(KEY_POSITION, mLayoutManager.findFirstCompletelyVisibleItemPosition());
        }
        super.onSaveInstanceState(outState);
    }

    public String getTitle(){
        if (mRoot)
            return getString(R.string.network_browsing);
        else
            return Strings.getName(mMrl);
    }

    public boolean isRootDirectory(){
        return mRoot;
    }

    public void goBack(){
        getActivity().getSupportFragmentManager().popBackStack();
    }

    public void browse (MediaWrapper media){
        FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
        Fragment next = new NetworkFragment();
        Bundle args = new Bundle();
        args.putString(KEY_MRL, media.getLocation());
        next.setArguments(args);
        ft.replace(R.id.fragment_placeholder, next, media.getLocation());
        ft.addToBackStack(mMrl);
        ft.commit();
    }

    @Override
    public void onMediaAdded(int index, Media media) {
        mAdapter.addItem(media, mRoot, true);
        updateEmptyView();
        if (mRoot)
            mHandler.sendEmptyMessage(NetworkFragmentHandler.MSG_HIDE_LOADING);
    }

    @Override
    public void onMediaRemoved(int index, Media media) {
        //TODO
    }

    @Override
    public void onBrowseEnd() {
        mAdapter.sortList();
        mHandler.sendEmptyMessage(NetworkFragmentHandler.MSG_HIDE_LOADING);
        if (savedPosition > 0) {
            mLayoutManager.scrollToPositionWithOffset(savedPosition, 0);
            savedPosition = 0;
        }
    }

    @Override
    public void onRefresh() {
        savedPosition = mLayoutManager.findFirstCompletelyVisibleItemPosition();
        refresh();
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

    /**
     * Update views visibility and emptiness info
     *
     * @return True if content needs can be refreshed
     */
    private boolean updateEmptyView(){
        if (AndroidDevices.hasLANConnection()){
            if (mAdapter.isEmpty()){
                mEmptyView.setText(mRoot ? R.string.network_shares_discovery : R.string.network_empty);
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
                mSwipeRefreshLayout.setEnabled(false);
            } else {
                if (mEmptyView.getVisibility() == View.VISIBLE) {
                    mEmptyView.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
                    mSwipeRefreshLayout.setEnabled(true);
                }
            }
            return true;
        } else {
            if (mEmptyView.getVisibility() == View.GONE){
                mEmptyView.setText(R.string.network_connection_needed);
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
                mSwipeRefreshLayout.setEnabled(false);
            }
            return false;
        }

    }

    private void updateDisplay(){
        if (mMediaBrowser == null)
            mMediaBrowser = new MediaBrowser(mLibVLC, this);
        if (mAdapter.isEmpty())
            refresh();
        else if (mRoot)
            updateFavorites();
    }

    @Override
    public void refresh() {
        mAdapter.clear();
        if (mRoot){
            ArrayList<String> favs = MediaDatabase.getInstance().getAllNetworkFav();
            if (!favs.isEmpty()) {
                mFavorites = favs.size();
                for (String fav : favs) {
                    mAdapter.addItem(new MediaWrapper(fav), false, true);
                    mAdapter.notifyDataSetChanged();
                }
                mAdapter.addItem("Network favorites", false, true);
            }
        }
        if (mRoot)
            mMediaBrowser.discoverNetworkShares();
        else
            mMediaBrowser.browse(mMrl);
        mHandler.sendEmptyMessageDelayed(NetworkFragmentHandler.MSG_SHOW_LOADING, 300);
    }

    private void updateFavorites(){
        ArrayList<String> favs = MediaDatabase.getInstance().getAllNetworkFav();
        int newSize = favs.size(), totalSize = mAdapter.getItemCount();

        if (newSize == 0 && mFavorites == 0)
            return;
        for (int i = 1 ; i <= mFavorites ; ++i){ //remove former favorites
            mAdapter.removeItem(totalSize-i);
        }
        if (newSize == 0)
            mAdapter.removeItem(totalSize-mFavorites-1); //also remove separator if no more fav
        else {
            if (mFavorites == 0)
                mAdapter.addItem("Network favorites", false, false); //add header if needed
            for (String fav : favs)
                mAdapter.addItem(new MediaWrapper(fav), false, false); //add new favorites
        }
        mFavorites = newSize; //update count
    }

    public void toggleFavorite() {
        MediaDatabase db = MediaDatabase.getInstance();
        if (db.networkFavExists(mMrl))
            db.deleteNetworkFav(mMrl);
        else
            db.addNetworkFavItem(mMrl);
        getActivity().supportInvalidateOptionsMenu();
    }

    private static class NetworkFragmentHandler extends WeakHandler<NetworkFragment> {

        public static final int MSG_SHOW_LOADING = 0;
        public static final int MSG_HIDE_LOADING = 1;

        public NetworkFragmentHandler(NetworkFragment owner) {
            super(owner);
        }
        @Override
        public void handleMessage(Message msg) {
            NetworkFragment fragment = getOwner();
            switch (msg.what){
                case MSG_SHOW_LOADING:
                    fragment.mSwipeRefreshLayout.setRefreshing(true);
                    break;
                case MSG_HIDE_LOADING:
                    removeMessages(MSG_SHOW_LOADING);
                    fragment.mSwipeRefreshLayout.setRefreshing(false);
                    break;
            }
        }
    }

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action))
                if (updateEmptyView())
                    updateDisplay();
        }
    };
}
