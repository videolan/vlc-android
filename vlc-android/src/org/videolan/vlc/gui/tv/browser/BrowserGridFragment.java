/*
 * *************************************************************************
 *  BrowserGridFragment.java
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

package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.ExternalMonitor;
import org.videolan.vlc.gui.tv.DetailsActivity;
import org.videolan.vlc.gui.tv.MediaItemDetails;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;
import org.videolan.vlc.gui.tv.browser.interfaces.DetailsFragment;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.viewmodels.browser.NetworkModel;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class BrowserGridFragment extends GridFragment implements OnItemViewSelectedListener, OnItemViewClickedListener, DetailsFragment {

    private MediaWrapper mItemSelected;
    private NetworkModel provider;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setOnItemViewSelectedListener(this);
        setOnItemViewClickedListener(this);
        final boolean showHiddenFiles = Settings.INSTANCE.getInstance(requireContext()).getBoolean("browser_show_hidden_files", false);
        provider = ViewModelProviders.of(this, new NetworkModel.Factory(requireContext(), null, showHiddenFiles)).get(NetworkModel.class);
        provider.getDataset().observe(this, new Observer<List<MediaLibraryItem>>() {
            @Override
            public void onChanged(@Nullable List<MediaLibraryItem> mediaLibraryItems) {
                mAdapter.setItems(mediaLibraryItems, TvUtil.INSTANCE.getDiffCallback());
            }
        });
        ExternalMonitor.INSTANCE.getConnected().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean connected) {
                if (connected != null && connected) provider.refresh();
                //TODO empty/disconnected view
            }
        });
    }

    public void onPause() {
        super.onPause();
        ((BrowserActivityInterface)mContext).updateEmptyView(false);
    }

    public void showDetails() {
        if (mItemSelected.getType() == MediaWrapper.TYPE_DIR) {
            final Intent intent = new Intent(getActivity(), DetailsActivity.class);
            // pass the item information
            intent.putExtra("media", mItemSelected);
            intent.putExtra("item", new MediaItemDetails(mItemSelected.getTitle(),
                    mItemSelected.getArtist(), mItemSelected.getAlbum(),
                    mItemSelected.getLocation(), mItemSelected.getArtworkURL()));
            startActivity(intent);
        }
    }

    @Override
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        mItemSelected = (MediaWrapper)item;
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        MediaWrapper media = (MediaWrapper) item;
        if (media.getType() == MediaWrapper.TYPE_DIR)
            TvUtil.INSTANCE.browseFolder(requireActivity(), Constants.HEADER_NETWORK, ((MediaWrapper) item).getUri());
        else
            TvUtil.INSTANCE.openMedia(requireActivity(), item, null);
    }
}
