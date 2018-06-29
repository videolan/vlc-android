/*
 * *************************************************************************
 *  NetworkBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2015-2017 VLC authors and VideoLAN
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

import android.app.Activity;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.jetbrains.annotations.NotNull;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.ExternalMonitor;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.SimpleAdapter;
import org.videolan.vlc.gui.dialogs.NetworkServerDialog;
import org.videolan.vlc.gui.dialogs.VlcLoginDialog;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.viewmodels.browser.NetworkModel;

import java.util.List;

public class NetworkBrowserFragment extends BaseBrowserFragment implements SimpleAdapter.FavoritesHandler {

    @Override
    public void onClick(@NotNull MediaLibraryItem item) {
        browse((MediaWrapper) item, true);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getBinding().setShowFavorites(isRootDirectory());
        viewModel = ViewModelProviders.of(this, new NetworkModel.Factory(getMrl(), getShowHiddenFiles())).get(NetworkModel.class);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (isRootDirectory()) ((NetworkModel) viewModel).getFavorites().observe(this, new Observer<List<MediaLibraryItem>>() {
            @Override
            public void onChanged(@Nullable List<MediaLibraryItem> mediaLibraryItems) {
                getBinding().setShowFavorites(!Util.isListEmpty(mediaLibraryItems));
                favoritesAdapter.update(mediaLibraryItems);
            }
        });
        ExternalMonitor.connected.observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean connected) {
                refresh(connected);
            }
        });
    }

    private BaseBrowserAdapter favoritesAdapter;
    @Override
    protected void initFavorites() {
        if (!isRootDirectory()) return;
        getBinding().favoritesList.setLayoutManager(new LinearLayoutManager(getActivity()));
        favoritesAdapter = new BaseBrowserAdapter(this, true);
        getBinding().favoritesList.setAdapter(favoritesAdapter);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_option_network, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final MenuItem item = menu.findItem(R.id.ml_menu_save);
        item.setVisible(!isRootDirectory());

        boolean isFavorite = getMrl() != null && MediaDatabase.getInstance().networkFavExists(Uri.parse(getMrl()));
        item.setIcon(isFavorite ?
                R.drawable.ic_menu_bookmark_w :
                R.drawable.ic_menu_bookmark_outline_w);
        item.setTitle(isFavorite ? R.string.favorites_remove : R.string.favorites_add);
    }

    public void onStart() {
        super.onStart();
        if (!isRootDirectory()) LocalBroadcastManager.getInstance(VLCApplication.getAppContext()).registerReceiver(mLocalReceiver, new IntentFilter(VlcLoginDialog.ACTION_DIALOG_CANCELED));
        mFabPlay.setImageResource(R.drawable.ic_fab_add);
        mFabPlay.setOnClickListener(this);
        setFabPlayVisibility(true);
    }

    @Override
    public void refresh() {
        refresh(ExternalMonitor.isConnected());
    }

    public void refresh(boolean connected) {
        if (connected) super.refresh();
        else {
            updateEmptyView();
            getAdapter().clear();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ml_menu_save:
                toggleFavorite();
                onPrepareOptionsMenu(getMenu());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected Fragment createFragment() {
        return new NetworkBrowserFragment();
    }

    @Override
    public void onStop() {
        super.onStop();
        setFabPlayVisibility(false);
        mFabPlay.setOnClickListener(null);
        if (!isRootDirectory()) LocalBroadcastManager.getInstance(VLCApplication.getAppContext()).unregisterReceiver(mLocalReceiver);
        setGoBack(false);
    }

    @Override
    public void onCtxAction(int position, int option) {
        final MediaWrapper mw = (MediaWrapper) (isRootDirectory() ? favoritesAdapter.getItem(position) : getAdapter().getItem(position));
        switch (option) {
            case Constants.CTX_NETWORK_ADD:
                MediaDatabase.getInstance().addNetworkFavItem(mw.getUri(), mw.getTitle(), mw.getArtworkURL());
                if (isRootDirectory()) ((NetworkModel) getViewModel()).updateFavs();
                break;
            case Constants.CTX_NETWORK_EDIT:
                showAddServerDialog(mw);
                break;
            case Constants.CTX_NETWORK_REMOVE:
                MediaDatabase.getInstance().deleteNetworkFav(mw.getUri());
                if (isRootDirectory()) ((NetworkModel) getViewModel()).updateFavs();
                break;
            default:
                super.onCtxAction(position, option);
        }
    }

    @Override
    protected void browseRoot() {}

    private boolean allowLAN() {
        return ExternalMonitor.isLan() || ExternalMonitor.isVPN();
    }

    @Override
    protected String getCategoryTitle() {
        return getString(R.string.network_browsing);
    }

    public void toggleFavorite() {
        final MediaDatabase db = MediaDatabase.getInstance();
        if (db.networkFavExists(getCurrentMedia().getUri()))
            db.deleteNetworkFav(getCurrentMedia().getUri());
        else
            db.addNetworkFavItem(getCurrentMedia().getUri(), getCurrentMedia().getTitle(), getCurrentMedia().getArtworkURL());
        final Activity activity = getActivity();
        if (activity!= null) activity.invalidateOptionsMenu();
    }

    /**
     * Update views visibility and emptiness info
     */
    protected void updateEmptyView() {
        if (getBinding() == null) return;
        if (ExternalMonitor.isConnected()) {
            if (Util.isListEmpty(getViewModel().getDataset().getValue())) {
                if (mSwipeRefreshLayout == null || mSwipeRefreshLayout.isRefreshing()) {
                    getBinding().empty.setText(R.string.loading);
                    getBinding().empty.setVisibility(View.VISIBLE);
                    getBinding().networkList.setVisibility(View.GONE);
                } else {
                    if (isRootDirectory()) getBinding().empty.setText(allowLAN() ? R.string.network_shares_discovery : R.string.network_connection_needed);
                    else getBinding().empty.setText(R.string.network_empty);
                    getBinding().empty.setVisibility(View.VISIBLE);
                    getBinding().networkList.setVisibility(View.GONE);
                    getHandler().sendEmptyMessage(BaseBrowserFragmentKt.MSG_HIDE_LOADING);
                }
            } else if (getBinding().empty.getVisibility() == View.VISIBLE) {
                    getBinding().empty.setVisibility(View.GONE);
                    getBinding().networkList.setVisibility(View.VISIBLE);
            }
        } else {
            getBinding().empty.setText(R.string.network_connection_needed);
            getBinding().empty.setVisibility(View.VISIBLE);
            getBinding().networkList.setVisibility(View.GONE);
            getBinding().setShowFavorites(false);
        }
    }

    @Override
    public void onClick(View v) {
        if (!isRootDirectory()) super.onClick(v);
        else if (v.getId() == R.id.fab) showAddServerDialog(null);
    }

    public void showAddServerDialog(MediaWrapper mw) {
        final FragmentManager fm = getFragmentManager();
        final NetworkServerDialog dialog = new NetworkServerDialog();
        if (mw != null) dialog.setServer(mw);
        dialog.show(fm, "fragment_add_server");
    }

    private BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isResumed()) goBack();
            else setGoBack(true);
        }
    };
}
