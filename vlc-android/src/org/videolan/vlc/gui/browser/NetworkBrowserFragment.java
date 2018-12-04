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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.ExternalMonitor;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.dialogs.NetworkServerDialog;
import org.videolan.vlc.gui.dialogs.VlcLoginDialog;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.WorkersKt;
import org.videolan.vlc.viewmodels.browser.NetworkModel;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class NetworkBrowserFragment extends BaseBrowserFragment {

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelProviders.of(this, new NetworkModel.Factory(requireContext(), getMrl(), getShowHiddenFiles())).get(NetworkModel.class);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ExternalMonitor.INSTANCE.getConnected().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean connected) {
                refresh(connected);
            }
        });
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
        WorkersKt.runIO(new Runnable() {
            @Override
            public void run() {
                final boolean isFavorite = getMrl() != null && getBrowserFavRepository().browserFavExists(Uri.parse(getMrl()));
                WorkersKt.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        item.setIcon(isFavorite ?
                                R.drawable.ic_menu_bookmark_w :
                                R.drawable.ic_menu_bookmark_outline_w);
                        item.setTitle(isFavorite ? R.string.favorites_remove : R.string.favorites_add);
                    }
                });
            }
        });
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
        refresh(ExternalMonitor.INSTANCE.isConnected());
    }

    public void refresh(boolean connected) {
        if (connected) super.refresh();
        else {
            updateEmptyView();
            getAdapter().clear();
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
        final MediaWrapper mw = (MediaWrapper) getAdapter().getItem(position);
        switch (option) {
            case Constants.CTX_FAV_ADD:
                getBrowserFavRepository().addNetworkFavItem(mw.getUri(), mw.getTitle(), mw.getArtworkURL());
                break;
            case Constants.CTX_FAV_EDIT:
                showAddServerDialog(mw);
                break;
            default:
                super.onCtxAction(position, option);
        }
    }

    @Override
    protected void browseRoot() {}

    private boolean allowLAN() {
        return ExternalMonitor.INSTANCE.isLan() || ExternalMonitor.INSTANCE.isVPN();
    }

    @Override
    protected String getCategoryTitle() {
        return getString(R.string.network_browsing);
    }

    /**
     * Update views visibility and emptiness info
     */
    protected void updateEmptyView() {
        if (getBinding() == null) return;
        if (ExternalMonitor.INSTANCE.isConnected()) {
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
