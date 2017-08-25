/*
 * *************************************************************************
 *  NetworkBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.videolan.medialibrary.media.DummyItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.dialogs.NetworkServerDialog;
import org.videolan.vlc.gui.dialogs.VlcLoginDialog;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.util.AndroidDevices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NetworkBrowserFragment extends BaseBrowserFragment {

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_option_network, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem item = menu.findItem(R.id.ml_menu_save);
        item.setVisible(isSortEnabled());

        boolean isFavorite = mMrl != null && MediaDatabase.getInstance().networkFavExists(Uri.parse(mMrl));
        item.setIcon(isFavorite ?
                R.drawable.ic_menu_bookmark_w :
                R.drawable.ic_menu_bookmark_outline_w);
        item.setTitle(isFavorite ? R.string.favorites_remove : R.string.favorites_add);
    }

    public void onStart() {
        super.onStart();
        //Handle network connection state
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        mSkipRefresh = !mAdapter.isEmpty();
        getActivity().registerReceiver(networkReceiver, filter);
        if (!mRoot)
            LocalBroadcastManager.getInstance(VLCApplication.getAppContext()).registerReceiver(mLocalReceiver, new IntentFilter(VlcLoginDialog.ACTION_DIALOG_CANCELED));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ml_menu_save:
                toggleFavorite();
                onPrepareOptionsMenu(mMenu);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!mRoot || mFabPlay == null)
            return;
        if (hidden) {
            setFabPlayVisibility(false);
            mFabPlay.setOnClickListener(null);
        } else {
            mFabPlay.setImageResource(R.drawable.ic_fab_add);
            mFabPlay.setOnClickListener(this);
            setFabPlayVisibility(true);
        }
    }

    @Override
    protected Fragment createFragment() {
        return new NetworkBrowserFragment();
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unregisterReceiver(networkReceiver);
        if (!mRoot)
            LocalBroadcastManager.getInstance(VLCApplication.getAppContext()).unregisterReceiver(mLocalReceiver);
    }

    protected boolean handleContextItemSelected(MenuItem item, final int position) {
        int id = item.getItemId();
        if (! (mAdapter.getItem(position) instanceof MediaWrapper))
            return super.onContextItemSelected(item);
        final MediaWrapper mw = (MediaWrapper) mAdapter.getItem(position);
        MediaDatabase db;
        switch (id){
            case R.id.network_add_favorite:
                db = MediaDatabase.getInstance();
                db.addNetworkFavItem(mw.getUri(), mw.getTitle(), mw.getArtworkURL());
                if (isRootDirectory())
                    updateFavorites();
                return true;
            case R.id.network_remove_favorite:
                db = MediaDatabase.getInstance();
                db.deleteNetworkFav(mw.getUri());
                if (isRootDirectory())
                    updateFavorites();
                return true;
            case R.id.network_edit_favorite:
                showAddServerDialog(mw);
                return true;
        }
        return super.handleContextItemSelected(item, position);
    }

    @Override
    protected void browseRoot() {
        if (!isAdded())
            return;
        updateFavorites();
        mAdapter.setTop(mAdapter.getItemCount());
        if (allowLAN())
            runOnBrowserThread(new Runnable() {
                @Override
                public void run() {
                    if (mMediaBrowser == null)
                        initMediaBrowser(NetworkBrowserFragment.this);
                    mMediaBrowser.discoverNetworkShares();
                }
            });
        else {
            int itemCount = mAdapter.getItemCount();
            if (itemCount > 0)
                mAdapter.removeItem(itemCount - 1);
            mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
        }
    }

    private boolean allowLAN() {
        return AndroidDevices.hasLANConnection() || AndroidDevices.isVPNActive();
    }

    @Override
    protected String getCategoryTitle() {
        return getString(R.string.network_browsing);
    }

    private void updateFavorites(){
        if (!AndroidDevices.hasConnection()) {
            if (mFavorites != 0) {
                mAdapter.clear();
                mFavorites = 0;
            }
            updateEmptyView();
            return;
        }

        ArrayList<MediaWrapper> favs = MediaDatabase.getInstance().getAllNetworkFav();
        int newSize = favs.size();

        if (newSize == 0 && mFavorites == 0)
            return;
        if (!allowLAN()) {
            ArrayList<MediaWrapper> toRemove = new ArrayList<>();
            List<String> schemes = Arrays.asList("ftp", "sftp", "ftps", "http", "https");
            for (MediaWrapper mw : favs)
                if (!schemes.contains(mw.getUri().getScheme()))
                    toRemove.add(mw);
            if (!toRemove.isEmpty())
                for (MediaWrapper mw : toRemove)
                    favs.remove(mw);
            newSize = favs.size();
            if (newSize == 0) {
                if (mFavorites != 0) {
                    mAdapter.clear();
                    mFavorites = 0;
                }
                return;
            }
        }
        if (mFavorites != 0 && !mAdapter.isEmpty())
            for (int i = 1 ; i <= mFavorites ; ++i) //remove former favorites
                mAdapter.removeItem(1);

        if (newSize == 0 && !mAdapter.isEmpty()) {
            mAdapter.removeItem(0); //also remove separator if no more fav
            mAdapter.removeItem(0); //also remove separator if no more fav
        } else {
            boolean isEmpty =  mAdapter.isEmpty();
            if (mFavorites == 0 || isEmpty)
                mAdapter.addItem(new DummyItem(getString(R.string.network_favorites)), false,0); //add header if needed
            for (int i = 0 ; i < newSize ; )
                mAdapter.addItem(favs.get(i), false, ++i); //add new favorites
            if (mFavorites == 0 || isEmpty)
                mAdapter.addItem(new DummyItem(getString(R.string.network_shared_folders)), false, newSize + 1); //add header if needed
        }
        mFavorites = newSize; //update count
        if (newSize != 0)
            mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
        updateEmptyView();
    }

    public void toggleFavorite() {
        MediaDatabase db = MediaDatabase.getInstance();
        if (db.networkFavExists(mCurrentMedia.getUri()))
            db.deleteNetworkFav(mCurrentMedia.getUri());
        else
            db.addNetworkFavItem(mCurrentMedia.getUri(), mCurrentMedia.getTitle(), mCurrentMedia.getArtworkURL());
        getActivity().supportInvalidateOptionsMenu();
    }

    /**
     * Update views visibility and emptiness info
     */
    protected void updateEmptyView() {
        if (AndroidDevices.hasConnection()) {
            if (mAdapter.isEmpty()) {
                if (mSwipeRefreshLayout.isRefreshing()) {
                    mEmptyView.setText(R.string.loading);
                    mEmptyView.setVisibility(View.VISIBLE);
                    mRecyclerView.setVisibility(View.GONE);
                } else {
                    if (mRoot)
                        mEmptyView.setText(allowLAN() ? R.string.network_shares_discovery : R.string.network_connection_needed);
                    else
                        mEmptyView.setText(R.string.network_empty);
                    mEmptyView.setVisibility(View.VISIBLE);
                    mRecyclerView.setVisibility(View.GONE);
                    mSwipeRefreshLayout.setRefreshing(false);
                }
            } else if (mEmptyView.getVisibility() == View.VISIBLE) {
                    mEmptyView.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
            }
        } else {
            mEmptyView.setText(R.string.network_connection_needed);
            mEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        if (!isRootDirectory())
            super.onClick(v);
        else if (v.getId() == R.id.fab)
            showAddServerDialog(null);
    }

    public void showAddServerDialog(MediaWrapper mw) {
        FragmentManager fm = getFragmentManager();
        NetworkServerDialog dialog = new NetworkServerDialog();
        if (mw != null)
            dialog.setServer(mw);
        dialog.show(fm, "fragment_add_server");
    }

    public boolean isSortEnabled() {
        return !mRoot;
    }

    private boolean mSkipRefresh = false;
    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        boolean connected = true;
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mReadyToDisplay && ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                final NetworkInfo networkInfo = ((ConnectivityManager) VLCApplication.getAppContext().getSystemService(
                        Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
                if (networkInfo == null || networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                    if (networkInfo == null){
                        if (connected)
                            connected = false;
                        else
                            return; //block consecutive calls when disconnected
                    } else
                        connected = true;
                    if (mSkipRefresh)
                        mSkipRefresh = false;
                    else
                        refresh();
                }
            }
        }
    };

    private BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isResumed())
                goBack();
            else
                goBack = true;
        }
    };
}
