/*
 * *************************************************************************
 *  NetworkBrowseFragment.java
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

package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import org.jetbrains.annotations.NotNull;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.ExternalMonitor;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.dialogs.VlcLoginDialog;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.viewmodels.browser.NetworkModel;

import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import kotlin.Pair;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class NetworkBrowserFragment extends MediaSortedFragment<NetworkModel> {

    public static final String TAG = "VLC/NetworkBrowserFragment";
    boolean goBack = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = ViewModelProviders.of(this, new NetworkModel.Factory(requireContext(), getUri().toString(), getShowHiddenFiles())).get(NetworkModel.class);
        viewModel.getCategories().observe(this, new Observer<Map<String, List<MediaLibraryItem>>>() {
            @Override
            public void onChanged(@Nullable Map<String, List<MediaLibraryItem>> stringListMap) {
                if (stringListMap != null) update(stringListMap);
            }
        });
        ExternalMonitor.INSTANCE.getConnected().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean connected) {
                refresh(connected);
            }
        });
        viewModel.getDescriptionUpdate().observe(this, new Observer<Pair<Integer, String>>() {
            @Override
            public void onChanged(Pair<Integer, String> pair) {
                final int position = pair.component1();
                final ArrayObjectAdapter adapter = (ArrayObjectAdapter) getAdapter();
                int index = -1;
                for (int i = 0; i < adapter.size(); ++i) {
                    final ObjectAdapter objectAdapter = ((ListRow) adapter.get(i)).getAdapter();
                    if (position > index + objectAdapter.size()) index += objectAdapter.size();
                    else for (int j = 0; j < objectAdapter.size(); ++j) {
                        if (++index == position) objectAdapter.notifyItemRangeChanged(j, 1, Constants.UPDATE_DESCRIPTION);
                    }
                }
            }
        });
    }

    public void refresh(boolean connected) {
        if (connected) refresh();
        //TODO Disconnected view
    }

    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(VLCApplication.Companion.getAppContext()).registerReceiver(mLocalReceiver, new IntentFilter(VlcLoginDialog.ACTION_DIALOG_CANCELED));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (goBack) requireActivity().finish();
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(VLCApplication.Companion.getAppContext()).unregisterReceiver(mLocalReceiver);
    }

    @Override
    public void onItemClicked(@NotNull Presenter.ViewHolder viewHolder, @NotNull Object item, @NotNull RowPresenter.ViewHolder viewHolder1, @NotNull Row row) {
        if (item instanceof MediaWrapper && ((MediaWrapper)item).getType() == MediaWrapper.TYPE_DIR) viewModel.saveList((MediaWrapper)item);
        super.onItemClicked(viewHolder, item, viewHolder1, row);
    }

    private BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isResumed()) goBack = true;
        }
    };
}
